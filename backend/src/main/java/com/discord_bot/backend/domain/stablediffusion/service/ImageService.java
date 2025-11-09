package com.discord_bot.backend.domain.stablediffusion.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Service
public class ImageService {

	@Value("${fastapi.url}")
	private String FASTAPI_URL;

	private final OkHttpClient client = new OkHttpClient.Builder()
		.connectTimeout(60, TimeUnit.SECONDS)
		.readTimeout(60, TimeUnit.SECONDS)
		.writeTimeout(60, TimeUnit.SECONDS)
		.build();

	public File generateImage(String prompt) {
		String url = FASTAPI_URL + "/txt_generate?prompt=" + prompt.replace(" ", "%20"); // URL 인코딩

		try {
			Request request = new Request.Builder().url(url).build();
			Response response = client.newCall(request).execute();

			if (response.isSuccessful()) {
				ResponseBody body = response.body();
				if (body != null) {
					InputStream inputStream = body.byteStream();
					File tempFile = File.createTempFile("generated", ".png");

					FileOutputStream outputStream = new FileOutputStream(tempFile);
					byte[] buffer = new byte[1024];
					int bytesRead;
					while ((bytesRead = inputStream.read(buffer)) != -1) {
						outputStream.write(buffer, 0, bytesRead);
					}
					outputStream.close();

					return tempFile;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public File[] generateMultipleImages(String prompt) {
		File[] images = new File[4];

		try {
			Request request = new Request.Builder()
				.url(FASTAPI_URL + "/txt_generate?prompt=" + prompt.replace(" ", "%20"))
				.build();
			Response response = client.newCall(request).execute();

			if (response.isSuccessful() && response.body() != null) {

				String responseBody = response.body().string();
				JSONObject json = new JSONObject(responseBody);
				JSONArray imageArray = json.getJSONArray("images");

				for (int i = 0; i < 4; i++) {
					String base64Image = imageArray.getString(i);
					images[i] = decodeBase64ToFile(base64Image, "generated_" + i + ".png");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return images;
	}

	public File img2img(File inputImage) {
		try {
			MultipartBody requestBody = new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("file", inputImage.getName(),
					RequestBody.create(inputImage, MediaType.parse("image/png")))
				.build();

			Request request = new Request.Builder().url(FASTAPI_URL + "/img_generate").post(requestBody).build();
			Response response = client.newCall(request).execute();

			if (response.isSuccessful() && response.body() != null) {
				File tempFile = File.createTempFile("img2img", ".png");
				FileOutputStream outputStream = new FileOutputStream(tempFile);
				outputStream.write(response.body().bytes());
				outputStream.close();
				return tempFile;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public File[] img2imgMultiple(File inputImage) {
		File[] images = new File[4];

		try {
			MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("file", inputImage.getName(),
					RequestBody.create(MediaType.parse("image/png"), inputImage)
				);

			Request request = new Request.Builder()
				.url(FASTAPI_URL + "/img_generate")
				.post(bodyBuilder.build())
				.build();

			Response response = client.newCall(request).execute();

			if (response.isSuccessful() && response.body() != null) {
				String responseBody = response.body().string();
				JSONObject json = new JSONObject(responseBody);
				JSONArray imageArray = json.getJSONArray("images");

				for (int i = 0; i < 4; i++) {
					String base64Image = imageArray.getString(i);
					images[i] = decodeBase64ToFile(base64Image, "img2img_" + i + ".png");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return images;
	}

	public static File decodeBase64ToFile(String base64, String filename) throws IOException {
		byte[] decodedBytes = Base64.getDecoder().decode(base64);
		File file = new File(filename);
		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.write(decodedBytes);
		}

		return file;
	}
}
