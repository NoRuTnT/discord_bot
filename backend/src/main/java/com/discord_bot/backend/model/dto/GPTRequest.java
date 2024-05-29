package com.discord_bot.backend.model.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GPTRequest {

	private String prompt;
	private int max_tokens;
	private double temperature;
	private double top_p;
	private int n;
	private List<String> stop;

}