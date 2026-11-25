package ru.practicum.moviehub.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {
    static final String MOVIES_PATH = "/movies";
    private static final int MIN_YEAR = 1888;
    private static final int MAX_TITLE_LENGTH = 100;

    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        // HttpServer выбирает контекст по префиксу, поэтому /moviesExtra тоже нужно проверить.
        if (!MOVIES_PATH.equals(exchange.getRequestURI().getPath())) {
            sendError(exchange, 404, "Путь не найден");
            return;
        }
        switch (exchange.getRequestMethod()) {
            case "GET":
                getMovies(exchange);
                break;
            case "POST":
                addMovie(exchange);
                break;
            default:
                sendMethodNotAllowed(exchange, "GET, POST");
        }
    }

    private void getMovies(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            sendResponse(exchange, 200, store.findAll());
            return;
        }
        int year;
        try {
            // -1 сохраняет пустой год после знака = чтобы его тоже проверить
            String[] parts = query.split("=", -1);
            if (parts.length != 2 || !URLDecoder.decode(parts[0], StandardCharsets.UTF_8).equals("year")) {
                throw new IllegalArgumentException();
            }
            // декодируем значение из URL и проверяем что это число в допустимом диапазоне
            year = Integer.parseInt(URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            if (!isValidYear(year)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "Некорректный параметр запроса — 'year'");
            return;
        }
        sendResponse(exchange, 200, store.findByYear(year));
    }

    private void addMovie(HttpExchange exchange) throws IOException {
        // часть после ; может содержать кодировку поэтому отдельно сравниваем сам тип JSON
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
            sendError(exchange, 415, "Требуется Content-Type application/json");
            return;
        }
        JsonObject json;
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try (JsonReader reader = new JsonReader(new StringReader(body))) {
            // Читаем через адаптер, чтобы Gson не принял JSON с ошибками.
            reader.setLenient(false);
            JsonElement value = gson.getAdapter(JsonElement.class).read(reader);
            // После объекта не должно быть второго JSON или другого текста.
            if (value == null || !value.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonParseException("Ожидается JSON-объект");
            }
            json = value.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException | IOException e) {
            sendError(exchange, 400, "Некорректный JSON");
            return;
        }
        String title = readTitle(json);
        Integer year = readYear(json.get("year"));
        List<String> details = validateMovie(title, year);
        if (!details.isEmpty()) {
            sendResponse(exchange, 422, new ErrorResponse("Ошибка валидации", details));
            return;
        }
        Movie movie = store.add(title, year);
        exchange.getResponseHeaders().set("Location", MovieByIdHandler.MOVIE_PATH_PREFIX + movie.getId());
        sendResponse(exchange, 201, movie);
    }

    private List<String> validateMovie(String title, Integer year) {
        // Собираем все ошибки, чтобы клиент мог исправить поля за один запрос.
        List<String> details = new ArrayList<>();
        if (title == null || title.isBlank()) {
            details.add("название не должно быть пустым и должно быть строкой");
        } else if (title.length() > MAX_TITLE_LENGTH) {
            details.add("название должно содержать не более 100 символов");
        }
        if (year == null || !isValidYear(year)) {
            details.add("год должен быть целым числом между 1888 и " + (Year.now().getValue() + 1));
        }
        return details;
    }

    private String readTitle(JsonObject json) {
        JsonElement titleValue = json.get("title");
        // проверяем тип до getAsString иначе Gson может превратить число в строку
        if (titleValue != null && titleValue.isJsonPrimitive() && titleValue.getAsJsonPrimitive().isString()) {
            return titleValue.getAsString();
        }
        return null;
    }

    private Integer readYear(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            // не обрезаем дробную часть и не допускаем переполнение int
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private boolean isValidYear(int year) {
        return year >= MIN_YEAR && year <= Year.now().getValue() + 1;
    }
}
