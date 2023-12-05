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
        Map<String, Object> imageUrlMap = new HashMap<>(Map.of("url", uri.toString()));
        if (detail != null) {
            imageUrlMap.put("detail", detail.name().toLowerCase());
        }

        return new JSONObject(Map.of(
                "type", "image_url",
                "image_url", new JSONObject(imageUrlMap)
        ));
    }
}
