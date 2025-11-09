package com.discord_bot.backend.common.ApiClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.discord_bot.backend.domain.stock.model.KisDomesticQuoteRaw;
import com.discord_bot.backend.domain.stock.model.KisStockResponseDto;
import com.discord_bot.backend.domain.stock.model.KisTokenResponseDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisApiClient {

	private final RestTemplate restTemplate;

	@Value("${hantu.api.url}")
	private String BASE_URL;

	@Value("${hantu.api.appkey}")
	private String APP_KEY;

	@Value("${hantu.api.appsecret}")
	private String APP_SECRET;

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter KIS_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

	private volatile String cachedAccessToken;
	private volatile Instant tokenExpireAt = Instant.EPOCH;
	private final ReentrantLock tokenLock = new ReentrantLock();

	public String getAccessToken() {

		if (cachedAccessToken != null && Instant.now().isBefore(tokenExpireAt.minusSeconds(300))) {
			return cachedAccessToken;
		}
		tokenLock.lock();
		try {

			if (cachedAccessToken != null && Instant.now().isBefore(tokenExpireAt.minusSeconds(300))) {
				return cachedAccessToken;
			}
			var url = BASE_URL + "/oauth2/tokenP";
			var headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);

			var body = Map.of(
				"grant_type", "client_credentials",
				"appkey", APP_KEY,
				"appsecret", APP_SECRET
			);

			var entity = new HttpEntity<>(body, headers);
			var resp = restTemplate.exchange(url, HttpMethod.POST, entity, KisTokenResponseDto.class);
			var tokenRes = resp.getBody();

			if (tokenRes == null || tokenRes.getAccessToken() == null) {
				throw new IllegalStateException("토큰 발급 실패: 응답 본문이 비었습니다.");
			}

			cachedAccessToken = tokenRes.getAccessToken();
			tokenExpireAt = computeExpireInstant(tokenRes);

			return cachedAccessToken;
		} finally {
			tokenLock.unlock();
		}
	}

	private Instant computeExpireInstant(KisTokenResponseDto dto) {
		String expireAtStr = dto.getExpireAtStr();
		if (expireAtStr != null && !expireAtStr.isBlank()) {
			Instant absoluteExp = LocalDateTime.parse(expireAtStr.trim(), KIS_TS).atZone(KST).toInstant();
			return absoluteExp.minus(CLOCK_SKEW);
		}

		Long expiresIn = dto.getExpiresIn();
		if (expiresIn != null) {
			long sec = expiresIn;
			if (sec > 60L * 60L * 24L * 365L) {
				sec = sec / 1000L;
			}
			return Instant.now().plusSeconds(sec).minus(CLOCK_SKEW);
		}

		throw new IllegalStateException("No expiration info in token response");
	}

	public KisDomesticQuoteRaw getDomesticStock(String code) {
		var uri = UriComponentsBuilder.fromHttpUrl(
				BASE_URL + "/uapi/domestic-stock/v1/quotations/inquire-price")
			.queryParam("FID_COND_MRKT_DIV_CODE", "J")
			.queryParam("FID_INPUT_ISCD", code)
			.build(true)
			.toUri();

		var headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("authorization", "Bearer " + getAccessToken());
		headers.set("appkey", APP_KEY);
		headers.set("appsecret", APP_SECRET);
		headers.set("tr_id", "FHKST01010100");
		headers.set("custtype", "P");

		var entity = new HttpEntity<>(null, headers);

		try {
			var resp = restTemplate.exchange(uri, HttpMethod.GET, entity, KisDomesticQuoteRaw.class);
			return resp.getBody();
		} catch (RestClientResponseException e) {
			throw new IllegalStateException("시세 조회 실패: " + e.getRawStatusCode() + " " + e.getResponseBodyAsString(), e);
		}
	}

	public KisStockResponseDto getOverseaStock(String code, String excd) {
		var uri = UriComponentsBuilder.fromHttpUrl(
				BASE_URL + "/uapi/overseas-price/v1/quotations/price-detail")
			.queryParam("AUTH", "J") //todo 요구하는 사용자인증정보 확인
			.queryParam("EXCD", excd)
			.queryParam("SYMB", code)
			.build(true)
			.toUri();

		var headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("authorization", "Bearer " + getAccessToken());
		headers.set("appkey", APP_KEY);
		headers.set("appsecret", APP_SECRET);
		headers.set("tr_id", "HHDFS76200200");
		headers.set("custtype", "P");

		var entity = new HttpEntity<>(null, headers);

		try {
			var resp = restTemplate.exchange(uri, HttpMethod.GET, entity, KisStockResponseDto.class);
			return resp.getBody();
		} catch (RestClientResponseException e) {
			throw new IllegalStateException("시세 조회 실패: " + e.getRawStatusCode() + " " + e.getResponseBodyAsString(), e);
		}
	}

}
