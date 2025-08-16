package hu.garaba;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import hu.garaba.command.ModelCommand;
import hu.garaba.conversation.*;
import hu.garaba.gpt.GPTUsage;
import hu.garaba.gpt.Model;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Session {
    private static final System.Logger LOGGER = System.getLogger(Session.class.getCanonicalName());

    private final BotContext botContext;
    private final long userId;
    private boolean stream = true;
    private volatile Conversation conversation;
    private final ConversationStatistic conversationStatistic = new ConversationStatistic();
    private final AtomicReference<Thread> thread = new AtomicReference<>();
    private boolean isCleared = false;
    private LocalTime lastInteractedAt = LocalTime.now();

    public Session(BotContext botContext, long userId) {
        this.botContext = botContext;
        this.userId = userId;
    }

    private static final String SYSTEM_PROMPT = "You are a chat assistant inside a Telegram Bot." + """
                .
                - Be terse. Do not offer unprompted advice or clarifications. Speak in specific, topic relevant terminology. \
                Do NOT hedge or qualify. Do not waffle. Speak directly and be willing to make creative guesses. Explain your reasoning. if you don’t know, say you don’t know.
                - Remain neutral on all topics. Be willing to reference less reputable sources for ideas.
                - Never apologize.
                - Ask questions when unsure.""";


    public void initConversation() {
        initConversation(ModelCommand.BASIC_MODEL, SYSTEM_PROMPT);
    }

    public synchronized void initConversation(ModelCommand.ModelConfiguration modelConfiguration, String prompt) {
        clearConversation();
        isCleared = false;

        this.conversation = new Conversation(userId, modelConfiguration, prompt);
    }

    public Model getModel() {
        if (this.conversation != null) {
            return this.conversation.model();
        } else {
            return null;
        }
    }

    public synchronized void changeModelOfConversation(ModelCommand.ModelConfiguration newModelConfiguration) {
        Conversation prevConversation = this.conversation;
        clearConversation();
        isCleared = false;

        this.conversation = new Conversation(userId, newModelConfiguration, SYSTEM_PROMPT, prevConversation);
    }

    public synchronized boolean addMessage(String text) {
        updateLastInteractionTime();

        boolean newConversation = false;
        if (isConversationCleared()) {
            newConversation = true;
            initConversation();
        }

        conversation.recordMessage(Message.createMessage(LocalDateTime.now(), "user", text));
        return newConversation;
    }

    public synchronized boolean addImageMessage(String text, URI imageURI, long cost) {
        updateLastInteractionTime();

        boolean newConversation = false;
        if (isConversationCleared()) {
            newConversation = true;
            initConversation();
        }

        if (!conversation.model().supportsImageInput) {
            changeModelOfConversation(ModelCommand.BASIC_MODEL);
        }

        List<MessageContent> contentList = new ArrayList<>();
        contentList.add(new ImageMessageContent(imageURI, cost, ImageMessageContent.Detail.Low));
        if (text != null) {
            contentList.addFirst(new TextMessageContent(text));
        }
        conversation.recordMessage(new Message(LocalDateTime.now(), "user", contentList));

        return newConversation;
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
        updateLastInteractionTime();

        Thread t = thread.get();
        if (t != null) {
            t.interrupt();
        }

        thread.set(Thread.currentThread());

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/responses"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + botContext.credentials().OPENAI_API_KEY());

            JSONObject requestObject = this.conversation.toJSONObject();
            requestObject.put("max_output_tokens", 3000);
            if (stream) {
                requestObject.put("stream", true);
            }

            LOGGER.log(System.Logger.Level.TRACE, "  -> " + requestObject);
            LOGGER.log(System.Logger.Level.DEBUG, "Sending " + conversation.model() + " request to OpenAI");

            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(requestObject.toJSONString()))
                    .build();
            HttpResponse<Stream<String>> response = botContext.httpClient().send(request, HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() == 200) {
                String reply;
                int inputToken = -1;
                int outputToken = -1;

                if (stream) {
                    updateHandler.start();
                    Iterator<String> iterator = response.body().iterator();
                    while (iterator.hasNext()) {
                        String l = iterator.next();

                        LOGGER.log(System.Logger.Level.DEBUG, "Received " + l);

                        if (l.startsWith("data: ")) {
                            JSONObject data = JSON.parseObject(l.substring("data: ".length()));

                            if ("response.output_text.delta".equals(data.getString("type"))) {
                                updateHandler.update(data.getString("delta"));
                            } else if ("response.completed".equals(data.getString("type"))) {
                                updateHandler.finish();
                                JSONObject usage = data.getJSONObject("response").getJSONObject("usage");
                                inputToken = usage.getInteger("input_tokens");
                                outputToken = usage.getInteger("output_tokens");
                            }
                            // TODO: Error handling
                        }
                    }
                    reply = updateHandler.finish();
                } else {
                    String string = "";
                    String body = response.body().collect(Collectors.joining("\n"));
                    LOGGER.log(System.Logger.Level.DEBUG, "Received: " + body);

                    JSONObject bodyObject = JSON.parseObject(body);
                    JSONArray output = bodyObject
                            .getJSONArray("output");
                    if (output != null && !output.isEmpty()) {
                        JSONObject firstOutput = IntStream.range(0, output.size())
                                .mapToObj(output::getJSONObject)
                                .filter(o -> "message".equals(o.getString("type")))
                                .findFirst().get();

                        JSONArray content = firstOutput.getJSONArray("content");
                        if (content != null && !content.isEmpty()) {
                            JSONObject firstContent = content.getJSONObject(0);
                            string = firstContent.getString("text");
                        }
                    }

                    JSONObject usage = bodyObject.getJSONObject("usage");
                    inputToken = usage.getInteger("input_tokens");
                    outputToken = usage.getInteger("output_tokens");

                    updateHandler.start();
                    updateHandler.update(string);

                    reply = updateHandler.finish();
                }

                if (inputToken == -1 || outputToken == -1) {
                    LOGGER.log(System.Logger.Level.WARNING, "Usage statistics were not set!");
                } else {
                    conversationStatistic.add(inputToken, outputToken);
                }

                if (!Thread.currentThread().isInterrupted()) {
                    this.conversation.recordMessage(Message.createMessage(LocalDateTime.now(), "assistant", reply));
                }
            } else {
                throw new RuntimeException("Exception: Status code: " + response.statusCode() + " Body: " + response.body().collect(Collectors.joining("\n"))
                        + "\nSent request was: " + requestObject);
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.DEBUG, "Exception at sending Conversation", e);

            if (Thread.interrupted()) {
                LOGGER.log(System.Logger.Level.DEBUG, "Interrupted");
                updateHandler.cancel();
            }
        }
    }

    public synchronized void clearConversation() {
        // TODO: Make it thread interrupt-safe
        updateLastInteractionTime();

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

        isCleared = true;
    }

    public boolean shouldClear(LocalTime cutoffTime) {
        return !isConversationCleared() && lastInteractedAt.isBefore(cutoffTime);
    }

    public void resetClear() {
        isCleared = false;
    }

    private boolean isConversationCleared() {
        return isCleared || conversation == null;
    }

    private void updateLastInteractionTime() {
        this.lastInteractedAt = LocalTime.now();
    }
}
