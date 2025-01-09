package com.discord_bot.backend.listener;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import com.discord_bot.backend.service.AudioService;
import com.discord_bot.backend.service.GPTService;
import com.google.api.services.youtube.model.SearchResult;

@Component
public class DiscordListener extends ListenerAdapter {

	private final AudioService audioService;  // final: 불변성 제공
	private final GPTService gptService;

	@Autowired
	public DiscordListener(AudioService audioService, GPTService gptService) {
		this.audioService = audioService;
		this.gptService = gptService;
	}

	private static final Logger logger = LoggerFactory.getLogger(DiscordListener.class);

	private final Map<Long, List<SearchResult>> searchResultsMap = new ConcurrentHashMap<>();

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (event.getAuthor().isBot()) {
			return;
		}
		String message = event.getMessage().getContentRaw();
		Member member = event.getMember();
		if (member == null) {
			event.getChannel().sendMessage("이 명령어는 서버에서만 사용할 수 있습니다.").queue();
			return;
		}

		Long userId = Long.valueOf(event.getAuthor().getIdLong());

		String command = getCommand(message);

		switch (command) {
			case "!play":
				PlayCommand(message, member, event, userId);
				break;
			case "!list":
				ListCommand(event);
				break;
			case "!stop":
				StopCommand(event);
				break;
			case "!pause":
				PauseCommand(event);
				break;
			case "!resume":
				ResumeCommand(event);
				break;
			case "!help":
				HelpCommand(event);
				break;
			case "!gpt":
				GptCommand(message, event);
				break;
			default:
				break;
		}
	}

	private String getCommand(String message) {
		message = message.trim().toLowerCase(); // 공백제거 및 소문자변환
		if (message.startsWith("!play ")) {
			return "!play";
		} else if (message.equals("!list")) {
			return "!list";
		} else if (message.equals("!stop")) {
			return "!stop";
		} else if (message.equals("!pause")) {
			return "!pause";
		} else if (message.equals("!resume")) {
			return "!resume";
		} else if (message.equals("!help")) {
			return "!help";
		} else if (message.startsWith("!gpt ")) {
			return "!gpt";
		} else {
			return "";
		}
	}

	private void PlayCommand(String message, Member member, MessageReceivedEvent event, Long userId) {
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

				event.getChannel().sendMessage(response.toString()).queue(
					sentMessage -> {
						// 메시지가 성공적으로 전송되었을 때의 처리

						// 저장된 사용자의 입력(예: 번호 선택)을 처리 (입력 로직 구현 필요)
						waitForUserInput(event.getChannel(), userId, sentMessage);

					});

			} catch (GeneralSecurityException | IOException e) {
				event.getChannel().sendMessage("유튜브 검색 중 오류가 발생했습니다. 나중에 다시 시도해주세요.").queue();
				logger.error("YouTube 검색 오류: ", e);
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

	private void StopCommand(MessageReceivedEvent event) {
		try {
			audioService.stopTrack(event.getGuild()); // AudioService의 stopPlayback() 호출
			event.getChannel().sendMessage("현재 음악 재생 중지.").queue(); // 알림 메시지
		} catch (Exception e) {
			event.getChannel().sendMessage("음악을 멈추는 도중 문제가 발생했습니다.").queue(); // 예외 처리
			logger.error("Error stopping playback: ", e);
		}
	}

	private void PauseCommand(MessageReceivedEvent event) {
		try {
			audioService.pauseTrack(event.getGuild()); // AudioService의 pausePlayback() 호출
			event.getChannel().sendMessage("현재 음악 재생이 일시정지되었습니다.").queue(); // 성공 메시지 전송
		} catch (Exception e) {
			event.getChannel().sendMessage("음악을 일시정지하는 도중 문제가 발생했습니다.").queue(); // 예외 처리
			logger.error("Error pausing playback: ", e);
		}
	}

	private void ResumeCommand(MessageReceivedEvent event) {
		try {
			audioService.resumeTrack(event.getGuild()); // AudioService의 resumePlayback() 호출
			event.getChannel().sendMessage("현재 음악 재생이 다시 시작되었습니다.").queue(); // 성공 메시지 전송
		} catch (Exception e) {
			event.getChannel().sendMessage("음악을 재개하는 도중 문제가 발생했습니다.").queue(); // 예외 처리
			logger.error("Error resuming playback: ", e);
		}
	}

	private void HelpCommand(MessageReceivedEvent event) {
		try {
			event.getChannel().sendMessage("도움말 출력.").queue(); // 알림 메시지
		} catch (Exception e) {
			event.getChannel().sendMessage("도움말을 불러오지못했습니다.").queue(); // 예외 처리
			logger.error("Error send help message: ", e);
		}
	}

	public void waitForUserInput(MessageChannelUnion channel, Long userId, Message sentMessage) {
		// 선택 입력 대기 로직
		channel.sendMessage("재생할 번호를 선택해주세요.").queue();

		channel.getJDA().addEventListener(new ListenerAdapter() {
			@Override
			public void onMessageReceived(MessageReceivedEvent event) {
				if (event.getAuthor().getIdLong() == userId) {                   // 사용자의 입력만 처리
					String messageContent = event.getMessage().getContentRaw();

					try {
						// 입력된 번호를 인덱스로 변환
						int selectedIndex = Integer.parseInt(messageContent) - 1;

						// 유효한 선택인지 확인
						if (searchResultsMap.containsKey(userId)) {
							List<SearchResult> results = searchResultsMap.get(userId);
							if (selectedIndex >= 0 && selectedIndex < results.size()) {

								// 선택된 비디오 정보
								SearchResult selectedResult = results.get(selectedIndex);
								String videoId = selectedResult.getId().getVideoId(); // Video ID 가져오기

								// 유튜브 URL 생성 및 재생목록 추가
								String url = createYouTubeUrl(videoId); // URL 생성 메서드 호출
								Member member = event.getMember(); // 입력한 사용자의 멤버 객체 가져오기

								if (member != null) {
									audioService.addTrackToQueue(url, event.getGuild(), member); // 재생목록에 추가
									channel.sendMessage("선택된 영상이 재생목록에 추가되었습니다: "
										+ selectedResult.getSnippet().getTitle()).queue();
								} else {
									channel.sendMessage("사용자 정보를 가져오지 못했습니다.").queue();
								}

								// 리스트 메시지 삭제
								sentMessage.delete().queue();

								// 검색 결과 Map에서 사용자 데이터 제거
								searchResultsMap.remove(userId);

								// 이벤트 리스너 제거
								channel.getJDA().removeEventListener(this);
							} else {
								channel.sendMessage("잘못된 번호입니다. 리스트의 번호를 정확히 입력해주세요.").queue();
							}
						}
					} catch (NumberFormatException e) {
						channel.sendMessage("숫자를 입력해주세요!").queue();
					}
				}
			}
		});
	}

	private String createYouTubeUrl(String videoId) {
		return "https://www.youtube.com/watch?v=" + videoId;
	}

	// private void NumberInput(String message, Member member, MessageReceivedEvent event, Long userId) {
	// 	List<SearchResult> results = searchResultsMap.get(userId);
	//
	// 	if (results == null || results.isEmpty()) {
	// 		event.getChannel().sendMessage("선택 가능한 검색 결과가 없습니다. 새로 검색해주세요.").queue();
	// 		return;
	// 	}
	//
	// 	if (results != null) {
	// 		try {
	// 			int index = Integer.parseInt(message.trim()) - 1;
	// 			if (index >= 0 && index < results.size()) {
	// 				String videoId = results.get(index).getId().getVideoId();
	// 				String url = "https://www.youtube.com/watch?v=" + videoId;
	// 				if (member != null) {
	// 					audioService.addTrackToQueue(url, event.getGuild(), member);
	// 					event.getChannel().sendMessage("재생목록에 추가되었습니다.").queue();
	// 				} else {
	// 					event.getChannel().sendMessage("로그인정보를 받아오지못했습니다.").queue();
	// 				}
	// 				searchResultsMap.remove(userId); // 다 끝났으면 검색 결과 삭제
	// 			} else {
	// 				event.getChannel().sendMessage("잘못된 값 입니다. 리스트의 번호를 정확하게 입력해주세요.").queue();
	// 			}
	// 		} catch (NumberFormatException e) {
	// 			event.getChannel().sendMessage("숫자를 입력해주세요.").queue();
	// 		}
	// 	}
	// }

	private void GptCommand(String message, MessageReceivedEvent event) {
		String question = message.substring(5).trim();
		CompletableFuture.runAsync(() -> {
			String response = gptService.getResponse(question);
			event.getChannel().sendMessage(response).queue();
		});
		// String response = gptService.getResponse(question);
		// event.getChannel().sendMessage(response).queue();
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
