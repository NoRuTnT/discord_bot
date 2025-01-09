package com.discord_bot.backend.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import lombok.Getter;

@Getter
public class GuildAudioManager {

	public final AudioPlayer player;
	public final TrackScheduler scheduler;
	private final BlockingQueue<AudioTrack> queue;

	public GuildAudioManager(AudioPlayerManager manager) {
		this.player = manager.createPlayer();
		this.queue = new LinkedBlockingQueue<>();
		this.scheduler = new TrackScheduler(player, queue);
		this.player.addListener(scheduler);
	}

}
