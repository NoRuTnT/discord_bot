package com.discord_bot.backend.domain.stock.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KisStockResponseDto {
	private String code;
	private String name;
	private String price;
	private String sign;
	private String diffText;

}
