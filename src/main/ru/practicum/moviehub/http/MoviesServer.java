package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {

    private final HttpServer server;

    public MoviesServer(MoviesStore moviesStore, int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        this.server.createContext("/movies", new MoviesHandler(moviesStore));
    }

    public void start() {
        server.start();
    }


    public void stop() {
        server.stop(1);
    }
}