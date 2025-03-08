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
		return audioManagers.computeIfAbsent(Long.valueOf(guild.getIdLong()), guildId -> {
			GuildAudioManager manager = new GuildAudioManager(playerManager);
			guild.getAudioManager().setSendingHandler(new AudioPlayerSendHandler(manager.player));
			return manager;
		});
	}

	public void addTrackToQueue(String url, Guild guild, Member member) {
		GuildAudioManager manager = getGuildAudioManager(guild);

		playerManager.loadItem(url, new AudioLoadResultHandler() {
			@Override
			public void trackLoaded(AudioTrack track) {
				manager.scheduler.queue(track);
				connectToVoiceChannel(guild, member);
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				playlist.getTracks().forEach(manager.scheduler::queue);
				connectToVoiceChannel(guild, member);
			}

			@Override
			public void noMatches() {
				System.err.println("No matches found for the URL: " + url);
			}

			@Override
			public void loadFailed(FriendlyException exception) {
				System.err.println("Failed to load the track: " + exception.getMessage());
			}
		});
	}

	public void stopTrack(Guild guild) {
		GuildAudioManager manager = getGuildAudioManager(guild);

		// 현재 재생 중인 트랙을 중단

	}

	public void pauseTrack(Guild guild) {
		GuildAudioManager manager = getGuildAudioManager(guild);
		manager.scheduler.onPlayerPause(manager.player); // 재생을 일시정지
	}

	public void resumeTrack(Guild guild) {
		GuildAudioManager manager = getGuildAudioManager(guild);
		manager.scheduler.onPlayerResume(manager.player); // 재생 상태로 복귀
	}

	private void connectToVoiceChannel(Guild guild, Member member) {
		AudioManager audioManager = guild.getAudioManager();
		if (member.getVoiceState().getChannel() == null) {
			throw new IllegalStateException("아무도 음성채널에 들어와있지 않아요!");
		}
		audioManager.openAudioConnection(member.getVoiceState().getChannel());

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