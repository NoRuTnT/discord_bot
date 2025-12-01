package com.discord_bot.backend.common.kafka.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotEventRequestDto {
	private String userName;
	private String element;
	private long timestamp;
}
