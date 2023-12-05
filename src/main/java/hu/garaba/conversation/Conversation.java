package hu.garaba.conversation;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import hu.garaba.gpt.Model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class Conversation {
    private final long userId;
    private final Model model;
    private final List<Message> messages = new CopyOnWriteArrayList<>();

    public Conversation(long userId, Model model) {
        this.userId = userId;
        this.model = model;
    }

    public Conversation(long userId, Model model, Conversation conversation) {
        this.userId = userId;
        this.model = model;
        this.messages.addAll(conversation.messages);
    }

    public Model model() {
        return this.model;
    }

    public long tokenCount() {
        return messages.stream().mapToLong(Message::tokenCount).sum();
    }

    public void recordMessage(Message message) {
        messages.add(message);
    }

    public JSONObject toJSONObject() {
        return new JSONObject(new HashMap<>(Map.of(
                "model", model.modelName,
                "messages", new JSONArray(messages.stream().map(Message::toJSONObject).toList())
        )));
    }
}
