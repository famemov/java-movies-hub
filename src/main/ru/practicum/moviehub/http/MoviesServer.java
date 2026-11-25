package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    private final HttpServer server;

    public MoviesServer(MoviesStore store, int port) {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
            server.createContext(MoviesHandler.MOVIES_PATH, new MoviesHandler(store));
            server.createContext(MovieByIdHandler.MOVIE_PATH_PREFIX, new MovieByIdHandler(store));
            server.createContext("/", new BaseHttpHandler() {
                @Override
                protected void handleRequest(HttpExchange exchange) throws IOException {
                    sendError(exchange, 404, "Путь не найден");
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось создать HTTP-сервер", e);
        }
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }
}
