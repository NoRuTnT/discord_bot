package com.discord_bot.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GPTConfig {

	@Value("${gpt.api.key}")
	private String apiKey;

	@Value("${gpt.api.texturl}")
	private String textUrl;

	@Value("${gpt.api.mainurl}")
	private String mainUrl;

	@Value("${gpt.api.prompt}")
	private String prompt;

	@Value("${gpt.api.mainprompt}")
	private String mainprompt;

	public String getApiKey() {
		return apiKey;
	}

	public String getTextUrl() {
		return textUrl;
	}

	public String getMainUrl() {
		return mainUrl;
	}

	public String getPrompt() {
		return prompt;
	}

	public String getMainprompt() {
		return mainprompt;
	}
}