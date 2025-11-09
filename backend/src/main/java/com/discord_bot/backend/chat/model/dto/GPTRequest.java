package com.discord_bot.backend.chat.model.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GPTRequest {

	private List<Content> contents;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Content {
		private List<Part> parts;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Part {
		private String text;
	}

}