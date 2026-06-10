package com.discord_bot.backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CommonApiExceptionHandler {

	@ExceptionHandler(BotException.class)
	public ResponseEntity<ErrorResponse> handleBotException(BotException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new ErrorResponse(e.getCode(), e.getUserMessage()));
	}
}
