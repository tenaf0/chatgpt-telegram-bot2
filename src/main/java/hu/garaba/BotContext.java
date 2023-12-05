package hu.garaba;

import hu.garaba.db.UserDatabase;
import jakarta.annotation.Nullable;

import java.net.http.HttpClient;
import java.util.concurrent.Executor;

public record BotContext(Credentials credentials, Executor executor, UserDatabase userDatabase, HttpClient httpClient, @Nullable Messaging messaging) {
}
