package com.discord_bot.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpClientConfig {
	@Bean
	public RestTemplate restTemplate() {
		var f = new SimpleClientHttpRequestFactory();
		f.setConnectTimeout(5000);
		f.setReadTimeout(5000);
		return new RestTemplate(f);
	}
}