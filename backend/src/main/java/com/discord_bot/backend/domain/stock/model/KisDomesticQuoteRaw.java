package com.discord_bot.backend.domain.stock.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KisDomesticQuoteRaw {
	//성공여부
	@JsonProperty("rt_cd")
	private String rtCd;
	//응답코드
	@JsonProperty("msg_cd")
	private String msgCd;
	//메세지
	@JsonProperty("msg1")
	private String msg1;
	@JsonProperty("output")
	private Output output;

	@Data
	public static class Output {
		//업종 도메인 한글명
		@JsonProperty("bstp_kor_isnm")
		private String bstpKorIsnm;
		//현재가
		@JsonProperty("stck_prpr")
		private String stckPrpr;
		//전일대비
		@JsonProperty("prdy_vrss")
		private String prdyVrss;
		//부호
		@JsonProperty("prdy_vrss_sign")
		private String prdyVrssSign;
		//전일대비율
		@JsonProperty("prdy_ctrt")
		private String prdyCtrt;

	}
}
