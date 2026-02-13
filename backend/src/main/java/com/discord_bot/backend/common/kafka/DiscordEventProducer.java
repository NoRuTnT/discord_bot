package com.discord_bot.backend.common.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.discord_bot.backend.common.kafka.model.dto.BotEventRequestDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DiscordEventProducer {
	private final KafkaTemplate<String, Object> kafkaTemplate;

	public void sendBotChatEvent(BotEventRequestDto requestDto) {
		kafkaTemplate.send("lalabot-chat-topic",
			DiscordEventMessage.builder()
				.eventType("CHAT")
				.payload(requestDto)
				.build());
	}

	public void sendBotStartEvent(BotEventRequestDto requestDto) {
		kafkaTemplate.send("lalabot-event-topic",
			DiscordEventMessage.builder()
				.eventType("EVENT_START")
				.payload(requestDto)
				.build());
	}

}
