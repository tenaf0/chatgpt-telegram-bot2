package command;

import hu.garaba.BotContext;
import hu.garaba.Messaging;
import hu.garaba.Session;
import hu.garaba.gpt.Model;
import org.telegram.telegrambots.meta.api.objects.Message;

public class ModelChangeCommand implements Command {
    @Override
    public void action(BotContext context, Session session, Message message) {
        String text = message.getText();
        Messaging messaging = context.messaging();

        String arg = text.substring("/modelChange ".length()).trim().toLowerCase();
        Model model = switch (arg) {
            case "gpt4" -> Model.GPT4;
            case "gpt3" -> Model.GPT3_TURBO;
            default -> null;
        };

        if (model == null) {
            messaging.sendMessage(message.getFrom().getId(), "The specified model is unknown");
        } else {
            session.changeModelOfConversation(model);
            messaging.sendMessage(message.getFrom().getId(), "Now using model: " + model);
        }
    }
}
