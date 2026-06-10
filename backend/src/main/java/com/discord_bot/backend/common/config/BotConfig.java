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
import com.discord_bot.backend.domain.music.config.LavalinkClientProvider;

import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.libraries.jda.JDAVoiceUpdateListener;

@Configuration
public class BotConfig {

	private static final String COMMAND_STOCK = "주식";
	private static final String COMMAND_CHAT = "라라";
	private static final String COMMAND_PARTY = "파티";
	private static final String COMMAND_DICE = "주사위";
	private static final String COMMAND_MUSIC_PANEL = "음악패널";
	private static final String OPTION_QUESTION = "질문";

	private final ApplicationContext context;
	private final LavalinkClientProvider lavalinkClientProvider;

	@Value("${discord.token}")
	private String token;

	public BotConfig(ApplicationContext context, LavalinkClientProvider lavalinkClientProvider) {
		this.context = context;
		this.lavalinkClientProvider = lavalinkClientProvider;
	}

	@Bean
	public JDA jdaBuilder() throws LoginException, InterruptedException {
		LavalinkClient lavalinkClient = lavalinkClientProvider.getOrCreate(token);

		JDA jda = JDABuilder.createDefault(token)
			.setActivity(Activity.playing("라라 대기중..."))
			.enableIntents(GatewayIntent.MESSAGE_CONTENT)
			.setVoiceDispatchInterceptor(new JDAVoiceUpdateListener(lavalinkClient))
			.addEventListeners(context.getBean(DiscordListener.class))
			.build();

		jda.awaitReady();

		jda.updateCommands().addCommands(
			Commands.slash(COMMAND_STOCK, "종목 시세 조회")
				.addOption(OptionType.STRING, "query", "종목명 검색", true, true),
			Commands.slash(COMMAND_CHAT, "라라봇에게 질문합니다")
				.addOption(OptionType.STRING, OPTION_QUESTION, "질문 내용을 입력해주세요", true),
			Commands.slash(COMMAND_PARTY, "파티집결 링크를 보여줍니다"),
			Commands.slash(COMMAND_DICE, "주사위 게임을 시작합니다"),
			Commands.slash(COMMAND_MUSIC_PANEL, "음악 제어 패널을 엽니다")
		).queue();

		return jda;
	}
}
