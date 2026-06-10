package com.discord_bot.backend.domain.music.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MoveTrackRequest {

	private int fromIndex;
	private int toIndex;
}
