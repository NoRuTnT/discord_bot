package com.discord_bot.backend.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

class ChatSummaryServiceTest {

	private static final String MCP_URL = "http://localhost:18000/mcp";

	private ChatSummaryService chatSummaryService;
	private MockRestServiceServer mockServer;

	@BeforeEach
	void setUp() {
		RestTemplate restTemplate = new RestTemplate();
		mockServer = MockRestServiceServer.bindTo(restTemplate).build();

		chatSummaryService = new ChatSummaryService(restTemplate, new ObjectMapper());
		ReflectionTestUtils.setField(chatSummaryService, "mcpUrl", MCP_URL);
	}

	@Test
	void getAvailableDates_formatsDateListFromMcpResponse() {
		mockServer.expect(requestTo(MCP_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("""
				{
				  "jsonrpc": "2.0",
				  "id": "discord-request",
				  "method": "tools/call",
				  "params": {
				    "name": "list_chat_dates",
				    "arguments": {
				      "limit": 3
				    }
				  }
				}
				"""))
			.andRespond(withSuccess("""
				{
				  "jsonrpc": "2.0",
				  "result": {
				    "content": [
				      {
				        "text": "[\\\"2026-06-10\\\",\\\"2026-06-09\\\",\\\"2026-06-08\\\"]"
				      }
				    ]
				  }
				}
				""", MediaType.APPLICATION_JSON));

		String result = chatSummaryService.getAvailableDates(3);

		assertThat(result).contains("1. 2026-06-10");
		assertThat(result).contains("2. 2026-06-09");
		assertThat(result).contains("/채팅요약 date:YYYY-MM-DD");
		mockServer.verify();
	}

	@Test
	void summarizeChat_returnsRawTextFromMcpResponse() {
		mockServer.expect(requestTo(MCP_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("""
				{
				  "jsonrpc": "2.0",
				  "id": "discord-request",
				  "method": "tools/call",
				  "params": {
				    "name": "analyze_chat_topics",
				    "arguments": {
				      "date": "2026-06-10",
				      "limit": 3000
				    }
				  }
				}
				"""))
			.andRespond(withSuccess("""
				{
				  "jsonrpc": "2.0",
				  "result": {
				    "content": [
				      {
				        "text": "요약 결과 본문"
				      }
				    ]
				  }
				}
				""", MediaType.APPLICATION_JSON));

		String result = chatSummaryService.summarizeChat("2026-06-10");

		assertThat(result).isEqualTo("요약 결과 본문");
		mockServer.verify();
	}

	@Test
	void summarizeChat_throwsUserFriendlyMessageWhenMcpReturnsError() {
		mockServer.expect(requestTo(MCP_URL))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("""
				{
				  "jsonrpc": "2.0",
				  "error": {
				    "message": "internal failure"
				  }
				}
				""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> chatSummaryService.summarizeChat("2026-06-10"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("요청 처리 중 오류가 발생했습니다.");

		mockServer.verify();
	}
}
