package com.discord_bot.backend.domain.music.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MusicTrackView {

	private String title;
	private String author;
	private String uri;
	private long durationMs;
	private boolean current;
}
