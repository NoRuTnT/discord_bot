package com.discord_bot.backend.config;

import javax.security.auth.login.LoginException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.exceptions.RateLimitedException;

@Configuration
public class BotConfig {

	@Value("${discord.token}")
	private String token;

	@Bean
	public JDABuilder jdaBuilder() throws LoginException, RateLimitedException {
		return JDABuilder.createDefault(token);
	}
}
