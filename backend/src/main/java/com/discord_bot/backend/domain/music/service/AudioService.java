package com.discord_bot.backend.domain.music.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.DirectAudioController;

import com.discord_bot.backend.common.exception.BotErrorCode;
import com.discord_bot.backend.common.exception.BotException;
import com.discord_bot.backend.domain.music.config.LavalinkClientProvider;
import com.discord_bot.backend.domain.music.dto.MusicQueueResponse;
import com.discord_bot.backend.domain.music.model.MusicSession;
import com.discord_bot.backend.domain.music.model.MusicTrackView;
import com.google.api.services.youtube.model.SearchResult;

import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.event.TrackEndEvent;
import dev.arbjerg.lavalink.client.player.LavalinkLoadResult;
import dev.arbjerg.lavalink.client.player.LoadFailed;
import dev.arbjerg.lavalink.client.player.NoMatches;
import dev.arbjerg.lavalink.client.player.PlaylistLoaded;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.client.player.TrackLoaded;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioService {

	private static final Duration IDLE_DISCONNECT_DURATION = Duration.ofMinutes(3);

	private final LavalinkClientProvider lavalinkClientProvider;
	private final Map<Long, MusicSession> sessions = new ConcurrentHashMap<>();
	private final ScheduledExecutorService idleMonitor = Executors.newSingleThreadScheduledExecutor();

	@Value("${discord.token}")
	private String discordToken;

	@Value("${music.homeurl}")
	private String homeUrl;

	private LavalinkClient lavalinkClient;

	@PostConstruct
	public void initialize() {
		lavalinkClient = lavalinkClientProvider.getOrCreate(discordToken);
		lavalinkClient.on(TrackEndEvent.class).subscribe(this::handleTrackEndEvent);
		idleMonitor.scheduleAtFixedRate(this::cleanupIdleSessions, 30, 30, TimeUnit.SECONDS);
		log.info("[music] lavalink client initialized");
	}

	@PreDestroy
	public void shutdown() {
		idleMonitor.shutdownNow();
		log.info("[music] idle monitor stopped");
	}

	public void ensureSessionReady(Guild guild, Member member) {
		validateVoiceChannel(member);
		AudioChannel voiceChannel = member.getVoiceState().getChannel();
		MusicSession session = getOrCreateSession(guild);

		synchronized (session) {
			session.markActivity();
			session.setVoiceChannelId(voiceChannel.getIdLong());
		}

		connectToVoiceChannel(guild, voiceChannel);
		getLink(guild.getIdLong());
	}

	public void bindPanelMessage(long guildId, long channelId, long messageId) {
		MusicSession session = getRequiredSession(guildId);
		synchronized (session) {
			session.bindPanel(channelId, messageId);
			session.markActivity();
		}
	}

	public MusicQueueResponse getQueueState(long guildId) {
		MusicSession session = sessions.get(guildId);
		if (session == null) {
			return new MusicQueueResponse(
				guildId,
				false,
				false,
				false,
				buildWebControlUrl(guildId),
				BotErrorCode.MUSIC_SESSION_NOT_READY.getUserMessage(),
				null,
				List.of()
			);
		}

		synchronized (session) {
			return new MusicQueueResponse(
				guildId,
				true,
				isConnected(session),
				session.isPaused(),
				buildWebControlUrl(guildId),
				null,
				toTrackView(session.getCurrentTrack(), true),
				toTrackViews(session.getQueuedTracks(), false)
			);
		}
	}

	public CompletableFuture<MusicQueueResponse> addTrack(long guildId, String input) {
		MusicSession session = getRequiredSession(guildId);
		String identifier = resolveLoadIdentifier(input);

		return CompletableFuture.supplyAsync(() -> {
			synchronized (session) {
				session.markActivity();
			}

			LavalinkLoadResult result = executeLavalinkAction(
				guildId,
				"track load",
				() -> getLink(guildId).loadItem(identifier).block()
			);
			return handleLoadResult(session, guildId, result);
		});
	}

	public MusicQueueResponse togglePause(long guildId) {
		MusicSession session = getRequiredSession(guildId);

		synchronized (session) {
			session.markActivity();
			boolean nextPaused = !session.isPaused();
			executeLavalinkAction(
				guildId,
				"toggle pause",
				() -> getLink(guildId).createOrUpdatePlayer()
					.setPaused(nextPaused)
					.block()
			);
			session.setPaused(nextPaused);
		}

		return getQueueState(guildId);
	}

	public MusicQueueResponse skipTrack(long guildId) {
		MusicSession session = getRequiredSession(guildId);

		synchronized (session) {
			session.markActivity();
			playNextQueuedTrack(session);
		}

		return withMessage(guildId, "다음 곡으로 넘어갔습니다.");
	}

	public MusicQueueResponse stopTrack(Guild guild) {
		MusicSession session = getRequiredSession(guild.getIdLong());
		disconnectSession(session, "사용자가 중지 버튼을 눌러 세션을 종료했습니다.");
		return withMessage(guild.getIdLong(), "재생을 중지하고 음성 채널에서 나갔습니다.");
	}

	public MusicQueueResponse moveQueuedTrack(long guildId, int fromIndex, int toIndex) {
		MusicSession session = getRequiredSession(guildId);

		synchronized (session) {
			session.markActivity();
			List<Track> queue = session.getQueuedTracks();
			if (fromIndex < 0 || toIndex < 0 || fromIndex >= queue.size() || toIndex >= queue.size()) {
				throw new BotException(BotErrorCode.INVALID_QUEUE_POSITION);
			}

			Track moved = queue.remove(fromIndex);
			queue.add(toIndex, moved);
		}

		return withMessage(guildId, "대기열 순서를 변경했습니다.");
	}

	public void addTrackToQueue(String input, Guild guild, Member member) {
		ensureSessionReady(guild, member);
		addTrack(guild.getIdLong(), input);
	}

	public void pauseTrack(Guild guild) {
		MusicSession session = getRequiredSession(guild.getIdLong());
		synchronized (session) {
			session.markActivity();
			executeLavalinkAction(
				guild.getIdLong(),
				"pause",
				() -> getLink(guild.getIdLong()).createOrUpdatePlayer().setPaused(true).block()
			);
			session.setPaused(true);
		}
	}

	public void resumeTrack(Guild guild) {
		MusicSession session = getRequiredSession(guild.getIdLong());
		synchronized (session) {
			session.markActivity();
			executeLavalinkAction(
				guild.getIdLong(),
				"resume",
				() -> getLink(guild.getIdLong()).createOrUpdatePlayer().setPaused(false).block()
			);
			session.setPaused(false);
		}
	}

	public List<String> getQueue(Guild guild) {
		return getQueueState(guild.getIdLong()).getQueuedTracks().stream()
			.map(track -> "%s - %s".formatted(track.getTitle(), track.getAuthor()))
			.toList();
	}

	public List<SearchResult> searchYouTube(String query) throws GeneralSecurityException, IOException {
		log.warn("[music] legacy youtube search requested query={}", query);
		return List.of();
	}

	public void handleBotVoiceUpdate(long guildId, Long joinedChannelId, Long leftChannelId) {
		if (joinedChannelId == null && leftChannelId != null) {
			MusicSession session = sessions.get(guildId);
			if (session != null) {
				log.info("[music] bot disconnected from voice channel guildId={} channelId={}", guildId, leftChannelId);
				sessions.remove(guildId);
			}
		}
	}

	private MusicQueueResponse handleLoadResult(MusicSession session, long guildId, LavalinkLoadResult result) {
		synchronized (session) {
			if (result instanceof TrackLoaded trackLoaded) {
				boolean started = enqueueTrack(session, trackLoaded.getTrack());
				return withMessage(guildId, started ? "곡을 바로 재생합니다." : "곡을 대기열에 추가했습니다.");
			}

			if (result instanceof dev.arbjerg.lavalink.client.player.SearchResult searchResult) {
				List<Track> tracks = searchResult.getTracks();
				if (tracks.isEmpty()) {
					return withMessage(guildId, "검색 결과를 찾지 못했습니다.");
				}

				boolean started = enqueueTrack(session, tracks.get(0));
				return withMessage(guildId, started ? "검색 결과를 바로 재생합니다." : "검색 결과 첫 곡을 대기열에 추가했습니다.");
			}

			if (result instanceof PlaylistLoaded playlistLoaded) {
				List<Track> tracks = playlistLoaded.getTracks();
				if (tracks.isEmpty()) {
					return withMessage(guildId, "재생목록에 곡이 없습니다.");
				}

				boolean started = enqueueTrack(session, tracks.get(0));
				for (int i = 1; i < tracks.size(); i++) {
					session.getQueuedTracks().add(tracks.get(i));
				}
				return withMessage(guildId, started
					? "재생목록을 불러왔고 첫 곡을 바로 재생합니다."
					: "재생목록을 대기열에 추가했습니다.");
			}

			if (result instanceof NoMatches || result == null) {
				return withMessage(guildId, "재생할 수 있는 곡을 찾지 못했습니다.");
			}

			if (result instanceof LoadFailed loadFailed) {
				String message = loadFailed.getException() != null
					? loadFailed.getException().getMessage()
					: "알 수 없는 오류";
				return withMessage(guildId, "곡을 불러오지 못했습니다: " + message);
			}

			return withMessage(guildId, "곡을 처리하지 못했습니다.");
		}
	}

	private boolean enqueueTrack(MusicSession session, Track track) {
		if (session.getCurrentTrack() == null) {
			playTrack(session, track);
			return true;
		}

		session.getQueuedTracks().add(track);
		return false;
	}

	private void playTrack(MusicSession session, Track track) {
		executeLavalinkAction(
			session.getGuildId(),
			"play track",
			() -> getLink(session.getGuildId()).createOrUpdatePlayer()
				.setTrack(track)
				.setPaused(false)
				.block()
		);
		session.setCurrentTrack(track);
		session.setPaused(false);
		session.markActivity();
		log.info("[music] playTrack guildId={} title={}", session.getGuildId(), track.getInfo().getTitle());
	}

	private void playNextQueuedTrack(MusicSession session) {
		if (session.getQueuedTracks().isEmpty()) {
			executeLavalinkAction(
				session.getGuildId(),
				"stop track",
				() -> getLink(session.getGuildId()).createOrUpdatePlayer().stopTrack().block()
			);
			session.setCurrentTrack(null);
			session.setPaused(false);
			return;
		}

		Track nextTrack = session.getQueuedTracks().remove(0);
		playTrack(session, nextTrack);
	}

	private void handleTrackEndEvent(TrackEndEvent event) {
		MusicSession session = sessions.get(event.getGuildId());
		if (session == null) {
			return;
		}

		synchronized (session) {
			Track currentTrack = session.getCurrentTrack();
			if (currentTrack == null || event.getTrack() == null) {
				return;
			}

			if (!currentTrack.getEncoded().equals(event.getTrack().getEncoded())) {
				return;
			}

			if (session.getQueuedTracks().isEmpty()) {
				session.setCurrentTrack(null);
				session.setPaused(false);
				session.markActivity();
				return;
			}

			Track nextTrack = session.getQueuedTracks().remove(0);
			playTrack(session, nextTrack);
		}
	}

	private MusicQueueResponse withMessage(long guildId, String message) {
		MusicQueueResponse state = getQueueState(guildId);
		return new MusicQueueResponse(
			state.getGuildId(),
			state.isPanelReady(),
			state.isConnected(),
			state.isPaused(),
			state.getWebUrl(),
			message,
			state.getCurrentTrack(),
			state.getQueuedTracks()
		);
	}

	private MusicSession getOrCreateSession(Guild guild) {
		return sessions.computeIfAbsent(guild.getIdLong(), guildId -> new MusicSession(guildId, guild));
	}

	private MusicSession getRequiredSession(long guildId) {
		MusicSession session = sessions.get(guildId);
		if (session == null) {
			throw new BotException(BotErrorCode.MUSIC_SESSION_NOT_READY);
		}
		return session;
	}

	private void validateVoiceChannel(Member member) {
		if (member == null || member.getVoiceState() == null || member.getVoiceState().getChannel() == null) {
			throw new BotException(BotErrorCode.VOICE_CHANNEL_REQUIRED);
		}
	}

	private void connectToVoiceChannel(Guild guild, AudioChannel targetChannel) {
		AudioChannel currentChannel = guild.getSelfMember().getVoiceState() != null
			? guild.getSelfMember().getVoiceState().getChannel()
			: null;

		if (currentChannel != null && currentChannel.getIdLong() == targetChannel.getIdLong()) {
			return;
		}

		DirectAudioController controller = guild.getJDA().getDirectAudioController();
		controller.connect(targetChannel);
		log.info("[music] direct audio connect guildId={} channelId={}", guild.getIdLong(), targetChannel.getIdLong());
	}

	private void cleanupIdleSessions() {
		for (MusicSession session : sessions.values()) {
			try {
				handleIdleSession(session);
			} catch (Exception e) {
				log.warn("[music] cleanupIdleSessions failed guildId={}", session.getGuildId(), e);
			}
		}
	}

	private void handleIdleSession(MusicSession session) {
		AudioChannel connectedChannel = session.getGuild().getSelfMember().getVoiceState() != null
			? session.getGuild().getSelfMember().getVoiceState().getChannel()
			: null;

		if (connectedChannel == null) {
			sessions.remove(session.getGuildId());
			return;
		}

		long humanCount = connectedChannel.getMembers().stream()
			.filter(member -> !member.getUser().isBot())
			.count();

		synchronized (session) {
			if (humanCount == 0) {
				session.markChannelEmptyIfNeeded();
				if (Duration.between(session.getEmptyChannelSince(), Instant.now()).compareTo(IDLE_DISCONNECT_DURATION) >= 0) {
					disconnectSession(session, "음성 채널에 사람이 없어 자동으로 나갑니다.");
				}
				return;
			}

			session.clearEmptyChannel();

			boolean noPlayback = session.getCurrentTrack() == null && session.getQueuedTracks().isEmpty();
			if (noPlayback && Duration.between(session.getLastActivityAt(), Instant.now()).compareTo(IDLE_DISCONNECT_DURATION) >= 0) {
				disconnectSession(session, "오랫동안 재생이 없어 자동으로 나갑니다.");
			}
		}
	}

	private void disconnectSession(MusicSession session, String reason) {
		synchronized (session) {
			log.info("[music] disconnectSession guildId={} reason={}", session.getGuildId(), reason);
			try {
				getLink(session.getGuildId()).destroy()
					.onErrorResume(error -> {
						log.warn("[music] failed to destroy lavalink player guildId={}", session.getGuildId(), error);
						return Mono.empty();
					})
					.block();
			} catch (Exception e) {
				log.warn("[music] disconnectSession destroy failed guildId={}", session.getGuildId(), e);
			}
			session.clearPlayback();
		}

		session.getGuild().getJDA().getDirectAudioController().disconnect(session.getGuild());
		sessions.remove(session.getGuildId());
	}

	private Link getLink(long guildId) {
		try {
			return lavalinkClient.getOrCreateLink(guildId);
		} catch (Exception e) {
			log.error("[music] failed to create lavalink link guildId={}", guildId, e);
			throw new BotException(BotErrorCode.LAVALINK_UNAVAILABLE, e);
		}
	}

	private boolean isConnected(MusicSession session) {
		return session.getGuild().getSelfMember().getVoiceState() != null
			&& session.getGuild().getSelfMember().getVoiceState().getChannel() != null;
	}

	private String resolveLoadIdentifier(String input) {
		if (input == null || input.isBlank()) {
			throw new BotException(BotErrorCode.INVALID_TRACK_INPUT);
		}

		String trimmed = input.trim();
		if (trimmed.matches("^(https?|ftp)://[^\\s/$.?#].[^\\s]*$")) {
			return trimmed;
		}
		return "ytsearch:" + trimmed;
	}

	private String buildWebControlUrl(long guildId) {
		String normalizedBaseUrl = homeUrl.endsWith("/") ? homeUrl.substring(0, homeUrl.length() - 1) : homeUrl;
		return normalizedBaseUrl + "/music/" + guildId;
	}

	private List<MusicTrackView> toTrackViews(List<Track> tracks, boolean current) {
		List<MusicTrackView> trackViews = new ArrayList<>();
		for (Track track : tracks) {
			trackViews.add(toTrackView(track, current));
		}
		return trackViews;
	}

	private MusicTrackView toTrackView(Track track, boolean current) {
		if (track == null) {
			return null;
		}

		return new MusicTrackView(
			track.getInfo().getTitle(),
			track.getInfo().getAuthor(),
			track.getInfo().getUri(),
			track.getInfo().getLength(),
			current
		);
	}

	private <T> T executeLavalinkAction(long guildId, String action, LavalinkSupplier<T> supplier) {
		try {
			return supplier.get();
		} catch (BotException e) {
			throw e;
		} catch (Exception e) {
			log.error("[music] lavalink action failed guildId={} action={}", guildId, action, e);
			throw new BotException(BotErrorCode.LAVALINK_UNAVAILABLE, e);
		}
	}

	@FunctionalInterface
	private interface LavalinkSupplier<T> {
		T get() throws Exception;
	}
}
