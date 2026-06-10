package com.discord_bot.backend.domain.chat.dto;

public class McpToolCallRequest {

	private String jsonrpc;
	private String id;
	private String method;
	private McpToolCallParams params;

	public McpToolCallRequest() {
	}

	public McpToolCallRequest(String jsonrpc, String id, String method, McpToolCallParams params) {
		this.jsonrpc = jsonrpc;
		this.id = id;
		this.method = method;
		this.params = params;
	}

	public String getJsonrpc() {
		return jsonrpc;
	}

	public void setJsonrpc(String jsonrpc) {
		this.jsonrpc = jsonrpc;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public McpToolCallParams getParams() {
		return params;
	}

	public void setParams(McpToolCallParams params) {
		this.params = params;
	}
}
