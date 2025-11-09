package com.discord_bot.backend.domain.chat.model.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GPTResponse {

	private List<Candidate> candidates;

	@Data
	public static class Candidate {
		private Content content;
	}

	@Data
	public static class Content {
		private List<Part> parts;
	}

	@Data
	public static class Part {
		private String text;
	}
}