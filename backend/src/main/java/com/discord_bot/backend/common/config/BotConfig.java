package com.discord_bot.backend.common.config;

import javax.security.auth.login.LoginException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

import com.discord_bot.backend.common.listener.DiscordListener;

@Configuration
public class BotConfig {

	private final ApplicationContext context;

	public BotConfig(ApplicationContext context) {
		this.context = context;
	}

	@Value("${discord.token}")
	private String token;
	private JDA jda;

	@Bean
	public JDA jdaBuilder() throws LoginException, InterruptedException {
		jda = JDABuilder.createDefault(token)
			.setActivity(Activity.playing("라라 대기중..."))
			.enableIntents(GatewayIntent.MESSAGE_CONTENT)
			.addEventListeners(context.getBean(DiscordListener.class))
			.build();

		jda.awaitReady();

		jda.updateCommands().addCommands(
			Commands.slash("ac-test", "autocomplete test")
				.addOption(OptionType.STRING, "query", "type...", true, true), // autocomplete=true
			Commands.slash("주식", "종목 시세 조회")
				.addOption(OptionType.STRING, "query", "종목명 검색", true, true)
		).queue();

		return jda;
	}
}
