package hu.garaba.command;

import hu.garaba.BotContext;
import hu.garaba.Session;
import org.telegram.telegrambots.meta.api.objects.Message;

public class ClearCommand implements Command {
    @Override
    public void action(BotContext context, Session session, Message message) {
        session.clearConversation();
        context.messaging().sendMessage(message.getFrom().getId(), "Conversation cleared");
    }
}
