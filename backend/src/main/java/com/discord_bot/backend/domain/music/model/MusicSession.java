package com.discord_bot.backend.domain.music.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;

import dev.arbjerg.lavalink.client.player.Track;

public class MusicSession {

	private final long guildId;
	private final Guild guild;
	private final List<Track> queuedTracks = new ArrayList<>();
	private Long panelChannelId;
	private Long panelMessageId;
	private Long voiceChannelId;
	private Instant lastActivityAt;
	private Instant emptyChannelSince;
	private Track currentTrack;
	private boolean paused;

	public MusicSession(long guildId, Guild guild) {
		this.guildId = guildId;
		this.guild = guild;
		this.lastActivityAt = Instant.now();
	}

	public long getGuildId() {
		return guildId;
	}

	public Guild getGuild() {
		return guild;
	}

	public List<Track> getQueuedTracks() {
		return queuedTracks;
	}

	public Long getPanelChannelId() {
		return panelChannelId;
	}

	public Long getPanelMessageId() {
		return panelMessageId;
	}

	public Long getVoiceChannelId() {
		return voiceChannelId;
	}

	public Instant getLastActivityAt() {
		return lastActivityAt;
	}

	public Instant getEmptyChannelSince() {
		return emptyChannelSince;
	}

	public Track getCurrentTrack() {
		return currentTrack;
	}

	public boolean isPaused() {
		return paused;
	}

	public void bindPanel(long channelId, long messageId) {
		this.panelChannelId = channelId;
		this.panelMessageId = messageId;
	}

	public void setVoiceChannelId(Long voiceChannelId) {
		this.voiceChannelId = voiceChannelId;
	}

	public void markActivity() {
		this.lastActivityAt = Instant.now();
		this.emptyChannelSince = null;
	}

	public void markChannelEmptyIfNeeded() {
		if (emptyChannelSince == null) {
			emptyChannelSince = Instant.now();
		}
	}

	public void clearEmptyChannel() {
		this.emptyChannelSince = null;
	}

	public void setCurrentTrack(Track currentTrack) {
		this.currentTrack = currentTrack;
	}

	public void setPaused(boolean paused) {
		this.paused = paused;
	}

	public void clearPlayback() {
		this.currentTrack = null;
		this.paused = false;
		this.queuedTracks.clear();
	}
}
