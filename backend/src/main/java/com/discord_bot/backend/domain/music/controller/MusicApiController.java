package com.discord_bot.backend.domain.music.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.discord_bot.backend.domain.music.dto.AddTrackRequest;
import com.discord_bot.backend.domain.music.dto.MoveTrackRequest;
import com.discord_bot.backend.domain.music.dto.MusicQueueResponse;
import com.discord_bot.backend.domain.music.service.AudioService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/music")
@RequiredArgsConstructor
public class MusicApiController {

	private final AudioService audioService;

	@GetMapping("/{guildId}")
	public MusicQueueResponse getQueue(@PathVariable long guildId) {
		return audioService.getQueueState(guildId);
	}

	@PostMapping("/{guildId}/tracks")
	public CompletableFuture<MusicQueueResponse> addTrack(@PathVariable long guildId,
		@RequestBody AddTrackRequest request) {
		return audioService.addTrack(guildId, request.getInput());
	}

	@PostMapping("/{guildId}/queue/move")
	public MusicQueueResponse moveTrack(@PathVariable long guildId, @RequestBody MoveTrackRequest request) {
		return audioService.moveQueuedTrack(guildId, request.getFromIndex(), request.getToIndex());
	}
}
