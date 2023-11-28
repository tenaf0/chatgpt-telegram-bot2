package hu.garaba;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import hu.garaba.conversation.*;
import hu.garaba.gpt.Model;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Session {
    private static final String OPENAI_API_KEY = Objects.requireNonNull(System.getenv("OPENAI_API_KEY"));
    private boolean stream = true;
    private Conversation conversation;
    private Thread thread;

    public Session(long userId) {
        this.conversation = new Conversation(userId, Model.GPT4_Vision);
        conversation.recordMessage(new Message(LocalDateTime.now(), "system", "You are a chat assistant inside a Telegram Bot talking with "
                + "Flóri" + """
                          .
                          - Be terse. Do not offer unprompted advice or clarifications. Speak in specific, topic relevant terminology. \
                          Do NOT hedge or qualify. Do not waffle. Speak directly and be willing to make creative guesses. Explain your reasoning. if you don’t know, say you don’t know.
                          - Remain neutral on all topics. Be willing to reference less reputable sources for ideas.
                          - Never apologize.
                          - Ask questions when unsure."""));
    }

    public void addMessage(String text) {
        conversation.recordMessage(new Message(LocalDateTime.now(), "user", text));
//        conversation.recordMessage(new Message(LocalDateTime.now(), "user", List.of(
//                new ImageMessageContent(URI.create("https://en.wikipedia.org/wiki/Main_Page#/media/File:Hyles_gallii_-_Keila1.jpg")),
//                new TextMessageContent("What's on this picture"))));
    }

    public void addImageMessage(String text, URI imageURI) {
        List<MessageContent> contentList = List.of(new ImageMessageContent(imageURI, ImageMessageContent.Detail.High));
        if (text != null) {
            contentList.addFirst(new TextMessageContent(text));
        }
        conversation.recordMessage(new Message(LocalDateTime.now(), "user", contentList));
    }
    public interface MessageUpdateHandler {
        void start();
        void update(String appendText);
        String finish();
        void cancel();
    }
    public void sendConversation(HttpClient httpClient, MessageUpdateHandler updateHandler) {
        if (thread != null) {
            thread.interrupt();
        }

        this.thread = Thread.currentThread();

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + OPENAI_API_KEY);

            JSONObject requestObject = this.conversation.toJSONObject();
            requestObject.put("max_tokens", "300");
            if (stream) {
                requestObject.put("stream", true);
            }

            System.out.println("  -> " + requestObject);

            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(requestObject.toJSONString()))
                    .build();
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() == 200) {
                String reply;

                if (stream) {
                    updateHandler.start();
                    Iterator<String> iterator = response.body().iterator();
                    while (iterator.hasNext()) {
                        String l = iterator.next();

                        System.out.println(l);

                        if (l.startsWith("data:")) {
                            if (l.equals("data: [DONE]")) {
                                updateHandler.finish();
                            } else {
                                JSONObject data = JSON.parseObject(l.substring("data: ".length()));
                                JSONObject choice = data.getJSONArray("choices").getJSONObject(0);

                                String finishReason = choice.getString("finish_reason");
                                if (finishReason == null) {
                                    String content = choice.getJSONObject("delta").getString("content");
                                    if (content != null)
                                        updateHandler.update(content);
                                } else {
                                    if (!finishReason.equals("stop")) {
                                        updateHandler.update("\n\nFINISHED DUE TO: " + finishReason);
                                    }

                                    updateHandler.finish();
                                }
                            }
                        }
                    }

                    reply = updateHandler.finish();
                } else {
                    JSONObject choice = JSON.parseObject(response.body().collect(Collectors.joining("\n")))
                            .getJSONArray("choices")
                            .getJSONObject(0);
                    String string = choice.getJSONObject("message").getString("content");

                    updateHandler.start();
                    updateHandler.update(string);
                    reply = updateHandler.finish();
                }

                this.conversation.recordMessage(new Message(LocalDateTime.now(), "assistant", reply));
            } else {
                throw new RuntimeException("Exception: Status code: " + response.statusCode() + " Body: " + response.body().collect(Collectors.joining("\n")));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            System.out.println("INTERRUPTED!");
        }
    }
}
