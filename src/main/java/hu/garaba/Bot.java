package hu.garaba;

import org.imgscalr.Scalr;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;


public class Bot extends TelegramLongPollingBot {

    //    private static final String OPENAI_API_KEY = Objects.requireNonNull(System.getenv("OPENAI_API_KEY"));

    private final Executor executor;
    public final HttpClient httpClient;
    private final Map<Long, Session> sessionMap = new ConcurrentHashMap<>();

    private final String TEMP_BOT_TOKEN;
    public Bot(String botToken, Executor executor) {
        super(botToken);
        this.TEMP_BOT_TOKEN = botToken;

        this.executor = executor;
        this.httpClient = HttpClient.newBuilder().executor(executor).build();
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

        executor.execute(() -> handleUpdate(update));
    }

    private void handleUpdate(Update update) {
        Message message = update.getMessage();
        assert message != null;

        User user = message.getFrom();
        Session session = sessionMap.computeIfAbsent(user.getId(), Session::new);

        if (message.hasPhoto()) {
            List<PhotoSize> photoList = message.getPhoto();

            PhotoSize maxSizePhoto = photoList.stream().sorted(Comparator.comparing(PhotoSize::getFileSize)).skip(photoList.size() - 1).findAny().get();
            GetFile getFileRequest = new GetFile(maxSizePhoto.getFileId());
            try {
                File imageFile = execute(getFileRequest);

                session.addImageMessage(message.getText(), URI.create(imageFile.getFileUrl(TEMP_BOT_TOKEN)));

//                BufferedImage image = ImageIO.read();
//                BufferedImage resizedImage = Scalr.resize(image, 1400);
//                image.flush();
            } catch (TelegramApiException e) {
                e.printStackTrace(System.err);
            }
        } else {
            session.addMessage(message.getText());
        }

        session.sendConversation(httpClient, new Session.MessageUpdateHandler() {
            private long messageId = -1;
            private final StringBuilder textBuffer = new StringBuilder();
            private int newChars = 0;
            private boolean isFinished;

            @Override
            public void start() {
                assert !isFinished;

                messageId = sendMessage(user.getId(), "...");
            }

            @Override
            public void update(String appendText) {
                assert !isFinished;

                newChars += appendText.length();
                textBuffer.append(appendText);

                if (newChars > 20) {
                    newChars = 0;
                    editMessage(user.getId(), messageId, textBuffer.toString());
                }
            }

            @Override
            public String finish() {
                if (!isFinished && newChars > 0) {
                    editMessage(user.getId(), messageId, textBuffer.toString());
                }

                isFinished = true;

                return textBuffer.toString();
            }

            @Override
            public void cancel() {
                assert !isFinished;
            }
        });
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
            throw new RuntimeException(e);
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
            System.err.println("Failure at editing message");
            e.printStackTrace(System.err);
        }
    }
}
