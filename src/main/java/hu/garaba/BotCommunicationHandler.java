package hu.garaba;

import command.*;
import hu.garaba.gpt.GPTUsage;
import hu.garaba.gpt.Model;
import hu.garaba.gpt.TokenCalculator;
import hu.garaba.gpt.Whisper;
import hu.garaba.util.FileDownloader;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BotCommunicationHandler {
    private static final System.Logger LOGGER = System.getLogger(BotCommunicationHandler.class.getCanonicalName());

    private final BotContext botContext;
    private final SessionManager sessionManager;
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    private final Map<String, Command> commandHandler = new HashMap<>();

    public BotCommunicationHandler(BotContext botContext, SessionManager sessionManager) {
        this.botContext = botContext;
        this.sessionManager = sessionManager;

        registerCommandHandlers();

        int oldInteractionClearDate = 20;

        scheduledExecutor.scheduleAtFixedRate(() -> {
            LOGGER.log(System.Logger.Level.INFO, "Clearing conversations");
            LocalTime cutoffDate = LocalTime.now().minusMinutes(oldInteractionClearDate);

            var sessionPairs = sessionManager.sessions();

            var iter = sessionPairs.iterator();
            while (iter.hasNext()) {
                Map.Entry<Long, Session> sessionPair = iter.next();
                if (sessionPair.getValue().shouldClear(cutoffDate)) {
                    sessionPair.getValue().clearConversation();
                    LOGGER.log(System.Logger.Level.INFO, "Clearing conversation of " + sessionPair.getKey());
//                    iter.remove();
                }
            }
        }, oldInteractionClearDate, oldInteractionClearDate, TimeUnit.MINUTES);
    }

    private void registerCommandHandlers() {
        commandHandler.put("clear", new ClearCommand());
        commandHandler.put("continue", (botContext, session, message) -> session.resetClear());
        commandHandler.put("addUser", new AddUserCommand());
        commandHandler.put("usage", new UsageCommand());
        commandHandler.put("model", new ModelCommand());
        commandHandler.put("summarize", new SummarizeCommand());
    }

    public void handleUpdate(Update update) {
        Message message = update.getMessage();
        assert message != null;

        User user = message.getFrom();
        if (!botContext.userDatabase().isWhitelisted(user.getId())) {
            System.err.println("Access denied to user " + user.getId());
            botContext.messaging().sendMessage(user.getId(), "You are not authorized to access this bot. Please request access from the admin of this bot, mentioning your user id: " + user.getId());
            return;
        }

        Session session = sessionManager.getOrCreate(user.getId(), id -> new Session(botContext, id));

        if (message.getText() != null && message.getText().startsWith("/")) {
            handleCommand(session, message);
        } else {
            handleMessage(session, message);
        }
    }

    private void handleCommand(Session session, Message message) {
        String text = message.getText();

        Pattern commandPattern = Pattern.compile("/([a-zA-Z]+)\\s?.*");
        Matcher matcher = commandPattern.matcher(text);
        matcher.matches();
        String command = matcher.group(1);

        Command handler = commandHandler.get(command);
        if (handler == null) {
            botContext.messaging().sendMessage(message.getFrom().getId(), "Command /" + command + " is unknown.");
        } else {
            handler.action(botContext, session, message);
        }


/*
        if (text.startsWith("/clear")) {
            session.clearConversation();
            sendMessage(message.getFrom().getId(), "Conversation cleared");
        } else if (text.startsWith("/continue")) {
            session.resetClear();
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
        } else if (text.startsWith("/usage")) {
            long userId = message.getFrom().getId();

            String usage = null;
            try {
                usage = botContext.userDatabase().queryUnpaidUsage(userId);
            } catch (SQLException e) {
                RuntimeException exception = new RuntimeException("Error happened during querying usage data for user " + userId, e);
                LOGGER.log(System.Logger.Level.DEBUG, exception);
                throw exception;
            } finally {
                sendMessage(userId, Objects.requireNonNullElse(usage, "Error happened during the querying of your usage"));
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
                    session.sendConversation(new MessageUpdateHandler(userId, botContext));
                } catch (Exception e) {
                    LOGGER.log(System.Logger.Level.DEBUG, "/summarize command failed", e);
                    sendMessage(userId, "/summarize command failed");
                }
            }
        } else if (text.startsWith("/generateImage ")) { // TODO: Handle if without ' '
            long userId = message.getFrom().getId();

            boolean hd = text.contains("#hd");
            String url;
            try {
                url = ImageGeneration.generateImage(botContext, text.substring("/generateImage ".length()), 1, hd);
            } catch (GPTException e) {
                sendMessage(userId, e.userMessage());
                LOGGER.log(System.Logger.Level.DEBUG, e);
                throw new RuntimeException(e);
            }

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
        } else if (text.startsWith("/speak ")) {
            long userId = message.getFrom().getId();

            String userText = text.substring("/speak ".length());
            Path path = Whisper.speakText(botContext, userText);
            SendVoice sendVoice = SendVoice.builder()
                    .chatId(userId)
                    .voice(new InputFile(path.toFile()))
                    .caption(userText)
                    .build();

            // TODO: Log usage

            try {
                execute(sendVoice);
            } catch (TelegramApiException e) {
                sendMessage(userId, "Failure during voice generation.");
                throw new RuntimeException(e);
            }
        }
*/
    }

    private void handleMessage(Session session, Message message) {
        User user = message.getFrom();

        if (message.hasPhoto()) {
            List<PhotoSize> photoList = message.getPhoto();

            PhotoSize maxSizePhoto = photoList.stream().sorted(Comparator.comparing(PhotoSize::getFileSize)).skip(photoList.size() - 1).findAny().get();
            GetFile getFileRequest = new GetFile(maxSizePhoto.getFileId());
            try {
                File imageFile = botContext.messaging().getFileRequest(getFileRequest);

                boolean b = session.addImageMessage(message.getCaption(), URI.create(imageFile.getFileUrl(botContext.credentials().TELEGRAM_BOT_TOKEN())),
                        TokenCalculator.image(true, maxSizePhoto.getWidth(), maxSizePhoto.getHeight()));
                if (b) {
                    botContext.messaging().sendMessage(user.getId(), "Conversation cleared");
                }
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.DEBUG, "Handling message with image failed.", e);
            }
        } else if (message.hasVoice()) {
            Voice voice = message.getVoice();

            GetFile getFileRequest = new GetFile(voice.getFileId());
            Path voiceFilePath = null;
            try {
                File voiceFile = botContext.messaging().getFileRequest(getFileRequest);

                URI uri = URI.create(voiceFile.getFileUrl(botContext.credentials().TELEGRAM_BOT_TOKEN()));
                voiceFilePath = FileDownloader.downloadFile(uri);

                String transcribed = Whisper.transcribeVoice(botContext, voiceFilePath);
                botContext.userDatabase().flushUsage(user.getId(), GPTUsage.VoiceTranscription, Model.WHISPER_1, voiceFile.getFileSize() / 1000);

                addSessionMessage(user.getId(), session, transcribed);
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.DEBUG, "Handling message with voice message failed.", e);
                botContext.messaging().sendMessage(user.getId(), "Could not transcribe voice message");
            } finally {
                if (voiceFilePath != null) {
                    try {
                        Files.delete(voiceFilePath);
                    } catch (IOException e) {
                        LOGGER.log(System.Logger.Level.ERROR, "Failed to delete temporary voice file " + voiceFilePath, e);
                    }
                }
            }
        } else {
            addSessionMessage(user.getId(), session, message.getText());
        }

        session.sendConversation(new MessageUpdateHandler(user.getId(), botContext));
    }

    private void addSessionMessage(long userId, Session session, String text) {
        boolean newConversation = session.addMessage(text);
        if (newConversation) {
            botContext.messaging().sendMessage(userId, "Conversation cleared");
        }
    }
}
