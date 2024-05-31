package com.discord_bot.backend.util;

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
		YouTube.Search.List request = youtubeService.search().list("snippet");
		SearchListResponse response = request.setQ(query)
			.setKey(apiKey)
			.setMaxResults(5L)
			.execute();
		return response.getItems();
	}

}
