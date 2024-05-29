package com.discord_bot.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GPTConfig {

	@Value("${gpt.api.key}")
	private String apiKey;

	@Value("${gpt.api.url}")
	private String apiUrl;

	public String getApiKey() {
		return apiKey;
	}

	public String getApiUrl() {
		return apiUrl;
	}
}