package com.discord_bot.backend.domain.stock.service;

import java.util.List;

import com.discord_bot.backend.domain.stock.model.KisStockResponseDto;
import com.discord_bot.backend.domain.stock.model.StockSuggestDto;

public interface StockSearchService {

	List<StockSuggestDto> suggestByName(String q);

	KisStockResponseDto getCompact(String code);

}
