package com.discord_bot.backend.domain.stock.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.discord_bot.backend.common.ApiClient.KisApiClient;
import com.discord_bot.backend.domain.stock.model.KisDomesticQuoteRaw;
import com.discord_bot.backend.domain.stock.model.KisStockResponseDto;
import com.discord_bot.backend.domain.stock.model.StockSuggestDto;
import com.discord_bot.backend.domain.stock.repository.StockRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockSearchServiceImpl implements StockSearchService {
	private final StockRepository stockRepository;
	private final KisApiClient kisClient;

	private static final DecimalFormat PRICE_FMT = new DecimalFormat("#,##0");
	private static final DecimalFormat RATE_FMT = new DecimalFormat("#,##0.##");

	public List<StockSuggestDto> suggestByName(String q) {
		log.info("검색 시작: 입력값='{}'", q);
		var list = stockRepository.searchByNameKorOrderPrefixFirst(q, PageRequest.of(0, 10));
		log.info("DB 조회 결과 {}건", list.size());
		list.forEach(r ->
			log.debug("결과 → code={}, name={}", r.getCode(), r.getNameKor())
		);

		return list.stream()
			.map(s -> new StockSuggestDto(
				s.getCode(),
				s.getNameKor(),
				s.getMarket()
			))
			.toList();
	}

	public KisStockResponseDto getCompact(String code) {

		KisDomesticQuoteRaw raw = kisClient.getDomesticStock(code);
		if (raw == null || !"0".equals(raw.getRtCd()) || raw.getOutput() == null) {
			throw new IllegalStateException("시세 조회 실패: " +
				(raw == null ? "no-body" : raw.getMsgCd() + " " + raw.getMsg1()));
		}

		String name = stockRepository.findNameByCode(code);
		if (name == null)
			name = code;

		var out = raw.getOutput();

		String price = fmtIntLike(out.getStckPrpr());
		String diff = fmtSignedIntLike(out.getPrdyVrss());
		String rate = fmtRate(out.getPrdyCtrt());
		String sign = normalizeSign(out.getPrdyVrssSign());
		String diffText = String.format("%s (%s%%)", diff, rate);

		return new KisStockResponseDto(code, name, price, sign, diffText);
	}

	private String normalizeSign(String s) {
		if (s == null)
			return "0";
		return switch (s.trim()) {
			case "+", "1", "2" -> "+";
			case "-", "5" -> "-";
			default -> "0";
		};
	}

	private String fmtIntLike(String raw) {
		try {
			if (raw == null || raw.isBlank())
				return "-";
			BigDecimal v = new BigDecimal(raw.trim());
			return PRICE_FMT.format(v);
		} catch (Exception e) {
			return raw;
		}
	}

	private String fmtSignedIntLike(String raw) {
		try {
			if (raw == null || raw.isBlank())
				return "0";
			BigDecimal v = new BigDecimal(raw.trim());
			String base = PRICE_FMT.format(v.abs());
			return v.signum() > 0 ? "+" + base : (v.signum() < 0 ? "-" + base : "0");
		} catch (Exception e) {
			return raw;
		}
	}

	private String fmtRate(String raw) {
		try {
			if (raw == null || raw.isBlank())
				return "0";
			BigDecimal v = new BigDecimal(raw.trim());
			return RATE_FMT.format(v);
		} catch (Exception e) {
			return raw;
		}
	}
}
