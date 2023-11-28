package hu.garaba.conversation;

import com.alibaba.fastjson.JSONObject;

public sealed interface MessageContent permits TextMessageContent, ImageMessageContent {
    JSONObject toJSONObject();
}
