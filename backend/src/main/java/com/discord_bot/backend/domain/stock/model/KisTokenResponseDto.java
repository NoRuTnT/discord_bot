package com.discord_bot.backend.domain.stock.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KisTokenResponseDto {
	@JsonProperty("access_token")
	private String accessToken;

	@JsonProperty("access_token_token_expired")
	private String expireAtStr;

	@JsonProperty("expires_in")
	private Long expiresIn;
}
