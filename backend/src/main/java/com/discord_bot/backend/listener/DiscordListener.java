package com.discord_bot.backend.listener;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import com.discord_bot.backend.service.AudioService;
import com.discord_bot.backend.service.GPTService;
import com.discord_bot.backend.service.ImageService;
import com.google.api.services.youtube.model.SearchResult;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Component
public class DiscordListener extends ListenerAdapter {

	private final AudioService audioService;  // final: 불변성 제공
	private final GPTService gptService;
	private final ImageService imageService;

	@Autowired
	public DiscordListener(AudioService audioService, GPTService gptService) {
		this.audioService = audioService;
		this.gptService = gptService;
		this.imageService = new ImageService();
	}

	private static final Logger logger = LoggerFactory.getLogger(DiscordListener.class);

	private final Map<String, List<SearchResult>> searchResultsMap = new ConcurrentHashMap<>();
	private final Map<String, File[]> userGeneratedImages = new HashMap<>(); // 사용자별 생성된 이미지 저장
	private final Map<Long, String> messageToUser = new HashMap<>();

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (event.getAuthor().isBot()) {
			return;
		}
		Message message = event.getMessage();
		String content = message.getContentRaw();
		List<Attachment> attachments = message.getAttachments();
		TextChannel channel = event.getChannel().asTextChannel();
		String userId = event.getAuthor().getId();

		Member member = event.getMember();
		if (member == null) {
			event.getChannel().sendMessage("이 명령어는 서버에서만 사용할 수 있습니다.").queue();
			return;
		}

		String command = getCommand(content);

		switch (command) {
			case "!play":
				PlayCommand(content, member, event, userId);
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
				GptCommand(content, event);
				break;
			case "!generate":
				GenerateCommand(content, event);
				break;
			case "!character":
				CharacterCommand(attachments, event);
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
		} else if (message.startsWith("!generate ")) {
			return "!generate";
		} else if (message.startsWith("!character ")) {
			return "!character";
		} else {
			return "";
		}
	}

	String emoji1 = "1️⃣";
	String emoji2 = "2️⃣";
	String emoji3 = "3️⃣";
	String emoji4 = "4️⃣";
	Emoji reactionEmoji1 = Emoji.fromUnicode(emoji1);
	Emoji reactionEmoji2 = Emoji.fromUnicode(emoji2);
	Emoji reactionEmoji3 = Emoji.fromUnicode(emoji3);
	Emoji reactionEmoji4 = Emoji.fromUnicode(emoji4);

	private void GenerateCommand(String message, MessageReceivedEvent event) {
		String prompt = message.substring(10).trim();
		event.getChannel().sendMessage("🎨 이미지 생성 중... 잠시만 기다려 주세요!").queue();
		String userId = event.getAuthor().getId();
		File[] generatedImages = imageService.generateMultipleImages(prompt);

		if (generatedImages != null) {
			userGeneratedImages.put(userId, generatedImages);

			EmbedBuilder embed = new EmbedBuilder()
				.setTitle("✅ 4개 이미지 생성 완료!")
				.setDescription("이모지를 클릭하여 원하는 스타일을 선택하세요!")
				.setColor(Color.ORANGE);

			event.getChannel().sendMessageEmbeds(embed.build())
				.addFiles(
					net.dv8tion.jda.api.utils.FileUpload.fromData(generatedImages[0]),
					net.dv8tion.jda.api.utils.FileUpload.fromData(generatedImages[1]),
					net.dv8tion.jda.api.utils.FileUpload.fromData(generatedImages[2]),
					net.dv8tion.jda.api.utils.FileUpload.fromData(generatedImages[3])
				)
				.queue(imgmessage -> {
					imgmessage.addReaction(reactionEmoji1).queue();
					imgmessage.addReaction(reactionEmoji2).queue();
					imgmessage.addReaction(reactionEmoji3).queue();
					imgmessage.addReaction(reactionEmoji4).queue();
					messageToUser.put(imgmessage.getIdLong(), userId);
				});
		} else {
			event.getChannel().sendMessage("⚠️ 이미지 생성 실패!").queue();
		}

	}

	@Override
	public void onMessageReactionAdd(MessageReactionAddEvent event) {
		if (event.getUser().isBot())
			return;
		long messageId = event.getMessageIdLong();
		String userId = messageToUser.get(messageId);
		if (userId == null || !userId.equals(event.getUserId()))
			return;

		int selectedIndex = -1;
		switch (event.getReaction().getEmoji().getName()) {
			case "1️⃣":
				selectedIndex = 0;
				break;
			case "2️⃣":
				selectedIndex = 1;
				break;
			case "3️⃣":
				selectedIndex = 2;
				break;
			case "4️⃣":
				selectedIndex = 3;
				break;
		}

		if (selectedIndex != -1) {
			File selectedImage = userGeneratedImages.get(userId)[selectedIndex];
			System.out.println(selectedIndex);
			event.getChannel().sendMessage("🎨 선택한 이미지 스타일로 다시 생성 중...").queue();

			File[] refinedImages = imageService.img2imgMultiple(selectedImage);
			if (refinedImages != null) {
				event.getChannel().sendMessage("✅ 새로운 4개 이미지 생성 완료!")
					.addFiles(
						net.dv8tion.jda.api.utils.FileUpload.fromData(refinedImages[0]),
						net.dv8tion.jda.api.utils.FileUpload.fromData(refinedImages[1]),
						net.dv8tion.jda.api.utils.FileUpload.fromData(refinedImages[2]),
						net.dv8tion.jda.api.utils.FileUpload.fromData(refinedImages[3])
					).queue();
			}
		}
	}

	private void CharacterCommand(List<Attachment> attachments, MessageReceivedEvent event) {
		Attachment imageAttachment = attachments.get(0);

		try {
			// 이미지를 임시폴더에 다운로드
			File inputImage = downloadImage(imageAttachment);
			if (inputImage != null) {
				event.getChannel().sendMessage("🎨 스타일 변환 중...").queue();

				File generatedImage = imageService.img2img(inputImage);
				if (generatedImage != null) {
					event.getChannel().sendMessage("✅ 변환 완료!")
						.addFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(generatedImage))
						.queue();
					generatedImage.delete();
				} else {
					event.getChannel().sendMessage("⚠️ 변환 실패!").queue();
				}

				inputImage.delete();
			}
		} catch (IOException e) {
			e.printStackTrace();
			event.getChannel().sendMessage("⚠️ 오류 발생: " + e.getMessage()).queue();
		}

	}

	private File downloadImage(Attachment attachment) throws IOException {
		OkHttpClient client = new OkHttpClient();
		Request request = new Request.Builder().url(attachment.getUrl()).build();
		Response response = client.newCall(request).execute();

		if (response.isSuccessful() && response.body() != null) {
			File tempFile = File.createTempFile("input", ".png");
			FileOutputStream outputStream = new FileOutputStream(tempFile);
			outputStream.write(response.body().bytes());
			outputStream.close();
			return tempFile;
		}
		return null;
	}

	private void PlayCommand(String message, Member member, MessageReceivedEvent event, String userId) {
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

						// 저장된 사용자의 입력을 처리 (입력 로직 구현 필요)
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

	public void waitForUserInput(MessageChannelUnion channel, String userId, Message sentMessage) {
		channel.sendMessage("재생할 번호를 선택해주세요.").queue();

		channel.getJDA().addEventListener(new ListenerAdapter() {
			@Override
			public void onMessageReceived(MessageReceivedEvent event) {
				if (event.getAuthor().getId().equals(userId)) {
					String messageContent = event.getMessage().getContentRaw();

					try {
						int selectedIndex = Integer.parseInt(messageContent) - 1;

						if (searchResultsMap.containsKey(userId)) {
							List<SearchResult> results = searchResultsMap.get(userId);
							if (selectedIndex >= 0 && selectedIndex < results.size()) {

								SearchResult selectedResult = results.get(selectedIndex);
								String videoId = selectedResult.getId().getVideoId();

								String url = createYouTubeUrl(videoId);
								Member member = event.getMember();

								if (member != null) {
									audioService.addTrackToQueue(url, event.getGuild(), member);
									channel.sendMessage("선택된 영상이 재생목록에 추가되었습니다: "
										+ selectedResult.getSnippet().getTitle()).queue();
								} else {
									channel.sendMessage("사용자 정보를 가져오지 못했습니다.").queue();
								}

								sentMessage.delete().queue();
								searchResultsMap.remove(userId);
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
