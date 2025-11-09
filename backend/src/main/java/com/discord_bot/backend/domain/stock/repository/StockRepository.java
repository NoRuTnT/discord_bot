package com.discord_bot.backend.domain.stock.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.discord_bot.backend.domain.stock.model.Stock;

public interface StockRepository extends JpaRepository<Stock, String> {

	// 전방일치 + 부분일치 한 번에 검색, 전방일치 먼저 정렬
	@Query("""
		SELECT s 
		FROM Stock s
		WHERE s.nameKor LIKE CONCAT(:q, '%')
		   OR s.nameKor LIKE CONCAT('%', :q, '%')
		ORDER BY CASE WHEN s.nameKor LIKE CONCAT(:q, '%') THEN 0 ELSE 1 END,
		         s.nameKor ASC
		""")
	List<Stock> searchByNameKorOrderPrefixFirst(@Param("q") String q, Pageable pageable);

	@Query("select s.nameKor from Stock s where s.code = :code")
	String findNameByCode(@Param("code") String code);

}