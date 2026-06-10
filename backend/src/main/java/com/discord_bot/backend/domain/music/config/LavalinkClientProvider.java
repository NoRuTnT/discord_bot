package com.discord_bot.backend.domain.music.config;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import dev.arbjerg.lavalink.client.Helpers;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.NodeOptions;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(LavalinkProperties.class)
public class LavalinkClientProvider {

	private final LavalinkProperties properties;
	private LavalinkClient client;

	public synchronized LavalinkClient getOrCreate(String discordToken) {
		if (client != null) {
			return client;
		}

		client = new LavalinkClient(Helpers.getUserIdFromToken(discordToken));
		client.addNode(new NodeOptions.Builder()
			.setName("main")
			.setServerUri(properties.serverUri())
			.setPassword(properties.getPassword())
			.build());
		return client;
	}

	@PreDestroy
	public synchronized void close() {
		if (client != null) {
			client.close();
			client = null;
		}
	}
}
