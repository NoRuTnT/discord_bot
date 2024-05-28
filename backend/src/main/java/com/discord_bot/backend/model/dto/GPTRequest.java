package com.discord_bot.backend.model.dto;

import java.util.List;

public class GPTRequest {

	private String prompt;
	private int max_tokens;
	private double temperature;
	private double top_p;
	private int n;
	private List<String> stop;

	public GPTRequest() {
	}

	public GPTRequest(String prompt, int max_tokens, double temperature, double top_p, int n, List<String> stop) {
		this.prompt = prompt;
		this.max_tokens = max_tokens;
		this.temperature = temperature;
		this.top_p = top_p;
		this.n = n;
		this.stop = stop;
	}

	// Getters and Setters

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	public int getMax_tokens() {
		return max_tokens;
	}

	public void setMax_tokens(int max_tokens) {
		this.max_tokens = max_tokens;
	}

	public double getTemperature() {
		return temperature;
	}

	public void setTemperature(double temperature) {
		this.temperature = temperature;
	}

	public double getTop_p() {
		return top_p;
	}

	public void setTop_p(double top_p) {
		this.top_p = top_p;
	}

	public int getN() {
		return n;
	}

	public void setN(int n) {
		this.n = n;
	}

	public List<String> getStop() {
		return stop;
	}

	public void setStop(List<String> stop) {
		this.stop = stop;
	}
}