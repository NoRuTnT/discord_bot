package com.discord_bot.backend.service;

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.discord_bot.backend.util.GPTUtil;

@Service
public class GPTService {

	private final GPTUtil gptUtil;

	@Autowired
	public GPTService(GPTUtil gptUtil) {
		this.gptUtil = gptUtil;
	}

	public String convertToCommand(String userInput) throws JSONException {
		String response = gptUtil.getChangeQuestion(userInput);

		return response;
	}

	public String getResponse(String question) {
		String response = gptUtil.getResponseFromGPT(question);

		return response;
	}
}