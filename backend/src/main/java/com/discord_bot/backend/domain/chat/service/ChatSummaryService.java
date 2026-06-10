package com.discord_bot.backend.domain.chat.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.discord_bot.backend.domain.chat.dto.McpToolCallParams;
import com.discord_bot.backend.domain.chat.dto.McpToolCallRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSummaryService {

	private static final String MCP_REQUEST_ERROR_MESSAGE = "요청 처리 중 오류가 발생했습니다.";
	private static final int DEFAULT_DATE_LIMIT = 10;
	private static final int SUMMARY_LIMIT = 3000;

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;

	@Value("${mcp.chat.url}")
	private String mcpUrl;

	public String getAvailableDates(Integer limit) {
		int safeLimit = (limit == null || limit <= 0) ? DEFAULT_DATE_LIMIT : limit;
		String responseText = callTool("list_chat_dates", Map.of("limit", safeLimit));
		return formatDateList(responseText, safeLimit);
	}

	public String summarizeChat(String date) {
		return callTool("analyze_chat_topics", Map.of(
			"date", date,
			"limit", SUMMARY_LIMIT
		));
	}

	private String callTool(String toolName, Map<String, Object> arguments) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		McpToolCallRequest request = new McpToolCallRequest(
			"2.0",
			"discord-request",
			"tools/call",
			new McpToolCallParams(toolName, arguments)
		);

		try {
			String responseBody = restTemplate.postForObject(
				mcpUrl,
				new HttpEntity<>(request, headers),
				String.class
			);

			if (responseBody == null || responseBody.isBlank()) {
				log.error("MCP response body is empty. tool={}", toolName);
				throw new IllegalStateException(MCP_REQUEST_ERROR_MESSAGE);
			}

			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode errorNode = root.path("error");
			if (!errorNode.isMissingNode() && !errorNode.isNull()) {
				log.error("MCP tool returned error. tool={}, message={}", toolName, errorNode.path("message").asText());
				throw new IllegalStateException(MCP_REQUEST_ERROR_MESSAGE);
			}

			JsonNode contentNode = root.path("result").path("content");
			if (!contentNode.isArray() || contentNode.isEmpty()) {
				log.error("MCP content node is missing. tool={}, response={}", toolName, responseBody);
				throw new IllegalStateException(MCP_REQUEST_ERROR_MESSAGE);
			}

			String text = contentNode.get(0).path("text").asText("");
			if (text.isBlank()) {
				log.error("MCP content text is blank. tool={}, response={}", toolName, responseBody);
				throw new IllegalStateException(MCP_REQUEST_ERROR_MESSAGE);
			}

			return text;
		} catch (JsonProcessingException e) {
			log.error("Failed to parse MCP response. tool={}", toolName, e);
			throw new IllegalStateException(MCP_REQUEST_ERROR_MESSAGE, e);
		} catch (RestClientException e) {
			log.error("Failed to call MCP server. tool={}, url={}", toolName, mcpUrl, e);
			throw new IllegalStateException(MCP_REQUEST_ERROR_MESSAGE, e);
		}
	}

	private String formatDateList(String responseText, int limit) {
		List<String> dates = extractDates(responseText);
		if (dates.isEmpty()) {
			return responseText;
		}

		StringBuilder builder = new StringBuilder("요약 가능한 채팅 날짜입니다.\n");
		for (int i = 0; i < Math.min(dates.size(), limit); i++) {
			builder.append(i + 1)
				.append(". ")
				.append(dates.get(i))
				.append('\n');
		}
		builder.append("\n원하는 날짜로 /채팅요약 date:YYYY-MM-DD 를 사용해주세요.");
		return builder.toString().trim();
	}

	private List<String> extractDates(String responseText) {
		try {
			JsonNode root = objectMapper.readTree(responseText);
			Set<String> dates = new LinkedHashSet<>();
			collectDates(root, dates);
			return new ArrayList<>(dates);
		} catch (JsonProcessingException e) {
			log.warn("Failed to parse date list response text as JSON. raw={}", responseText);
			return List.of();
		}
	}

	private void collectDates(JsonNode node, Set<String> dates) {
		if (node == null || node.isNull()) {
			return;
		}

		if (node.isTextual()) {
			String value = node.asText().trim();
			if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
				dates.add(value);
			}
			return;
		}

		if (node.isArray()) {
			for (JsonNode child : node) {
				collectDates(child, dates);
			}
			return;
		}

		if (node.isObject()) {
			node.fields().forEachRemaining(entry -> collectDates(entry.getValue(), dates));
		}
	}
}
