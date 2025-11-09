package com.discord_bot.backend.music.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import lombok.Getter;

public class TrackScheduler extends AudioEventAdapter {

	@Getter
	private final AudioPlayer player;
	private final BlockingQueue<AudioTrack> queue;

	public TrackScheduler(AudioPlayer player, BlockingQueue<AudioTrack> queue) {
		this.player = player;
		this.queue = new LinkedBlockingQueue<>();
	}

	public void queue(AudioTrack track) {
		if (!player.startTrack(track, true)) {
			queue.offer(track);
		}
	}

	public List<AudioTrack> getQueue() {

		return new ArrayList<>(queue);
	}

	@Override
	public void onPlayerPause(AudioPlayer player) {
		// Player was paused
		player.setPaused(true);
	}

	@Override
	public void onPlayerResume(AudioPlayer player) {
		// Player was resumed
		player.setPaused(false);
	}

	@Override
	public void onTrackStart(AudioPlayer player, AudioTrack track) {
		// A track started playing
	}

	@Override
	public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
		//다음곡 체크후 자동재생
		if (endReason.mayStartNext) {
			player.startTrack(queue.poll(), false);
		}

		// endReason == FINISHED: A track finished or died by an exception (mayStartNext = true).
		// endReason == LOAD_FAILED: Loading of a track failed (mayStartNext = true).
		// endReason == STOPPED: The player was stopped.
		// endReason == REPLACED: Another track started playing while this had not finished
		// endReason == CLEANUP: Player hasn't been queried for a while, if you want you can put a
		//                       clone of this back to your queue
	}

	@Override
	public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
		// An already playing track threw an exception (track end event will still be received separately)
	}

	@Override
	public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
		// Audio track has been unable to provide us any audio, might want to just start a new track
	}

}
