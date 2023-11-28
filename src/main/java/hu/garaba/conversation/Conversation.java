package hu.garaba.conversation;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import hu.garaba.gpt.Model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Conversation {
    private final long userId;
    private final Model model;
    private final List<Message> messages = new ArrayList<>();

    public Conversation(long userId, Model model) {
        this.userId = userId;
        this.model = model;
    }

    public synchronized void recordMessage(Message message) {
        messages.add(message);
    }

    public JSONObject toJSONObject() {
        return new JSONObject(new HashMap<>(Map.of(
                "model", model.modelName,
                "messages", new JSONArray(messages.stream().map(Message::toJSONObject).toList())
        )));
    }
}
