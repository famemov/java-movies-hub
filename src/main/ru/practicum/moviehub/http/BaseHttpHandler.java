package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ru.practicum.moviehub.api.ErrorResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";

    protected final Gson gson = new Gson();

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        try {
            handleRequest(exchange);
        } finally {
            exchange.close();
        }
    }

    protected abstract void handleRequest(HttpExchange exchange) throws IOException;

    protected void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        // длина ответа нужна в байтах, у кириллицы она отличается от длины строки
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", CT_JSON);
        exchange.sendResponseHeaders(status, bytes.length);
        // поток закрывается автоматически после записи тела ответа
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    protected void sendNoContent(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", CT_JSON);
        // -1 означает что тела ответа не будет
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    protected void sendMethodNotAllowed(HttpExchange exchange, String allow) throws IOException {
        exchange.getResponseHeaders().set("Allow", allow);
        sendError(exchange, 405, "Метод не поддерживается");
    }

    protected void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendResponse(exchange, status, new ErrorResponse(message));
    }

    protected void sendResponse(HttpExchange exchange, int status, Object value) throws IOException {
        sendJson(exchange, status, gson.toJson(value));
    }
}
