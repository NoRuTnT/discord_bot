package com.discord_bot.backend.domain.stock.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "stock")
public class Stock {

	@Id
	@Column(name = "code", length = 12, nullable = false)
	private String code;

	@Column(name = "name_kor", nullable = false, length = 200)
	private String nameKor;

	@Column(name = "market", nullable = false, length = 10)
	private String market;

	@Column(name = "isin", length = 12)
	private String isin;
}
