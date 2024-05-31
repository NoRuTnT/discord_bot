package com.discord_bot.backend.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.managers.AudioManager;

import com.discord_bot.backend.util.GuildAudioManager;
import com.discord_bot.backend.util.YouTubeUtil;
import com.google.api.services.youtube.model.SearchResult;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;

@Service
public class AudioService {

	private final YouTubeUtil youTubeUtil;
	private final AudioPlayerManager playerManager;
	private final Map<Long, GuildAudioManager> audioManagers;

	@Autowired
	public AudioService(YouTubeUtil youTubeUtil) {
		this.youTubeUtil = youTubeUtil;
		this.playerManager = new DefaultAudioPlayerManager();
		AudioSourceManagers.registerRemoteSources(playerManager);
		this.audioManagers = new HashMap<>();
	}

	private synchronized GuildAudioManager getGuildAudioManager(Guild guild) {
		return audioManagers.computeIfAbsent(guild.getIdLong(), guildId -> {
			GuildAudioManager manager = new GuildAudioManager(playerManager);
			guild.getAudioManager().setSendingHandler(new AudioPlayerSendHandler(manager.player));
			return manager;
		});
	}

	public void addTrackToQueue(String url, Guild guild, Member member, TextChannel channel) {
		GuildAudioManager manager = getGuildAudioManager(guild);

		playerManager.loadItem(url, new AudioLoadResultHandler() {
			@Override
			public void trackLoaded(AudioTrack track) {
				manager.scheduler.queue(track);
				connectToVoiceChannel(guild, member);

				// 현재 큐의 크기를 가져옴 (1부터 시작하기 위해 +1)
				int queuePosition = manager.scheduler.getQueue().size() + 1;
				String trackTitle = track.getInfo().title;

				// 메시지를 채널에 보냄
				channel.sendMessage(String.format("노래가 추가되었습니다: %d, Title: %s", queuePosition, trackTitle)).queue();
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				for (AudioTrack track : playlist.getTracks()) {
					manager.scheduler.queue(track);
				}
				connectToVoiceChannel(guild, member);
				channel.sendMessage("플레이리스트가 추가되었습니다.").queue();
			}

			@Override
			public void noMatches() {
				channel.sendMessage("url과 매칭되는 컨텐츠가 없습니다: " + url).queue();
			}

			@Override
			public void loadFailed(FriendlyException exception) {
				channel.sendMessage("로드하는데 실패했습니다: " + exception.getMessage()).queue();
			}
		});
	}

	private void connectToVoiceChannel(Guild guild, Member member) {
		AudioManager audioManager = guild.getAudioManager();
		if (!audioManager.isConnected() && member.getVoiceState().getChannel() != null) {
			audioManager.openAudioConnection(member.getVoiceState().getChannel());
		}
	}

	public List<String> getQueue(Guild guild) {
		GuildAudioManager manager = getGuildAudioManager(guild);
		return manager.scheduler.getQueue().stream()
			.map(AudioTrack::getInfo)
			.map(info -> String.format("%s 신청한사람: %s", info.title, info.author))
			.collect(Collectors.toList());
	}

	public List<SearchResult> searchYouTube(String query) throws GeneralSecurityException, IOException {
		return youTubeUtil.search(query);
	}

	private static class AudioPlayerSendHandler implements AudioSendHandler {

		private final AudioPlayer audioPlayer;
		private AudioFrame lastFrame;

		public AudioPlayerSendHandler(AudioPlayer audioPlayer) {
			this.audioPlayer = audioPlayer;
		}

		@Override
		public boolean canProvide() {
			lastFrame = audioPlayer.provide();
			return lastFrame != null;
		}

		@Override
		public ByteBuffer provide20MsAudio() {
			return ByteBuffer.wrap(lastFrame.getData());
		}

		@Override
		public boolean isOpus() {
			return true;
		}
	}
}