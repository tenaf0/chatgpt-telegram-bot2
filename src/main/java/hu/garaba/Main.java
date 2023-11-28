package hu.garaba;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static final String TELEGRAM_API_KEY = Objects.requireNonNull(System.getenv("TELEGRAM_API_KEY"));

    public static void main(String[] args) throws Exception {
        var executorService = Executors.newVirtualThreadPerTaskExecutor();

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        Bot bot = new Bot(TELEGRAM_API_KEY, executorService);
        botsApi.registerBot(bot);
    }
}