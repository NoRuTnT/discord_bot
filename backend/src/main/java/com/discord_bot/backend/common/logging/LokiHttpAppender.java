package com.discord_bot.backend.common.logging;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LokiHttpAppender extends AppenderBase<ILoggingEvent> {

	private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private String url;
	private String job = "lalabot";
	private String app = "lalabot";
	private String env = "prod";
	private String host = "unknown";

	private OkHttpClient client;
	private ExecutorService executorService;

	public void setUrl(String url) {
		this.url = url;
	}

	public void setJob(String job) {
		this.job = job;
	}

	public void setApp(String app) {
		this.app = app;
	}

	public void setEnv(String env) {
		this.env = env;
	}

	public void setHost(String host) {
		this.host = host;
	}

	@Override
	public void start() {
		client = new OkHttpClient.Builder()
			.connectTimeout(3, TimeUnit.SECONDS)
			.readTimeout(10, TimeUnit.SECONDS)
			.build();
		executorService = Executors.newSingleThreadExecutor(r -> {
			Thread thread = new Thread(r, "loki-log-sender");
			thread.setDaemon(true);
			return thread;
		});
		super.start();
	}

	@Override
	protected void append(ILoggingEvent eventObject) {
		if (url == null || url.isBlank()) {
			return;
		}

		String endpoint = normalizeUrl(url);
		String line = formatLine(eventObject);
		String timestamp = String.valueOf(eventObject.getTimeStamp() * 1_000_000L);

		executorService.submit(() -> {
			try {
				String payload = buildPayload(timestamp, line);
				Request request = new Request.Builder()
					.url(endpoint)
					.post(RequestBody.create(payload, JSON_MEDIA_TYPE))
					.build();

				try (Response response = client.newCall(request).execute()) {
					if (!response.isSuccessful()) {
						addWarn("Failed to push log to Loki. status=" + response.code());
					}
				}
			} catch (IOException e) {
				addWarn("Failed to push log to Loki: " + e.getMessage());
			}
		});
	}

	@Override
	public void stop() {
		if (executorService != null) {
			executorService.shutdown();
			try {
				executorService.awaitTermination(3, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		super.stop();
	}

	private String normalizeUrl(String configuredUrl) {
		if (configuredUrl.endsWith("/loki/api/v1/push")) {
			return configuredUrl;
		}
		if (configuredUrl.endsWith("/")) {
			return configuredUrl + "loki/api/v1/push";
		}
		return configuredUrl + "/loki/api/v1/push";
	}

	private String formatLine(ILoggingEvent eventObject) {
		return String.format(
			"%s [%s] %s - %s",
			eventObject.getLevel(),
			eventObject.getThreadName(),
			eventObject.getLoggerName(),
			eventObject.getFormattedMessage()
		);
	}

	private String buildPayload(String timestamp, String line) throws JsonProcessingException {
		Map<String, Object> payload = Map.of(
			"streams", List.of(
				Map.of(
					"stream", Map.of(
						"job", job,
						"app", app,
						"env", env,
						"host", host
					),
					"values", List.of(List.of(timestamp, line))
				)
			)
		);
		return OBJECT_MAPPER.writeValueAsString(payload);
	}
}
