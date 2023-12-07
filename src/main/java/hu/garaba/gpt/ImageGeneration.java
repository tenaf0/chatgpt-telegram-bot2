package hu.garaba.gpt;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import hu.garaba.BotContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public class ImageGeneration {
    public static String generateImage(BotContext botContext, String prompt, int n, boolean hd) throws GPTException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/images/generations"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + botContext.credentials().OPENAI_API_KEY());

        try {
            Map<String, Object> parameterMap = new HashMap<>(Map.of(
                    "model", "dall-e-3",
                    "prompt", prompt,
                    "n", n,
                    "size", "1024x1024"
            ));
            if (hd) {
                parameterMap.put("quality", "hd");
            }
            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(new JSONObject(parameterMap).toJSONString()))
                    .build();
            System.out.println(" -> " + parameterMap);
            HttpResponse<String> response = botContext.httpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject jsonObject = JSON.parseObject(response.body());
                JSONObject data = jsonObject.getJSONArray("data").getJSONObject(0);

                if (data == null) {
                    System.out.println(response.body());
                }
                return data.getString("url");
            } else {
                String userErrorMessage = "The OpenAI API responded with an error";

                JSONObject jsonObject = JSON.parseObject(response.body());
                JSONObject error = jsonObject.getJSONObject("error");
                if (error != null) {
                    String code = error.getString("code");
                    if (code != null && code.equals("content_policy_violation")) {
                        userErrorMessage = "Content policy violation";
                    }
                }

                throw new GPTException("Status code: " + response.statusCode() + " body: " + response.body(), userErrorMessage);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException();
        }
    }
}
