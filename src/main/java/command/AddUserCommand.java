package command;

import hu.garaba.BotContext;
import hu.garaba.Messaging;
import hu.garaba.Session;
import org.telegram.telegrambots.meta.api.objects.Message;

public class AddUserCommand implements Command {
    private static final System.Logger LOGGER = System.getLogger(AddUserCommand.class.getCanonicalName());

    @Override
    public void action(BotContext context, Session session, Message message) {
        long userId = message.getFrom().getId();
        String text = message.getText();
        Messaging messaging = context.messaging();

        if (!context.userDatabase().isAdmin(userId)) {
            LOGGER.log(System.Logger.Level.DEBUG, "User " + userId + " tried to execute /addUser!");
            throw new RuntimeException("This user is not an admin, /addUser command denied!");
        }

        try {
            String[] words = text.split("\\s+");

            long toBeAddedUserId = Long.parseLong(words[1]);
            String name = words[2];

            context.userDatabase().addUser(toBeAddedUserId, name);
            messaging.sendMessage(userId, "Successfully added user!");
        } catch (Exception e) {
            messaging.sendMessage(userId, "Failed to add user: " + e);
        }
    }
}
