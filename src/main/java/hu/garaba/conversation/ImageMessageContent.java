package hu.garaba.conversation;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Nullable;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public record ImageMessageContent(URI uri, long cost, @Nullable Detail detail) implements MessageContent {
    public enum Detail {
        Low,
        High
    }

    @Override
    public long tokenCount() {
        return cost;
    }

    @Override
    public JSONObject toJSONObject() {
        Map<String, Object> map = new HashMap<>(Map.of(
                "type", "input_image",
                "image_url", uri.toString()
        ));
        if (detail != null) {
            map.put("detail", detail.name().toLowerCase());
        }
        return new JSONObject(map);
    }
}
