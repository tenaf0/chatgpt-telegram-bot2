package command;

import hu.garaba.BotContext;
import hu.garaba.Messaging;
import hu.garaba.Session;
import hu.garaba.gpt.GPTException;
import hu.garaba.gpt.GPTUsage;
import hu.garaba.gpt.ImageGeneration;
import hu.garaba.gpt.Model;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.sql.SQLException;

public class GenerateImage implements Command {
    private static final System.Logger LOGGER = System.getLogger(SummarizeCommand.class.getCanonicalName());

    @Override
    public void action(BotContext context, Session session, Message message) {
        long userId = message.getFrom().getId();
        String text = message.getText();
        Messaging messaging = context.messaging();

        boolean hd = text.contains("#hd");
        String url;
        try {
            url = ImageGeneration.generateImage(context, text, 1, hd);
        } catch (GPTException e) {
            messaging.sendMessage(userId, e.userMessage());
            LOGGER.log(System.Logger.Level.DEBUG, e);
            throw new RuntimeException(e);
        }

        try {
            context.userDatabase().flushUsage(userId, GPTUsage.ImageGeneration, Model.DALL_E_3, 1);
        } catch (SQLException e) {
            LOGGER.log(System.Logger.Level.DEBUG, "Could not log user's image generation", userId);
        }

        SendPhoto sendPhoto = SendPhoto.builder()
                .chatId(userId)
                .photo(new InputFile(url))
                .build();
        /*try {
            execute(sendPhoto);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }*/
    }
}
