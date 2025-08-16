package hu.garaba.conversation;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import hu.garaba.command.ModelCommand;
import hu.garaba.gpt.Model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class Conversation {
    private final long userId;
    private final ModelCommand.ModelConfiguration modelConfiguration;
    private final String instructions;
    private final List<Message> messages = new CopyOnWriteArrayList<>();
    public Conversation(long userId, ModelCommand.ModelConfiguration modelConfiguration, String instructions) {
        this.userId = userId;
        this.modelConfiguration = modelConfiguration;
        this.instructions = instructions;
    }

    public Conversation(long userId, ModelCommand.ModelConfiguration modelConfiguration, String instructions, Conversation conversation) {
        this.userId = userId;
        this.modelConfiguration = modelConfiguration;
        this.instructions = instructions;

        if (conversation != null)
            this.messages.addAll(conversation.messages);
    }

    public Model model() {
        return this.modelConfiguration.model();
    }

    public void recordMessage(Message message) {
        messages.add(message);
    }

    public JSONObject toJSONObject() {
        JSONObject jsonObject = new JSONObject(new HashMap<>(Map.of(
                "model", modelConfiguration.model().modelName,
                "instructions", instructions,
                "input", new JSONArray(messages.stream().map(Message::toJSONObject).toList())
        )));
        jsonObject.putAll(modelConfiguration.extraParams());
        return jsonObject;
    }
}
