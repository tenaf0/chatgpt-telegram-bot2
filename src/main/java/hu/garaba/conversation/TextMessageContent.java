package hu.garaba.conversation;

import com.alibaba.fastjson.JSONObject;
import hu.garaba.gpt.TokenCalculator;

import java.util.HashMap;
import java.util.Map;

public record TextMessageContent(String text) implements MessageContent {

    @Override
    public long tokenCount() {
        return TokenCalculator.tokenCount(text);
    }

    @Override
    public JSONObject toJSONObject() {
        return new JSONObject(new HashMap<>(Map.of(
                "type", "text",
                "text", text
        )));
    }
}
