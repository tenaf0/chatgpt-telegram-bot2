package hu.garaba;

import hu.garaba.db.UserDatabase;
import org.apache.commons.logging.Log;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executors;

public class Main {
    private static final System.Logger LOGGER = System.getLogger(Main.class.getCanonicalName());
    private static final String TELEGRAM_API_KEY = Objects.requireNonNull(System.getenv("TELEGRAM_API_KEY"));
    private static final String OPENAI_API_KEY = Objects.requireNonNull(System.getenv("OPENAI_API_KEY"));

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("A path to the db file is required!");
        }

        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        UserDatabase userDatabase = new UserDatabase(Path.of(args[0]));

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

        Bot bot = new Bot(new BotContext(
                new Credentials(TELEGRAM_API_KEY, OPENAI_API_KEY),
                executorService, userDatabase,
                HttpClient.newBuilder().executor(executorService).build(),
                null));
        botsApi.registerBot(bot);

        LOGGER.log(System.Logger.Level.DEBUG, "Bot started");
    }
}