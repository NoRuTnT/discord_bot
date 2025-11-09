package com.discord_bot.backend.music.util;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;

@Component
public class YouTubeUtil {

	@Value("${youtube.api.key}")
	private String apiKey;

	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

	private YouTube getService() throws GeneralSecurityException, IOException {
		return new YouTube.Builder(
			GoogleNetHttpTransport.newTrustedTransport(),
			JSON_FACTORY,
			null)
			.setApplicationName("discord-bot")
			.build();
	}

	public List<SearchResult> search(String query) throws GeneralSecurityException, IOException {
		YouTube youtubeService = getService();
		YouTube.Search.List request = youtubeService.search()
			.list(List.of("snippet")); //기본정보만포함 나중에 조회수 좋아요,싫어요등 통계필요하면 videoId를 이용해 데이터를 받아오는 코드추가필요
		SearchListResponse response = request.setQ(query)
			.setKey(apiKey)
			.setMaxResults(5L)
			.execute();

		return response.getItems();
	}

	public String extractAudioUrl(String youtubeUrl) {

		return youtubeUrl;
	}
}
