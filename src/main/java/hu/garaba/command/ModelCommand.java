package hu.garaba.command;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import hu.garaba.BotContext;
import hu.garaba.Messaging;
import hu.garaba.Session;
import hu.garaba.gpt.Model;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.Map;

public class ModelCommand implements Command {
    public static final ModelConfiguration SMART_MODEL = new ModelConfiguration(Model.GPT_5, Map.of(
            "tools", JSONArray.parse("""
                    [{ type: "web_search_preview", user_location: {type: "approximate", region: "EU"}}]
                    """)
    ));
    public static final ModelConfiguration BASIC_MODEL = new ModelConfiguration(Model.GPT_5_MINI, Map.of(
            "reasoning", JSONObject.parse("{\"effort\": \"minimal\"}")
    ));

    public record ModelConfiguration(Model model, Map<String, Object> extraParams) {
        public ModelConfiguration(Model model) {
            this(model, Map.of());
        }
    }

    @Override
    public void action(BotContext context, Session session, Message message) {
        String text = message.getText();
        Messaging messaging = context.messaging();

        String mode;
        ModelConfiguration model;
        if (text.startsWith("/smart")) {
            mode = "smart";
            model =  SMART_MODEL;
        } else if (text.startsWith("/basic")) {
            mode = "basic";
            model = BASIC_MODEL;
        } else {
            throw new IllegalStateException("Unknown mode: " + text);
        }

        session.changeModelOfConversation(model);
        messaging.sendMessage(message.getFrom().getId(), "Now in " + mode + " mode");
    }
}
