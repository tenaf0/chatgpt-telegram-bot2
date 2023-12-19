package hu.garaba.gpt;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.mizosoft.methanol.MediaType;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MultipartBodyPublisher;
import hu.garaba.BotContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Whisper {
    private static final System.Logger LOGGER = System.getLogger(Whisper.class.getCanonicalName());

    public static String transcribeVoice(BotContext botContext, Path voiceFile) {
        LOGGER.log(System.Logger.Level.DEBUG, "Sending voice transcription request");

        try (Methanol client = Methanol.newBuilder().executor(botContext.executor()).build()) {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/audio/transcriptions"))
                    .header("Content-Type", "multipart/form-data")
                    .header("Authorization", "Bearer " + botContext.credentials().OPENAI_API_KEY());

            var multipartBody = MultipartBodyPublisher.newBuilder()
                    .textPart("model", Model.WHISPER_1.modelName)
                    .filePart("file", voiceFile, MediaType.APPLICATION_OCTET_STREAM)
                    .build();

            HttpRequest request = requestBuilder.POST(multipartBody).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                LOGGER.log(System.Logger.Level.INFO, response.body());

                JSONObject jsonObject = JSON.parseObject(response.body());
                return jsonObject.getString("text");
            } else {
                String errorMessage = "Exception during voice transcription. Status code: " + response.statusCode() + " body: " + response.body();
                LOGGER.log(System.Logger.Level.ERROR,
                        errorMessage);
                throw new RuntimeException(errorMessage);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Voice transcription failed", e);
            throw new RuntimeException(e);
        }
    }

    public static Path speakText(BotContext botContext, String text) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/audio/speech"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + botContext.credentials().OPENAI_API_KEY());

            Map<String, Object> parameterMap = new HashMap<>(Map.of(
                    "model", "tts-1",
                    "input", text,
                    "voice", "echo"
            ));

            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(new JSONObject(parameterMap).toJSONString()))
                    .build();

            HttpResponse<Path> response = botContext.httpClient().send(request, HttpResponse.BodyHandlers.ofFile(Files.createTempFile("generated-voice", ".mp3")));
            if (response.statusCode() == 200) {
                return response.body();
            } else {
                throw new RuntimeException("Error during voice generation request. Status code: " + response.statusCode() + " " + response);
            }
        } catch (IOException | InterruptedException e) {
            String message = "Failed generating voice";
            LOGGER.log(System.Logger.Level.ERROR, message, e);
            throw new RuntimeException(message, e);
        }
    }
}
