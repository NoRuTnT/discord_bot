package com.discord_bot.backend.domain.music.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class MusicPageController {

	@GetMapping("/music/{guildId}")
	public String musicPage(@PathVariable String guildId) {
		return "forward:/music/index.html";
	}
}
