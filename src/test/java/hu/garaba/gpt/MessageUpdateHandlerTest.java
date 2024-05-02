package hu.garaba.gpt;

import hu.garaba.BotContext;
import hu.garaba.MessageUpdateHandler;
import hu.garaba.Messaging;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

public class MessageUpdateHandlerTest {
    @Test
    public void test() {
        BotContext context = mock(BotContext.class);
        Messaging mockMessaging = mock(Messaging.class);
        when(context.messaging()).thenReturn(mockMessaging);

        MessageUpdateHandler updateHandler = new MessageUpdateHandler(1L, context);

        updateHandler.start();
        verify(mockMessaging)
                .sendMessage(1L, "...");

        String text = "a".repeat(MessageUpdateHandler.EDIT_THRESHOLD);
        updateHandler.update(text);

        verify(mockMessaging, times(0))
                .editMessage(eq(1L), eq(0L), any());


        text = text + "b";
        updateHandler.update("b");

        verify(mockMessaging)
                .editMessage(1L, 0L, text);


        text = text + "b".repeat(MessageUpdateHandler.EDIT_THRESHOLD);
        updateHandler.update("b".repeat(MessageUpdateHandler.EDIT_THRESHOLD));

        updateHandler.finish();

        verify(mockMessaging, times(2))
                .editMessage(eq(1L), eq(0L), any());
        verify(mockMessaging)
                .editMessage(1L, 0L, text);
    }
}
