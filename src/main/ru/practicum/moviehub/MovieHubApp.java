package ru.practicum.moviehub;

import ru.practicum.moviehub.http.MoviesServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;

public class MovieHubApp {
    private static final int PORT = 8080;

    public static void main(String[] args) {
        final MoviesServer server;
        try {
            server = new MoviesServer(new MoviesStore(), PORT);
        } catch (IOException e) {
            System.out.println("Не удалось запустить сервер. Причина: " + e.getMessage());
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}