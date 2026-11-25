package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;

class MovieByIdHandler extends BaseHttpHandler {
    static final String MOVIE_PATH_PREFIX = MoviesHandler.MOVIES_PATH + "/";

    private final MoviesStore store;

    MovieByIdHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        String rawId = exchange.getRequestURI().getPath().substring(MOVIE_PATH_PREFIX.length());
        if (rawId.isEmpty() || rawId.contains("/")) {
            sendError(exchange, 404, "Путь не найден");
            return;
        }
        handleMovie(exchange, rawId);
    }

    private void handleMovie(HttpExchange exchange, String rawId) throws IOException {
        String method = exchange.getRequestMethod();
        // сначала проверяем метод чтобы неподдерживаемый запрос получил 405
        if (!method.equals("GET") && !method.equals("DELETE")) {
            sendMethodNotAllowed(exchange, "GET, DELETE");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(rawId);
        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Некорректный ID");
            return;
        }
        if (method.equals("GET")) {
            Movie movie = store.findById(id);
            if (movie == null) {
                sendError(exchange, 404, "Фильм не найден");
            } else {
                sendResponse(exchange, 200, movie);
            }
        } else if (store.delete(id)) {
            sendNoContent(exchange);
        } else {
            sendError(exchange, 404, "Фильм не найден");
        }
    }
}
