package command;

import hu.garaba.BotContext;
import hu.garaba.Messaging;
import hu.garaba.Session;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.sql.SQLException;
import java.util.Objects;

public class UsageCommand implements Command {
    private static final System.Logger LOGGER = System.getLogger(UsageCommand.class.getCanonicalName());

    @Override
    public void action(BotContext context, Session session, Message message) {
        long userId = message.getFrom().getId();
        Messaging messaging = context.messaging();

        String usage = null;
        try {
            usage = context.userDatabase().queryUnpaidUsage(userId);
        } catch (SQLException e) {
            RuntimeException exception = new RuntimeException("Error happened during querying usage data for user " + userId, e);
            LOGGER.log(System.Logger.Level.DEBUG, exception);
            throw exception;
        } finally {
            messaging.sendMessage(userId, Objects.requireNonNullElse(usage, "Error happened during the querying of your usage"));
        }
    }
}
