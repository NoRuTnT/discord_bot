package com.discord_bot.backend.config;

import javax.security.auth.login.LoginException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;

import com.discord_bot.backend.listener.DiscordListener;

@Configuration
public class BotConfig {

	private final ApplicationContext context;

	public BotConfig(ApplicationContext context) {
		this.context = context;
	}

	@Value("${discord.token}")
	private String token;

	@Bean
	public JDA jdaBuilder() throws LoginException {
		return JDABuilder.createDefault(token)
			.setActivity(Activity.playing("라라 대기"))
			.enableIntents(GatewayIntent.MESSAGE_CONTENT)
			.addEventListeners(context.getBean(DiscordListener.class))
			.build();
	}
}
