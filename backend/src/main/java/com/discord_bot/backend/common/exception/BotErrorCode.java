package com.discord_bot.backend.common.exception;

public enum BotErrorCode {

	MUSIC_SESSION_NOT_READY(1000, "먼저 /음악패널 명령어로 세션을 열어주세요."),
	VOICE_CHANNEL_REQUIRED(1001, "먼저 음성 채널에 들어가 있어야 합니다."),
	INVALID_TRACK_INPUT(1002, "추가할 URL 또는 검색어를 입력해주세요."),
	INVALID_QUEUE_POSITION(1003, "변경할 수 없는 대기열 위치입니다."),
	LAVALINK_UNAVAILABLE(1004, "음악 서버에 연결할 수 없습니다. Lavalink 서버 상태를 확인해주세요.");

	private final int code;
	private final String userMessage;

	BotErrorCode(int code, String userMessage) {
		this.code = code;
		this.userMessage = userMessage;
	}

	public int getCode() {
		return code;
	}

	public String getUserMessage() {
		return userMessage;
	}
}
