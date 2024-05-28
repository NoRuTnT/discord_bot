package com.discord_bot.backend.listener;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import com.discord_bot.backend.service.AudioService;
import com.discord_bot.backend.service.GPTService;
import com.google.api.services.youtube.model.SearchResult;

@Component
public class DiscordListener extends ListenerAdapter {

	@Autowired
	private AudioService audioService;

	@Autowired
	private GPTService gptService;

	private final Map<Long, List<SearchResult>> searchResultsMap = new HashMap<>();

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (event.getAuthor().isBot())
			return;

		String message = event.getMessage().getContentRaw();
		Member member = event.getMember();
		long userId = event.getAuthor().getIdLong();

		String command = getCommand(message);

		switch (command) {
			case "!play":
				PlayCommand(message, member, event, userId);
				break;
			case "!list":
				ListCommand(event);
				break;
			case "!gpt":
				GptCommand(message, event);
				break;
			default:
				if (searchResultsMap.containsKey(userId) && isNumeric(message.trim())) {
					NumberInput(message, member, event, userId);
				}
				break;
		}
	}

	private String getCommand(String message) {
		if (message.startsWith("!play ")) {
			return "!play";
		} else if (message.equals("!list")) {
			return "!list";
		} else if (message.startsWith("!gpt ")) {
			return "!gpt";
		} else {
			return "";
		}
	}

	private void PlayCommand(String message, Member member, MessageReceivedEvent event, long userId) {
		String query = message.substring(6).trim();
		if (isValidURL(query)) {
			if (member != null) {
				audioService.addTrackToQueue(query, event.getGuild(), member);
				event.getChannel().sendMessage("추가완료").queue();
			} else {
				event.getChannel().sendMessage("로그인정보를 받아오지못했습니다.").queue();
			}
		} else {
			try {
				List<SearchResult> results = audioService.searchYouTube(query);
				searchResultsMap.put(userId, results);
				StringBuilder response = new StringBuilder("검색 결과:\n");
				for (int i = 0; i < results.size(); i++) {
					SearchResult result = results.get(i);
					response.append(i + 1).append(". ").append(result.getSnippet().getTitle()).append("\n");
				}
				response.append("재생하고싶은 영상의 번호를 입력해주세요.");
				event.getChannel().sendMessage(response.toString()).queue();
			} catch (GeneralSecurityException | IOException e) {
				event.getChannel().sendMessage("유튜브 검색 실패했어요: " + e.getMessage()).queue();
			}
		}
	}

	private void ListCommand(MessageReceivedEvent event) {
		List<String> queue = audioService.getQueue(event.getGuild());
		if (queue.isEmpty()) {
			event.getChannel().sendMessage("재생목록이 비었습니다.").queue();
		} else {
			StringBuilder response = new StringBuilder("현재 재생목록:\n");
			for (int i = 0; i < queue.size(); i++) {
				response.append(i + 1).append(". ").append(queue.get(i)).append("\n");
			}
			event.getChannel().sendMessage(response.toString()).queue();
		}
	}

	private void NumberInput(String message, Member member, MessageReceivedEvent event, long userId) {
		List<SearchResult> results = searchResultsMap.get(userId);
		if (results != null) {
			try {
				int index = Integer.parseInt(message.trim()) - 1;
				if (index >= 0 && index < results.size()) {
					String videoId = results.get(index).getId().getVideoId();
					String url = "https://www.youtube.com/watch?v=" + videoId;
					if (member != null) {
						audioService.addTrackToQueue(url, event.getGuild(), member);
						event.getChannel().sendMessage("재생목록에 추가되었습니다.").queue();
					} else {
						event.getChannel().sendMessage("로그인정보를 받아오지못했습니다.").queue();
					}
					searchResultsMap.remove(userId); // 다 끝났으면 검색 결과 삭제
				} else {
					event.getChannel().sendMessage("잘못된 값 입니다. 리스트의 번호를 정확하게 입력해주세요.").queue();
				}
			} catch (NumberFormatException e) {
				event.getChannel().sendMessage("숫자를 입력해주세요.").queue();
			}
		}
	}

	private void GptCommand(String message, MessageReceivedEvent event) {
		String question = message.substring(5).trim();
		String response = gptService.getResponse(question);
		event.getChannel().sendMessage(response).queue();
	}

	private boolean isValidURL(String url) {
		String regex = "^(https?|ftp)://[^\s/$.?#].[^\s]*$";
		Pattern pattern = Pattern.compile(regex);
		return pattern.matcher(url).matches();
	}

	private boolean isNumeric(String str) {
		return str != null && str.matches("\\d+");
	}
}
