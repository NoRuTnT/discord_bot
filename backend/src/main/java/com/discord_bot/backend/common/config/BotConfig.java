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

	private static final String COMMAND_STOCK = "주식";
	private static final String COMMAND_CHAT = "라라";
	private static final String COMMAND_PARTY = "파티";
	private static final String COMMAND_DICE = "주사위";
	private static final String COMMAND_CHAT_DATES = "채팅날짜";
	private static final String COMMAND_CHAT_SUMMARY = "채팅요약";
	private static final String OPTION_QUESTION = "질문";
	private static final String OPTION_LIMIT = "limit";
	private static final String OPTION_DATE = "date";

	private final ApplicationContext context;

	public BotConfig(ApplicationContext context) {
		this.context = context;
	}

	@Value("${discord.token}")
	private String token;

	@Bean
	public JDA jdaBuilder() throws LoginException, InterruptedException {
		JDA jda = JDABuilder.createDefault(token)
			.setActivity(Activity.playing("라라 대기중..."))
			.enableIntents(GatewayIntent.MESSAGE_CONTENT)
			.addEventListeners(context.getBean(DiscordListener.class))
			.build();

		jda.awaitReady();

		jda.updateCommands().addCommands(
			Commands.slash(COMMAND_STOCK, "종목 시세 조회")
				.addOption(OptionType.STRING, "query", "종목명 검색", true, true),
			Commands.slash(COMMAND_CHAT, "라라봇에게 질문합니다")
				.addOption(OptionType.STRING, OPTION_QUESTION, "질문 내용을 입력해주세요", true),
			Commands.slash(COMMAND_PARTY, "파티짜줘 링크를 보여줍니다"),
			Commands.slash(COMMAND_DICE, "주사위 게임을 시작합니다"),
			Commands.slash(COMMAND_CHAT_DATES, "요약 가능한 채팅 날짜를 조회합니다")
				.addOption(OptionType.INTEGER, OPTION_LIMIT, "조회할 날짜 개수 (기본값 10)", false),
			Commands.slash(COMMAND_CHAT_SUMMARY, "특정 날짜의 채팅 내용을 요약합니다")
				.addOption(OptionType.STRING, OPTION_DATE, "요약할 날짜 (YYYY-MM-DD)", true)
		).queue();

		return jda;
	}
}
