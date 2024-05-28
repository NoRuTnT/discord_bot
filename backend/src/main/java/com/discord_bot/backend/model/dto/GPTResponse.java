package com.discord_bot.backend.model.dto;

import java.util.List;

public class GPTResponse {

	private List<GPTChoice> choices;

	public GPTResponse() {
	}

	public GPTResponse(List<GPTChoice> choices) {
		this.choices = choices;
	}

	// Getters and Setters

	public List<GPTChoice> getChoices() {
		return choices;
	}

	public void setChoices(List<GPTChoice> choices) {
		this.choices = choices;
	}

	public static class GPTChoice {
		private String text;

		public GPTChoice() {
		}

		public GPTChoice(String text) {
			this.text = text;
		}

		// Getters and Setters

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}
	}
}