package hu.garaba;

public interface Messaging {
    int sendMessage(long userId, String message);
    void editMessage(long userId, long messageId, String newText);
    void deleteMessage(long userId, long messageId);
}
