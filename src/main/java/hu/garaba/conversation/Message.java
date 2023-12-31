package hu.garaba.conversation;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record Message(LocalDateTime dateTime, String role, List<MessageContent> contentList) {
    public static Message createMessage(LocalDateTime dateTime, String role, String text) {
        if (text == null)
            throw new IllegalArgumentException("`text` can't be null");

        return new Message(dateTime, role, List.of(new TextMessageContent(text)));
    }

    public long tokenCount() {
        return contentList.stream().mapToLong(MessageContent::tokenCount).sum();
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
