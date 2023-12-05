package hu.garaba.conversation;

import com.alibaba.fastjson.JSONObject;

public sealed interface MessageContent permits TextMessageContent, ImageMessageContent {
    long tokenCount();
    JSONObject toJSONObject();
}
