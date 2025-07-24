package com.discord_bot.backend.listener;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import com.discord_bot.backend.service.AudioService;
import com.discord_bot.backend.service.GPTService;
import com.discord_bot.backend.service.ImageService;
import com.google.api.services.youtube.model.SearchResult;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Component
public class DiscordListener extends ListenerAdapter {

	private final AudioService audioService;  // final: 불변성 제공
	private final GPTService gptService;
	private final ImageService imageService;

	@Autowired
	public DiscordListener(AudioService audioService, GPTService gptService, ImageService imageService) {
		this.audioService = audioService;
		this.gptService = gptService;
		this.imageService = imageService;
	}

	record TimedValue<T>(T value, Instant expiresAt) {
	}

	private final Map<Long, List<SearchResult>> searchResultsMap = new ConcurrentHashMap<>();
	private final Map<Long, TimedValue<File[]>> userGeneratedImages = new ConcurrentHashMap<>();
	private final Map<Long, Long> messageToUser = new ConcurrentHashMap<>();
	private final Map<Long, Boolean> requestInProgress = new ConcurrentHashMap<>();

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (event.getAuthor().isBot()) {
			return;
		}
		Message message = event.getMessage();
		String content = message.getContentRaw();
		List<Attachment> attachments = message.getAttachments();
		TextChannel channel = event.getChannel().asTextChannel();
		Long userId = event.getAuthor().getIdLong();
		Member member = event.getMember();
		if (member == null) {
			event.getChannel().sendMessage("이 명령어는 서버에서만 사용할 수 있습니다.").queue();
			return;
		}
		content = content.trim();

		if (content.startsWith("!help")) {
			log.info("help명령어");
			HelpCommand(event);
			return;
		}
		if (content.startsWith("!데굴데굴")) {
			log.info("주사위게임");
			DiceGameCommand(event);
			return;
		}

		if (content.startsWith("!파티")) {
			log.info("파티사이트");
			UrlCommand(event);
			return;
		}

		if (!content.startsWith("!라라")) {
			log.info("명령어아님");
			return;
		}

		String convertedMessage = null;

		try {
			convertedMessage = gptService.convertToCommand(content);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}

		log.info(convertedMessage);
		String command = getCommand(convertedMessage);

		switch (command) {
			case "!play":
				PlayCommand(convertedMessage, member, event, userId);
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
			case "!gpt":
				GptCommand(convertedMessage, event);
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
		} else if (message.startsWith("!url")) {
			return "!url";
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

	/**
	 이미지생성
	 **/

	private void GenerateCommand(String message, MessageReceivedEvent event) {
		String prompt = message.substring(10).trim();
		event.getChannel().sendMessage("🎨 이미지 생성 중... 잠시만 기다려 주세요!").queue();
		Long userId = event.getAuthor().getIdLong();
		File[] generatedImages = imageService.generateMultipleImages(prompt);

		if (generatedImages != null) {
			Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
			userGeneratedImages.put(userId, new TimedValue<>(generatedImages, expiresAt));

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
		Long userId = messageToUser.get(messageId);
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
			TimedValue<File[]> timed = userGeneratedImages.get(userId);
			if (timed != null) {
				if (timed.expiresAt().isAfter(Instant.now())) {
					File selectedImage = timed.value[selectedIndex];
					log.info(String.valueOf(selectedIndex));
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
				} else {
					userGeneratedImages.remove(userId);
				}
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

	/**
	 youtube 음악재생
	 **/

	private void PlayCommand(String message, Member member, MessageReceivedEvent event, Long userId) {
		String query = message.substring(6).trim();
		if (isValidURL(query)) {
			if (member != null) {
				audioService.addTrackToQueue(query, event.getGuild(), member);
				event.getChannel().sendMessage("영상이 재생목록에 추가되었습니다").queue();
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
						waitForUserInput(event.getChannel(), userId, sentMessage);
					});

			} catch (GeneralSecurityException | IOException e) {
				event.getChannel().sendMessage("유튜브 검색 중 오류가 발생했습니다. 나중에 다시 시도해주세요.").queue();
				log.error("YouTube 검색 오류: ", e);
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
			audioService.stopTrack(event.getGuild());
			event.getChannel().sendMessage("현재 음악 재생 중지.").queue();
		} catch (Exception e) {
			event.getChannel().sendMessage("음악을 멈추는 도중 문제가 발생했습니다.").queue();
			log.error("Error stopping playback: ", e);
		}
	}

	private void PauseCommand(MessageReceivedEvent event) {
		try {
			audioService.pauseTrack(event.getGuild());
			event.getChannel().sendMessage("현재 음악 재생이 일시정지되었습니다.").queue();
		} catch (Exception e) {
			event.getChannel().sendMessage("음악을 일시정지하는 도중 문제가 발생했습니다.").queue();
			log.error("Error pausing playback: ", e);
		}
	}

	private void ResumeCommand(MessageReceivedEvent event) {
		try {
			audioService.resumeTrack(event.getGuild()); // AudioService의 resumePlayback() 호출
			event.getChannel().sendMessage("현재 음악 재생이 다시 시작되었습니다.").queue(); // 성공 메시지 전송
		} catch (Exception e) {
			event.getChannel().sendMessage("음악을 재개하는 도중 문제가 발생했습니다.").queue(); // 예외 처리
			log.error("Error resuming playback: ", e);
		}
	}

	private void HelpCommand(MessageReceivedEvent event) {
		try {
			event.getChannel().sendMessage("```"
				+ "라라봇 명령어"
				+ "1. gpt사용 !라라 + 질문내용\n"
				+ "2. 주사위게임 !데굴데굴\n"
				+ "3. 파티짜줘 !파티\n"
				+ "```").queue(); // 알림 메시지
		} catch (Exception e) {
			event.getChannel().sendMessage("오류가 발생하여 도움말을 불러오지못했습니다").queue(); // 예외 처리
			log.error("Error send help message: ", e);
		}
	}

	private void UrlCommand(MessageReceivedEvent event) {
		try {
			event.getChannel().sendMessage("https://partycontrol.duckdns.org/").queue(); // 알림 메시지
		} catch (Exception e) {
			event.getChannel().sendMessage("오류가 발생하여 답변을 불러오지못했습니다").queue(); // 예외 처리
			log.error("Error send help message: ", e);
		}
	}

	public void waitForUserInput(MessageChannelUnion channel, Long userId, Message sentMessage) {
		// 선택 입력 대기 로직
		channel.sendMessage("재생할 번호를 선택해주세요.").queue();

		channel.getJDA().addEventListener(new ListenerAdapter() {
			@Override
			public void onMessageReceived(MessageReceivedEvent event) {
				if (event.getAuthor().getIdLong() == userId) {
					String messageContent = event.getMessage().getContentRaw();

					try {
						// 입력된 번호를 인덱스로 변환
						int selectedIndex = Integer.parseInt(messageContent) - 1;

						// 유효한 선택인지 확인
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

	private boolean isValidURL(String url) {
		String regex = "^(https?|ftp)://[^\s/$.?#].[^\s]*$";
		Pattern pattern = Pattern.compile(regex);
		return pattern.matcher(url).matches();
	}

	private boolean isNumeric(String str) {
		return str != null && str.matches("\\d+");
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
	// 				searchResultsMap.remove(userId);
	// 			} else {
	// 				event.getChannel().sendMessage("잘못된 값 입니다. 리스트의 번호를 정확하게 입력해주세요.").queue();
	// 			}
	// 		} catch (NumberFormatException e) {
	// 			event.getChannel().sendMessage("숫자를 입력해주세요.").queue();
	// 		}
	// 	}
	// }

	/**
	 gpt
	 **/

	private void GptCommand(String message, MessageReceivedEvent event) {
		Long userId = event.getAuthor().getIdLong();
		if (requestInProgress.getOrDefault(userId, false)) {
			event.getChannel().sendMessage("🐑 라라봇이 아직 이전 질문을 생각 중이에요. 조금만 기다려 주세요!").queue();
			return;
		}
		requestInProgress.put(userId, true);
		String question = message.substring("!gpt".length()).trim();

		EmbedBuilder waitingEmbed = new EmbedBuilder();
		waitingEmbed.setTitle("🐑 라라 응답 생성 중...");
		waitingEmbed.setDescription("잠시만 기다려 주세요. 라라봇이 열심히 생각하고 있어요 🧠💬");
		waitingEmbed.setColor(Color.GRAY);
		waitingEmbed.setFooter("질문자: " + event.getAuthor().getName());
		event.getChannel().sendMessageEmbeds(waitingEmbed.build()).queue(waitingMessage -> {

			String response = gptService.getResponse(question);

			EmbedBuilder responseEmbed = new EmbedBuilder();
			responseEmbed.setTitle("🐑 라라봇의 대답");
			responseEmbed.setDescription(response);
			responseEmbed.setColor(Color.ORANGE);
			responseEmbed.setTimestamp(Instant.now());
			responseEmbed.setFooter("질문자: " + event.getAuthor().getName());
			waitingMessage.editMessageEmbeds(responseEmbed.build()).queue();
			requestInProgress.remove(userId);
		});
	}

	/**
	 주사위
	 **/
	private final Set<String> participantIds = ConcurrentHashMap.newKeySet(); // 유저 ID로 저장
	private Message signUpMessage;

	public void DiceGameCommand(MessageReceivedEvent event) {
		//시작초기화
		participantIds.clear();
		signUpMessage = null;

		EmbedBuilder eb = new EmbedBuilder()
			.setTitle("🎲 주사위 게임 참가")
			.setDescription("버튼을 눌러 참가하거나 참가를 취소할 수 있어요!\n\n현재 참가자:\n(없음)")
			.setColor(Color.LIGHT_GRAY);

		event.getChannel().sendMessageEmbeds(eb.build())
			.setActionRow(
				Button.success("join_game", "✅ 게임 참가"),
				Button.danger("leave_game", "❌ 참가 취소"),
				Button.primary("start_game", "🎯 게임 시작")
			).queue(msg -> signUpMessage = msg);
	}

	public void onButtonInteraction(ButtonInteractionEvent event) {
		String userId = event.getUser().getId();
		Guild guild = event.getGuild();

		switch (event.getComponentId()) {
			case "join_game" -> {
				if (participantIds.contains(userId)) {
					event.reply("이미 참가하셨어요!").setEphemeral(true).queue();
				} else {
					participantIds.add(userId);
					updateSignupEmbed(guild);
					event.deferEdit().queue();
				}
			}

			case "leave_game" -> {
				if (!participantIds.contains(userId)) {
					event.reply("아직 참가하지 않으셨어요.").setEphemeral(true).queue();
				} else {
					participantIds.remove(userId);
					updateSignupEmbed(guild);
					event.deferEdit().queue();
				}
			}

			case "start_game" -> {
				List<Member> participants = participantIds.stream()
					.map(id ->
						guild.retrieveMemberById(id).complete()) // 혹은 retrieveMemberById(id).complete()
					.filter(Objects::nonNull)
					.toList();

				startDiceRolling(event, participants);
			}
		}
	}

	private void updateSignupEmbed(Guild guild) {
		EmbedBuilder updated = new EmbedBuilder()
			.setTitle("🎲 주사위 게임 참가")
			.setColor(Color.LIGHT_GRAY);

		if (participantIds.isEmpty()) {
			updated.setDescription("버튼을 눌러 참가하거나 참가를 취소할 수 있어요!\n\n현재 참가자:\n(없음)");
		} else {
			String list = participantIds.stream()
				.map(id -> {
					Member m = guild.retrieveMemberById(id).complete();
					return m != null ? m.getEffectiveName() : "(알 수 없음)";
				})
				.collect(Collectors.joining("\n"));

			updated.setDescription("버튼을 눌러 참가하거나 참가를 취소할 수 있어요!\n\n현재 참가자:\n" + list);
		}

		signUpMessage.editMessageEmbeds(updated.build()).queue();
	}

	public void startDiceRolling(ButtonInteractionEvent event, List<Member> participants) {

		Map<Member, Integer> finalResults = new HashMap<>();
		Map<Member, Integer> rollingNumbers = new HashMap<>();
		Random random = new Random();
		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

		List<Long> delays = List.of(500L, 500L, 500L, 500L, 500L, 700L, 700L, 700L, 1000L, 1000L, 1000L, 1000L, 1300L,
			1700L, 2200L, 2200L); // 점점 느려지는 간격

		if (signUpMessage != null) {
			signUpMessage.delete().queue();
		}

		// 초기 메시지 전송
		EmbedBuilder eb = new EmbedBuilder()
			.setTitle("🎲 주사위 게임 진행 중...")
			.setColor(Color.ORANGE)
			.setDescription(buildRollingText(participants, rollingNumbers));

		event.replyEmbeds(eb.build())
			.setEphemeral(false)
			.queue(interactionHook -> {

				for (int i = 0; i < delays.size(); i++) {
					final int round = i;

					scheduler.schedule(() -> {
						for (Member member : participants) {
							int roll = random.nextInt(100) + 1;
							rollingNumbers.put(member, roll);

							if (round == delays.size() - 1) {
								finalResults.put(member, roll); // 최종 숫자
							}
						}

						EmbedBuilder updated = new EmbedBuilder()
							.setTitle("🎲 주사위 굴리는 중...")
							.setColor(Color.YELLOW)
							.setDescription(buildRollingText(participants, rollingNumbers, finalResults));

						interactionHook.editOriginalEmbeds(updated.build()).queue(
							success -> {
							},
							failure -> {
								failure.printStackTrace();
							}
						);

						// 최종 결과 메시지
						if (round == delays.size() - 1) {
							scheduler.schedule(() -> {
								EmbedBuilder resultEmbed = new EmbedBuilder()
									.setTitle("주사위 최종 결과")
									.setColor(Color.BLUE)
									.setDescription(buildFinalText(finalResults));
								interactionHook.editOriginalEmbeds(resultEmbed.build()).queue();
							}, 2, TimeUnit.SECONDS);
						}

					}, delays.get(i), TimeUnit.MILLISECONDS);
				}
			});
	}

	// 현재 진행 중 숫자 텍스트 생성
	private String buildRollingText(List<Member> participants, Map<Member, Integer> rollingNumbers) {
		return buildRollingText(participants, rollingNumbers, null);
	}

	// 숫자 변화 또는 최종 표시
	private String buildRollingText(List<Member> participants, Map<Member, Integer> rollingNumbers,
		Map<Member, Integer> finalResults) {
		StringBuilder sb = new StringBuilder();
		for (Member m : participants) {
			int num = rollingNumbers.getOrDefault(m, 0);
			sb.append(m.getEffectiveName())
				.append(" : 🎲 ")
				.append(num)
				.append("\n");
		}
		return sb.toString();
	}

	// 최종 결과 정렬 및 승자 표시
	private String buildFinalText(Map<Member, Integer> finalResults) {
		StringBuilder sb = new StringBuilder();
		Member winner = finalResults.entrySet().stream()
			.max(Map.Entry.comparingByValue())
			.map(Map.Entry::getKey)
			.orElse(null);

		for (Map.Entry<Member, Integer> entry : finalResults.entrySet()) {
			sb.append(entry.getKey().getEffectiveName())
				.append(" : ")
				.append(entry.getValue());
			if (entry.getKey().equals(winner)) {
				sb.append(" 🏆");
			}
			sb.append("\n");
		}
		return sb.toString();
	}
}
