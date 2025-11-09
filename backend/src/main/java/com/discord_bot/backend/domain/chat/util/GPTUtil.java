package com.discord_bot.backend.domain.chat.util;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.discord_bot.backend.common.config.GPTConfig;
import com.discord_bot.backend.domain.chat.model.dto.GPTResponse;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.Tool;

import autovalue.shaded.com.google.common.collect.ImmutableList;

@Component
public class GPTUtil {

	private final GPTConfig gptConfig;

	@Autowired
	public GPTUtil(GPTConfig gptConfig) {
		this.gptConfig = gptConfig;
	}

	public String getChangeQuestion(String question) {
		String apiUrl = gptConfig.getTextUrl();
		String apiKey = gptConfig.getApiKey();
		String endpoint = apiUrl + apiKey;
		String prompt = gptConfig.getPrompt() + "Input: " + question;

		Client client = Client.builder().apiKey(apiKey).build();

		GenerateContentResponse response =
			client.models.generateContent(
				"gemma-3-4b-it",
				prompt,
				null);

		// GPTRequest.Part part = new GPTRequest.Part(prompt);
		// GPTRequest.Content content = new GPTRequest.Content(List.of(part));
		// GPTRequest requestDto = new GPTRequest(List.of(content));
		//
		// HttpHeaders headers = new HttpHeaders();
		// headers.setContentType(MediaType.APPLICATION_JSON);
		//
		// HttpEntity<GPTRequest> httpEntity = new HttpEntity<>(requestDto, headers);
		//
		// RestTemplate restTemplate = new RestTemplate();
		// ResponseEntity<GPTResponse> response = restTemplate.exchange(
		// 	endpoint,
		// 	HttpMethod.POST,
		// 	httpEntity,
		// 	GPTResponse.class);
		return response.text();
	}

	public String getResponseFromGPT(String question) {

		String apiKey = gptConfig.getApiKey();
		String mainprompt = gptConfig.getMainprompt() + "Input: " + question;

		Client client = Client.builder().apiKey(apiKey).build();
		Tool googleSearchTool = Tool.builder().googleSearch(GoogleSearch.builder().build()).build();

		GenerateContentConfig config =
			GenerateContentConfig.builder()
				.tools(ImmutableList.of(googleSearchTool))
				.build();

		GenerateContentResponse response =
			client.models.generateContent(
				"gemini-2.5-flash",
				mainprompt,
				config);
		return response.text();
	}

	private String parseResponse(ResponseEntity<GPTResponse> response) {
		if (response.getStatusCode().is2xxSuccessful()) {
			List<GPTResponse.Candidate> candidates = response.getBody().getCandidates();
			if (!candidates.isEmpty()) {
				String command = candidates.get(0).getContent().getParts().get(0).getText();
				return command;
			}
		}
		return "No response from GPT";
	}
}
