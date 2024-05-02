package hu.garaba;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


public class Bot extends TelegramLongPollingBot {
    private static final System.Logger LOGGER = System.getLogger(Bot.class.getCanonicalName());

    private final BotContext botContext;
    private final BotCommunicationHandler communicationHandler;


    public Bot(BotContext botContext) {
        super(botContext.credentials().TELEGRAM_BOT_TOKEN());

        if (botContext.messaging() != null) {
            this.botContext = botContext;
        } else {
            this.botContext = new BotContext(botContext.credentials(), botContext.executor(), botContext.userDatabase(), botContext.httpClient(), new TelegramMessaging());
        }

        this.communicationHandler = new BotCommunicationHandler(this.botContext, new SessionManager() {
            private final Map<Long, Session> sessionMap = new ConcurrentHashMap<>();

            @Override
            public Set<Map.Entry<Long, Session>> sessions() {
                return sessionMap.entrySet();
            }

            @Override
            public Session get(long id) {
                return sessionMap.get(id);
            }

            @Override
            public Session getOrCreate(long id, Function<Long, Session> f) {
                return sessionMap.computeIfAbsent(id, f);
            }
        });
    }

    @Override
    public String getBotUsername() {
        return "test_bot";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.getMessage() == null) {
            return;
        }

        botContext.executor().execute(() -> communicationHandler.handleUpdate(update));
    }

    /*private void handleUpdate(Update update) {
        Message message = update.getMessage();
        assert message != null;

        User user = message.getFrom();
        if (!botContext.userDatabase().isWhitelisted(user.getId())) {
            System.err.println("Access denied to user " + user.getId());
            botContext.messaging().sendMessage(user.getId(), "You are not authorized to access this bot. Please request access from the admin of this bot, mentioning your user id: " + user.getId());
            return;
        }

        Session session = sessionMap.computeIfAbsent(user.getId(), id -> new Session(botContext, id));

        if (message.getText() != null && message.getText().startsWith("/")) {
            handleCommand(session, message);
        } else {
            handleMessage(session, message);
        }
    }*/

    private class TelegramMessaging implements Messaging {

        @Override
        public int sendMessage(long userId, String message) {
            try {
                SendMessage sendMessage = SendMessage.builder()
                        .chatId(userId)
                        .text(message)
                        .build();
                Message sentMessage = execute(sendMessage);

                return sentMessage.getMessageId();
            } catch (TelegramApiException e) {
                RuntimeException exception = new RuntimeException("Failure at sending message with user id: " + userId, e);
                LOGGER.log(System.Logger.Level.DEBUG, e);
                throw exception;
            }
        }

        @Override
        public void editMessage(long userId, long messageId, String newText) {
            try {
                EditMessageText editRequest = EditMessageText.builder()
                        .chatId(userId)
                        .messageId((int) messageId)
                        .text(newText)
                        .build();
                execute(editRequest);
            } catch (TelegramApiException e) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Failure at editing message with user id: " + userId + ", message id: " + messageId, e);
            }
        }

        @Override
        public void deleteMessage(long userId, long messageId) {
            try {
                DeleteMessage deleteRequest = DeleteMessage.builder()
                        .chatId(userId)
                        .messageId((int) messageId)
                        .build();
                execute(deleteRequest);
            } catch (TelegramApiException e) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Failure at deleting message with user id: " + userId + ", message id: " + messageId, e);
            }
        }

        @Override
        public File getFileRequest(GetFile getFileRequest) {
            try {
                return execute(getFileRequest);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
