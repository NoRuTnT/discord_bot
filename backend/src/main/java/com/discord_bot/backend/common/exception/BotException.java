package com.discord_bot.backend.common.exception;

public class BotException extends RuntimeException {

	private final BotErrorCode errorCode;

	public BotException(BotErrorCode errorCode) {
		super(errorCode.getUserMessage());
		this.errorCode = errorCode;
	}

	public BotException(BotErrorCode errorCode, Throwable cause) {
		super(errorCode.getUserMessage(), cause);
		this.errorCode = errorCode;
	}

	public BotErrorCode getErrorCode() {
		return errorCode;
	}

	public String getUserMessage() {
		return errorCode.getUserMessage();
	}

	public int getCode() {
		return errorCode.getCode();
	}
}
