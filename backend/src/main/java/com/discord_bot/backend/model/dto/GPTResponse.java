package com.discord_bot.backend.model.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GPTResponse {

	private List<GPTChoice> choices;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class GPTChoice {
		private String text;

	}
}