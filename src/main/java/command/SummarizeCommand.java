package command;

import hu.garaba.*;
import hu.garaba.tools.Summarizer;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.net.URI;

public class SummarizeCommand implements Command {
    private static final System.Logger LOGGER = System.getLogger(SummarizeCommand.class.getCanonicalName());

    @Override
    public void action(BotContext context, Session session, Message message) {
        long userId = message.getFrom().getId();
        String text = message.getText();
        Messaging messaging = context.messaging();

        String[] words = text.split("\\s+");
        if (words.length < 2 || !Summarizer.isValidURL(words[1])) {
            messaging.sendMessage(userId, "The command is ill-formed, or you provided an illegal URL. The correct syntax: /summarize <url>");
        } else {
            URI uri = URI.create(words[1]);
            String textToSummarize;
            try {
                if (uri.getHost() != null && uri.getHost().replace(".", "").contains("youtube")) {
                    messaging.sendMessage(userId, "Summarizing video transcript at " + uri + ":");
                    textToSummarize = Summarizer.extractVideoTranscript(uri);
                } else {
                    messaging.sendMessage(userId, "Summarizing article at " + uri + ":");
                    textToSummarize = Summarizer.extractArticle(uri);
                }

                Summarizer.summarizeText(session, textToSummarize);
                session.sendConversation(new MessageUpdateHandler(userId, context));
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.DEBUG, "/summarize command failed", e);
                messaging.sendMessage(userId, "/summarize command failed");
            }
        }

    }
}
