package com.discord_bot.backend.service;

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

	public String getResponse(String question) {
		String response = gptUtil.getResponseFromGPT(question);
		// 필요에 따라 추가적인 비즈니스 로직을 여기에 추가할 수 있습니다.
		return response;
	}
}