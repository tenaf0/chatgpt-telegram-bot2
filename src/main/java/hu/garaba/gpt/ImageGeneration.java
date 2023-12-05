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
    public static String generateImage(BotContext botContext, String prompt, int n, boolean hd) {
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
                throw new RuntimeException("Status code: " + response.statusCode() + " body: " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException();
        }
    }
}
