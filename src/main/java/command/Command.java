package command;

import hu.garaba.BotContext;
import hu.garaba.Session;
import org.telegram.telegrambots.meta.api.objects.Message;

public interface Command {
    void action(BotContext context, Session session, Message message);
}
