package com.discord_bot.backend.domain.stock.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockSuggestDto {
	private String code;
	private String nameKor;
	private String market;
}