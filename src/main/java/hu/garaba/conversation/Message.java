package hu.garaba.conversation;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record Message(LocalDateTime dateTime, String role, List<MessageContent> contentList) {
    public Message(LocalDateTime dateTime, String role, String text) {
        this(dateTime, role, List.of(new TextMessageContent(text)));
    }

    public JSONObject toJSONObject() {
        if (contentList.size() == 1 && contentList.get(0) instanceof TextMessageContent(String text)) {
            return new JSONObject(Map.of("role", role, "content", text));
        } else {
            JSONArray contentArray = new JSONArray(contentList.size());
            for (var content : contentList) {
                contentArray.add(content.toJSONObject());
            }

            return new JSONObject(Map.of("role", role, "content", contentArray));
        }
    }
}
