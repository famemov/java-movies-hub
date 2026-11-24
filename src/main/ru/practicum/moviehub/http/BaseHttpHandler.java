package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";

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
}
