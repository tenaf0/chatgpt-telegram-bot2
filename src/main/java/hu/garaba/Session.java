package hu.garaba;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import hu.garaba.conversation.*;
import hu.garaba.gpt.GPTUsage;
import hu.garaba.gpt.Model;
import hu.garaba.gpt.TokenCalculator;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Session {
    private static final System.Logger LOGGER = System.getLogger(Session.class.getCanonicalName());

    private final BotContext botContext;
    private final long userId;
    private boolean stream = true;
    private volatile Conversation conversation;
    private final ConversationStatistic conversationStatistic = new ConversationStatistic();
    private final AtomicReference<Thread> thread = new AtomicReference<>();

    public Session(BotContext botContext, long userId) {
        this.botContext = botContext;
        this.userId = userId;
    }

    public void initConversation() {
        initConversation(Model.GPT4, "You are a chat assistant inside a Telegram Bot." + """
                          .
                          - Be terse. Do not offer unprompted advice or clarifications. Speak in specific, topic relevant terminology. \
                          Do NOT hedge or qualify. Do not waffle. Speak directly and be willing to make creative guesses. Explain your reasoning. if you don’t know, say you don’t know.
                          - Remain neutral on all topics. Be willing to reference less reputable sources for ideas.
                          - Never apologize.
                          - Ask questions when unsure.""");
    }

    public synchronized void initConversation(Model model, String prompt) {
        clearConversation();

        this.conversation = new Conversation(userId, model);
        conversation.recordMessage(new Message(LocalDateTime.now(), "system", prompt));
    }

    public synchronized void changeModelOfConversation(Model newModel) {
        Conversation prevConversation = this.conversation;
        clearConversation();

        this.conversation = new Conversation(userId, newModel, prevConversation);
    }

    public synchronized void addMessage(String text) {
        if (conversation == null) {
            initConversation();
        }

        conversation.recordMessage(new Message(LocalDateTime.now(), "user", text));
    }

    public synchronized void addImageMessage(String text, URI imageURI, long cost) {
        if (conversation == null) {
            initConversation();
        }

        if (conversation.model() != Model.GPT4_Vision) {
            changeModelOfConversation(Model.GPT4_Vision);
        }

        List<MessageContent> contentList = new ArrayList<>();
        contentList.add(new ImageMessageContent(imageURI, cost, ImageMessageContent.Detail.Low));
        if (text != null) {
            contentList.addFirst(new TextMessageContent(text));
        }
        conversation.recordMessage(new Message(LocalDateTime.now(), "user", contentList));
    }
    public interface MessageUpdateHandler {
        void start();
        void update(String appendText);

        /**
         * Should be idempotent, and be callable after both normal and interrupted execution flow
         * @return The up-to-date text buffer
         */
        String finish();
        void cancel();
    }
    public void sendConversation(MessageUpdateHandler updateHandler) {
        Thread t = thread.get();
        if (t != null) {
            t.interrupt();
        }

        thread.set(Thread.currentThread());

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + botContext.credentials().OPENAI_API_KEY());

            JSONObject requestObject = this.conversation.toJSONObject();
            requestObject.put("max_tokens", 2000);
            if (stream) {
                requestObject.put("stream", true);
            }

            LOGGER.log(System.Logger.Level.TRACE, "  -> " + requestObject);
            LOGGER.log(System.Logger.Level.DEBUG, "Sending " + conversation.model() + " request to OpenAI");

            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(requestObject.toJSONString()))
                    .build();
            HttpResponse<Stream<String>> response = botContext.httpClient().send(request, HttpResponse.BodyHandlers.ofLines());
            conversationStatistic.add(conversation.tokenCount(), 0);

            if (response.statusCode() == 200) {
                String reply;

                if (stream) {
                    updateHandler.start();
                    Iterator<String> iterator = response.body().iterator();
                    while (iterator.hasNext()) {
                        String l = iterator.next();

                        if (l.startsWith("data:")) {
                            if (l.equals("data: [DONE]")) {
                                updateHandler.finish();
                            } else {
                                JSONObject data = JSON.parseObject(l.substring("data: ".length()));
                                JSONObject choice = data.getJSONArray("choices").getJSONObject(0);

                                String finishReason = choice.getString("finish_reason"); // data: {"id":"chatcmpl-8Q0ZtHDviS1hw5WbZC73qurGUluRo","object":"chat.completion.chunk","created":1701209441,"model":"gpt-4-1106-vision-preview","choices":[{"delta":{},"index":0,"finish_details":{"type":"max_tokens"}}]}
                                // TODO: finish detail?
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

                if (!Thread.currentThread().isInterrupted()) {
                    this.conversation.recordMessage(new Message(LocalDateTime.now(), "assistant", reply));
                }
            } else {
                throw new RuntimeException("Exception: Status code: " + response.statusCode() + " Body: " + response.body().collect(Collectors.joining("\n")));
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.DEBUG, "Exception at sending Conversation", e);

            if (Thread.interrupted()) {
                LOGGER.log(System.Logger.Level.DEBUG, "Interrupted");
                updateHandler.cancel();
            }
        } finally {
            String finish = updateHandler.finish();
            conversationStatistic.add(0, TokenCalculator.tokenCount(finish));
        }
    }

    public synchronized void clearConversation() {
        ConversationStatistic.CountPair countPair = conversationStatistic.getAndClear();

        if (this.conversation != null) {
            try {
                botContext.userDatabase().flushUsage(userId, GPTUsage.ConversationInput, conversation.model(), countPair.input());
                botContext.userDatabase().flushUsage(userId, GPTUsage.ConversationOutput, conversation.model(), countPair.output());
            } catch (SQLException e) {
                LOGGER.log(System.Logger.Level.WARNING, "Could not write usage for user " + userId
                        + ". " + countPair, e);
            }
        }

        this.conversation = null;
    }
}
