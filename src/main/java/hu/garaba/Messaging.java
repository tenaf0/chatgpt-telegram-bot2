package hu.garaba;

import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;

public interface Messaging {
    int sendMessage(long userId, String message);
    void editMessage(long userId, long messageId, String newText);
    void deleteMessage(long userId, long messageId);

    File getFileRequest(GetFile getFileRequest); // TODO: Temporary method
}
