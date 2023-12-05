package hu.garaba;

import hu.garaba.gpt.GPTUsage;
import hu.garaba.gpt.ImageGeneration;
import hu.garaba.gpt.Model;
import hu.garaba.gpt.TokenCalculator;
import hu.garaba.tools.Summarizer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class Bot extends TelegramLongPollingBot {
    private static final System.Logger LOGGER = System.getLogger(Bot.class.getCanonicalName());

    private final BotContext botContext;
    private final Map<Long, Session> sessionMap = new ConcurrentHashMap<>();

    public Bot(BotContext botContext) {
        super(botContext.credentials().TELEGRAM_BOT_TOKEN());

        if (botContext.messaging() != null) {
            this.botContext = botContext;
        } else {
            this.botContext = new BotContext(botContext.credentials(), botContext.executor(), botContext.userDatabase(), botContext.httpClient(), new Messaging() {
                @Override
                public int sendMessage(long userId, String message) {
                    return Bot.this.sendMessage(userId, message);
                }

                @Override
                public void editMessage(long userId, long messageId, String newText) {
                    Bot.this.editMessage(userId, messageId, newText);
                }

                @Override
                public void deleteMessage(long userId, long messageId) {
                    Bot.this.deleteMessage(userId, messageId);
                }
            });
        }
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

        botContext.executor().execute(() -> handleUpdate(update));
    }

    private void handleUpdate(Update update) {
        Message message = update.getMessage();
        assert message != null;

        User user = message.getFrom();
        if (!botContext.userDatabase().isWhitelisted(user.getId())) {
            System.err.println("Access denied to user " + user.getId());
            sendMessage(user.getId(), "You are not authorized to access this bot. Please request access from the admin of this bot, mentioning your user id: " + user.getId());
            return;
        }

        Session session = sessionMap.computeIfAbsent(user.getId(), id -> new Session(botContext, id));

        if (message.getText() != null && message.getText().startsWith("/")) {
            handleCommand(session, message);
        } else {
            handleMessage(session, message);
        }
    }

    private void handleCommand(Session session, Message message) {
        String text = message.getText();

        if (text.startsWith("/clear")) {
            session.initConversation();
            sendMessage(message.getFrom().getId(), "Conversation cleared");
        } else if (text.startsWith("/addUser ")) {
            long userId = message.getFrom().getId();
            if (!botContext.userDatabase().isAdmin(userId)) {
                LOGGER.log(System.Logger.Level.DEBUG, "User " + userId + " tried to execute /addUser!");
                throw new RuntimeException("This user is not an admin, /addUser command denied!");
            }

            try {
                String[] words = text.split("\\s+");

                long toBeAddedUserId = Long.parseLong(words[1]);
                String name = words[2];

                botContext.userDatabase().addUser(toBeAddedUserId, name);
                sendMessage(userId, "Successfully added user!");
            } catch (Exception e) {
                sendMessage(userId, "Failed to add user: " + e);
            }
        } else if (text.startsWith("/modelChange ")) {
            String arg = text.substring("/modelChange ".length()).trim().toLowerCase();
            Model model = switch (arg) {
                case "gpt4" -> Model.GPT4;
                case "gpt3" -> Model.GPT3_TURBO;
                default -> null;
            };

            if (model == null) {
                sendMessage(message.getFrom().getId(), "The specified model is unknown");
            } else {
                session.changeModelOfConversation(model);
                sendMessage(message.getFrom().getId(), "Now using model: " + model);
            }
        } else if (text.startsWith("/summarize ")) {
            long userId = message.getFrom().getId();

            String[] words = text.split("\\s+");
            if (words.length < 2 || !Summarizer.isValidURL(words[1])) {
                sendMessage(userId, "The command is ill-formed, or you provided an illegal URL. The correct syntax: /summarize <url>");
            } else {
                URI uri = URI.create(words[1]);
                String textToSummarize;
                try {
                    if (uri.getHost() != null && uri.getHost().replace(".", "").contains("youtube")) {
                        sendMessage(userId, "Summarizing video transcript at " + uri + ":");
                        textToSummarize = Summarizer.extractVideoTranscript(uri);
                    } else {
                        sendMessage(userId, "Summarizing article at " + uri + ":");
                        textToSummarize = Summarizer.extractArticle(uri);
                    }

                    Summarizer.summarizeText(session, textToSummarize);
                    session.sendConversation(new MessageUpdateHandler(userId));
                } catch (IOException e) {
                    LOGGER.log(System.Logger.Level.DEBUG, "/summarize command failed", e);
                    sendMessage(userId, "/summarize command failed");
                }
            }
        } else if (text.startsWith("/generateImage ")) { // TODO: Handle if without ' '
            boolean hd = text.contains("#hd");
            String url = ImageGeneration.generateImage(botContext, text.substring("/generateImage ".length()), 1, hd);
            long userId = message.getFrom().getId();
            try {
                botContext.userDatabase().flushUsage(userId, GPTUsage.ImageGeneration, Model.DALL_E_3, 1);
            } catch (SQLException e) {
                LOGGER.log(System.Logger.Level.DEBUG, "Could not log user's image generation", userId);
            }

            SendPhoto sendPhoto = SendPhoto.builder()
                    .chatId(userId)
                    .photo(new InputFile(url))
                    .build();
            try {
                execute(sendPhoto);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void handleMessage(Session session, Message message) {
        User user = message.getFrom();

        if (message.hasPhoto()) {
            List<PhotoSize> photoList = message.getPhoto();

            PhotoSize maxSizePhoto = photoList.stream().sorted(Comparator.comparing(PhotoSize::getFileSize)).skip(photoList.size() - 1).findAny().get();
            GetFile getFileRequest = new GetFile(maxSizePhoto.getFileId());
            try {
                File imageFile = execute(getFileRequest);

                session.addImageMessage(message.getText(), URI.create(imageFile.getFileUrl(botContext.credentials().TELEGRAM_BOT_TOKEN())),
                        TokenCalculator.image(true, maxSizePhoto.getWidth(), maxSizePhoto.getHeight()));
            } catch (TelegramApiException e) {
                LOGGER.log(System.Logger.Level.DEBUG, "Handling message with image failed.", e);
            }
        } else {
            session.addMessage(message.getText());
        }

        session.sendConversation(new MessageUpdateHandler(user.getId()));
    }

    private class MessageUpdateHandler implements Session.MessageUpdateHandler {
        private final long userId;
        private long messageId = -1;
        private final StringBuilder textBuffer = new StringBuilder();
        private int newChars = 0;
        private boolean isFinished;
        private boolean isCanceled;

        MessageUpdateHandler(long userId) {
            this.userId = userId;
        }

        @Override
        public void start() {
            assert !isFinished;

            messageId = sendMessage(userId, "...");
        }

        @Override
        public void update(String appendText) {
            assert !isFinished;

            newChars += appendText.length();
            textBuffer.append(appendText);

            if (newChars > 26) {
                newChars = 0;
                editMessage(userId, messageId, textBuffer.toString());
            }
        }

        @Override
        public String finish() {
            if (!isFinished && !isCanceled && newChars > 0) {
                editMessage(userId, messageId, textBuffer.toString());
            }

            isFinished = true;

            return textBuffer.toString();
        }

        @Override
        public void cancel() {
            assert !isFinished;

            isCanceled = true;

            if (messageId == -1) {
                return;
            }

            System.out.println("Deleting message!");
            deleteMessage(userId, messageId);
        }
    }

    private int sendMessage(long userId, String message) {
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

    private void editMessage(long userId, long messageId, String newText) {
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

    private void deleteMessage(long userId, long messageId) {
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

}
