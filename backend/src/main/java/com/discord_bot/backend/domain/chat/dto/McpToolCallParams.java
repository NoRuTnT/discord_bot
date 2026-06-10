package com.discord_bot.backend.domain.chat.dto;

import java.util.Map;

public class McpToolCallParams {

	private String name;
	private Map<String, Object> arguments;

	public McpToolCallParams() {
	}

	public McpToolCallParams(String name, Map<String, Object> arguments) {
		this.name = name;
		this.arguments = arguments;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Map<String, Object> getArguments() {
		return arguments;
	}

	public void setArguments(Map<String, Object> arguments) {
		this.arguments = arguments;
	}
}
