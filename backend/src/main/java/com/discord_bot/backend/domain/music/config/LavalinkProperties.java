package com.discord_bot.backend.domain.music.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "lavalink")
public class LavalinkProperties {

	private String host;
	private int port;
	private String password;
	private boolean ssl;

	public URI serverUri() {
		String scheme = ssl ? "https" : "http";
		return URI.create(String.format("%s://%s:%d", scheme, host, port));
	}
}
