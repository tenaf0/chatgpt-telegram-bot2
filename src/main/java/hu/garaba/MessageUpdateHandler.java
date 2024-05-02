package hu.garaba;

public class MessageUpdateHandler implements Session.MessageUpdateHandler {
    private final long userId;
    private final Messaging messaging;
    private long messageId = -1;
    private final StringBuilder textBuffer = new StringBuilder();
    private int newChars = 0;
    private boolean isFinished;
    private boolean isCanceled;

    public MessageUpdateHandler(long userId, BotContext botContext) {
        this.userId = userId;
        this.messaging = botContext.messaging();
    }

    @Override
    public void start() {
        assert !isFinished;

        messageId = messaging.sendMessage(userId, "...");
    }

    @Override
    public void update(String appendText) {
        assert !isFinished;

        newChars += appendText.length();
        textBuffer.append(appendText);

        if (newChars > 60) {
            newChars = 0;
            messaging.editMessage(userId, messageId, textBuffer.toString());
        }
    }

    @Override
    public String finish() {
        if (!isFinished && !isCanceled && newChars > 0) {
            messaging.editMessage(userId, messageId, textBuffer.toString());
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
        messaging.deleteMessage(userId, messageId);
    }
}