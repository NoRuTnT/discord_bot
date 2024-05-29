package com.discord_bot.backend.util;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.discord_bot.backend.config.GPTConfig;
import com.discord_bot.backend.model.dto.GPTRequest;
import com.discord_bot.backend.model.dto.GPTResponse;

@Component
public class GPTUtil {

	private final GPTConfig gptConfig;

	@Autowired
	public GPTUtil(GPTConfig gptConfig) {
		this.gptConfig = gptConfig;
	}

	public String getResponseFromGPT(String question) {
		RestTemplate restTemplate = new RestTemplate();
		String apiUrl = gptConfig.getApiUrl();
		String apiKey = gptConfig.getApiKey();

		// GPT API 요청을 위한 요청 본문 구성
		GPTRequest gptRequest = new GPTRequest(question, 100, 0.7, 1.0, 1, Arrays.asList("\n"));

		// 헤더 구성
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(apiKey);

		// 요청 엔티티 구성
		HttpEntity<GPTRequest> entity = new HttpEntity<>(gptRequest, headers);

		// API 호출 및 응답 처리
		ResponseEntity<GPTResponse> responseEntity = restTemplate.exchange(apiUrl, HttpMethod.POST, entity,
			GPTResponse.class);
		return parseResponse(responseEntity.getBody());
	}

	private String parseResponse(GPTResponse response) {
		// 응답에서 필요한 정보만 추출 (첫 번째 선택 항목의 텍스트 반환)
		if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
			return response.getChoices().get(0).getText();
		}
		return "No response from GPT";
	}
}
