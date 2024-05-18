package hu.garaba.gpt;

import hu.garaba.*;
import hu.garaba.db.UserDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.net.http.HttpClient;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.Mockito.*;

public class BotTest {
    private UserDatabase db;
    private Messaging messaging;
    private SessionManager sessionManager;
    private BotCommunicationHandler communicationHandler;

    @BeforeEach
    public void init() {
        Credentials credentials = new Credentials("TELEGRAM_BOT_TOKEN", "OPENAI_API_KEY");
        db = mock(UserDatabase.class);
        messaging = mock(Messaging.class);
        sessionManager = mock(SessionManager.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        communicationHandler = new BotCommunicationHandler(new BotContext(credentials,
                executor,
                db,
                mock(HttpClient.class),
                messaging), sessionManager);
    }

    @Test
    public void botAccessDeniedTest() {
        when(db.isWhitelisted(1L)).thenReturn(false);

        Update update = new Update();
        Message message = new Message();
        User from = new User();
        from.setId(1L);
        message.setFrom(from);
        message.setText("Hi");
        update.setMessage(message);

        communicationHandler.handleUpdate(update);

        verify(db).isWhitelisted(1L);
        verify(messaging).sendMessage(eq(1L), startsWith("You are not authorized to access this bot"));
    }

    @Test
    public void botHelloTest() {
        when(db.isWhitelisted(1L)).thenReturn(true);

        Session sessionMock = mock(Session.class);
        when(sessionManager.getOrCreate(eq(1L), any())).thenReturn(sessionMock);

        Update update = new Update();
        Message message = new Message();
        User from = new User();
        from.setId(1L);
        message.setFrom(from);
        message.setText("Hi");
        update.setMessage(message);

        communicationHandler.handleUpdate(update);

        verify(db).isWhitelisted(1L);
        verify(sessionMock).addMessage("Hi");
        verify(sessionMock).sendConversation(any());
    }

    @Test
    public void botCommandNotFoundTest() {
        when(db.isWhitelisted(1L)).thenReturn(true);

        Session sessionMock = mock(Session.class);
        when(sessionManager.getOrCreate(eq(1L), any())).thenReturn(sessionMock);

        Update update = new Update();
        Message message = new Message();
        User from = new User();
        from.setId(1L);
        message.setFrom(from);
        message.setText("/noSuchCommand");
        update.setMessage(message);

        communicationHandler.handleUpdate(update);
        verify(messaging).sendMessage(1L, "Command /noSuchCommand is unknown.");
    }

    @Test
    public void addUserTest() throws SQLException {
        when(db.isWhitelisted(1L)).thenReturn(true);
        when(db.isAdmin(1L)).thenReturn(true);

        Session sessionMock = mock(Session.class);
        when(sessionManager.getOrCreate(eq(1L), any())).thenReturn(sessionMock);

        Update update = new Update();
        Message message = new Message();
        User from = new User();
        from.setId(1L);
        message.setFrom(from);
        message.setText("/addUser 2 John");
        update.setMessage(message);

        communicationHandler.handleUpdate(update);
        verify(db).addUser(2L, "John");
        verify(messaging).sendMessage(eq(1L), eq("Successfully added user!"));
    }

    @Test
    public void modelChangeTest() {
        when(db.isWhitelisted(1L)).thenReturn(true);
        Session sessionMock = mock(Session.class);
        when(sessionManager.getOrCreate(anyLong(), any())).thenReturn(sessionMock);

        when(sessionMock.getModel()).thenReturn(Model.GPT4);

        Update update = new Update();
        Message message = new Message();
        User from = new User();
        from.setId(1L);
        message.setFrom(from);
        message.setText("/model");
        update.setMessage(message);

        communicationHandler.handleUpdate(update);
        verify(messaging).sendMessage(eq(1L), eq("The current model is GPT4"));
    }
}
