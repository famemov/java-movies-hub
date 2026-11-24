package ru.practicum.moviehub.http;

import com.google.gson.Gson;
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
    private static final String MOVIES_PATH = "/movies";
    private static final String MOVIE_PATH_PREFIX = MOVIES_PATH + "/";
    private static final int MIN_YEAR = 1888;
    private static final int MAX_TITLE_LENGTH = 100;

    private final MoviesStore store;
    private final Gson gson = new Gson();

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Проверяем весь путь: /moviesExtra не должен попадать в список фильмов.
            String path = exchange.getRequestURI().getPath();
            if (path.equals(MOVIES_PATH)) {
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
            } else if (path.startsWith(MOVIE_PATH_PREFIX) && path.length() > MOVIE_PATH_PREFIX.length()
                    && !path.substring(MOVIE_PATH_PREFIX.length()).contains("/")) {
                handleMovie(exchange, path.substring(MOVIE_PATH_PREFIX.length()));
            } else {
                sendError(exchange, 404, "Путь не найден");
            }
        } finally {
            // Закрываем обмен даже при ошибке во время обработки запроса как и было в практике к этому финальному заданию
            exchange.close();
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
        // собираем все ошибки полей чтобы клиент мог исправить их за один запрос
        List<String> details = new ArrayList<>();
        JsonElement titleValue = json.get("title");
        String title = null;
        // проверяем тип до getAsString иначе Gson может превратить число в строку
        if (titleValue != null && titleValue.isJsonPrimitive() && titleValue.getAsJsonPrimitive().isString()) {
            title = titleValue.getAsString();
        }
        if (title == null || title.isBlank()) {
            details.add("название не должно быть пустым и должно быть строкой");
        } else if (title.length() > MAX_TITLE_LENGTH) {
            details.add("название должно содержать не более 100 символов");
        }
        Integer year = readYear(json.get("year"));
        if (year == null || !isValidYear(year)) {
            details.add("год должен быть целым числом между 1888 и " + (Year.now().getValue() + 1));
        }
        if (!details.isEmpty()) {
            sendResponse(exchange, 422, new ErrorResponse("Ошибка валидации", details));
            return;
        }
        Movie movie = store.add(title, year);
        exchange.getResponseHeaders().set("Location", "/movies/" + movie.getId());
        sendResponse(exchange, 201, movie);
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

    private void sendMethodNotAllowed(HttpExchange exchange, String allow) throws IOException {
        exchange.getResponseHeaders().set("Allow", allow);
        sendError(exchange, 405, "Метод не поддерживается");
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendResponse(exchange, status, new ErrorResponse(message));
    }

    private void sendResponse(HttpExchange exchange, int status, Object value) throws IOException {
        sendJson(exchange, status, gson.toJson(value));
    }
}
