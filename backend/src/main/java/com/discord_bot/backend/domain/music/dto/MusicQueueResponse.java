package com.discord_bot.backend.domain.music.dto;

import java.util.List;

import com.discord_bot.backend.domain.music.model.MusicTrackView;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MusicQueueResponse {

	private long guildId;
	private boolean panelReady;
	private boolean connected;
	private boolean paused;
	private String webUrl;
	private String message;
	private MusicTrackView currentTrack;
	private List<MusicTrackView> queuedTracks;
}
