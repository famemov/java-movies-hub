package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static final Gson GSON = new Gson();
    private static final MoviesStore STORE = new MoviesStore();
    private static MoviesServer server;
    private static HttpClient client;

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer(STORE, 8080);
        server.start();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }
    // сервер и клиент создаются один раз на все тесты

    @BeforeEach
    void beforeEach() {
        STORE.clear();
    }
    // очищаем фильмы перед каждым тестом чтобы результат не зависел от других тестов

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }
    // после всех проверок останавливаем сервер чтобы освободить порт

    private HttpResponse<String> request(String method, String path, String body, String type) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(3));
        if (type != null) {
            builder.header("Content-Type", type);
        }
        HttpRequest.BodyPublisher requestBody = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        HttpRequest request = builder.method(method, requestBody).build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals("application/json; charset=UTF-8",
                response.headers().firstValue("Content-Type").orElse(""));
        return response;
    }
    // вынес отправку запроса сюда чтобы не повторять сборку запроса и проверку заголовка в каждом тесте

    private HttpResponse<String> post(String body) throws Exception {
        return request("POST", "/movies", body, "application/json; charset=UTF-8");
    }
    // отправляем JSON с нужным Content-Type через общий метод

    private Movie add(String title, int year) throws Exception {
        HttpResponse<String> response = post("{\"title\":" + GSON.toJson(title) + ",\"year\":" + year + "}");
        assertEquals(201, response.statusCode());
        Movie movie = GSON.fromJson(response.body(), Movie.class);
        assertTrue(movie.getId() > 0);
        assertEquals(title, movie.getTitle());
        assertEquals(year, movie.getYear());
        return movie;
    }
    // этот метод добавляет фильм и сразу проверяет что сервер вернул его данные и назначил ID

    private void error(HttpResponse<String> response, int status) {
        assertEquals(status, response.statusCode());
        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        assertTrue(!json.get("error").getAsString().isBlank());
        if (status == 422) {
            assertTrue(json.getAsJsonArray("details").size() > 0);
        }
    }
    // тут проверяем не только код ошибки но и что в ответе есть описание, а для 422 еще и детали

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> response = request("GET", "/movies", null, null);
        assertEquals(200, response.statusCode());
        assertEquals("[]", response.body());
    }
    // проверка на то что пустое хранилище возвращает именно пустой массив с кодом 200

    @Test
    void getMovies_returnsAddedMovies() throws Exception {
        Movie first = add("Начало", 2010);
        Movie second = add("Интерстеллар", 2014);
        HttpResponse<String> response = request("GET", "/movies", null, null);
        assertEquals(200, response.statusCode());
        // TypeToken нужен чтобы Gson восстановил список именно из Movie а не просто из объектов
        List<Movie> movies = GSON.fromJson(response.body(), new ListOfMoviesTypeToken().getType());
        assertEquals(2, movies.size());
        assertEquals(first.getId(), movies.get(0).getId());
        assertEquals(second.getId(), movies.get(1).getId());
    }
    // проверка на то что добавленные фильмы попадают в список и сохраняют свои ID

    @Test
    void post_acceptsBoundariesAndUniqueIds() throws Exception {
        Movie first = add("я".repeat(100), 1888);
        Movie second = add("Будущее", Year.now().getValue() + 1);
        assertNotEquals(first.getId(), second.getId());
    }
    // проверка на то что граничные значения разрешены а у разных фильмов разные ID

    @Test
    void post_rejectsMissingFields() throws Exception {
        error(post("{}"), 422);
    }
    // проверка на то что отсутствующие поля дает 422

    @Test
    void post_rejectsEmptyTitle() throws Exception {
        error(post("{\"title\":\"\",\"year\":2000}"), 422);
    }
    // проверка на то что пустое название дает 422

    @Test
    void post_rejectsBlankTitle() throws Exception {
        error(post("{\"title\":\"   \",\"year\":2000}"), 422);
    }
    // проверка на то что название из пробелов дает 422

    @Test
    void post_rejectsNullTitle() throws Exception {
        error(post("{\"title\":null,\"year\":2000}"), 422);
    }
    // проверка на то что null вместо названия дает 422

    @Test
    void post_rejectsNumericTitle() throws Exception {
        error(post("{\"title\":42,\"year\":2000}"), 422);
    }
    // проверка на то что число вместо названия дает 422

    @Test
    void post_rejectsMissingYear() throws Exception {
        error(post("{\"title\":\"A\"}"), 422);
    }
    // проверка на то что отсутствующий год дает 422

    @Test
    void post_rejectsYearBeforeMinimum() throws Exception {
        error(post("{\"title\":\"A\",\"year\":1887}"), 422);
    }
    // проверка на то что год меньше 1888 дает 422

    @Test
    void post_rejectsFractionalYear() throws Exception {
        error(post("{\"title\":\"A\",\"year\":2000.5}"), 422);
    }
    // проверка на то что дробный год дает 422

    @Test
    void post_rejectsStringYear() throws Exception {
        error(post("{\"title\":\"A\",\"year\":\"2000\"}"), 422);
    }
    // проверка на то что строка вместо числового года дает 422

    @Test
    void post_rejectsLongTitleAndFutureYear() throws Exception {
        error(post("{\"title\":\"" + "x".repeat(101) + "\",\"year\":2000}"), 422);
        error(post("{\"title\":\"A\",\"year\":" + (Year.now().getValue() + 2) + "}"), 422);
        assertEquals("[]", request("GET", "/movies", null, null).body());
    }
    // проверка на то что превышение длины или года дает ошибку и фильм не сохраняется

    @Test
    void post_rejectsEmptyBody() throws Exception {
        error(post(""), 400);
    }
    // проверка на то что пустое тело запроса дает 400

    @Test
    void post_rejectsBrokenJson() throws Exception {
        error(post("{broken"), 400);
    }
    // проверка на то что сломанный JSON дает 400

    @Test
    void post_rejectsArrayBody() throws Exception {
        error(post("[]"), 400);
    }
    // проверка на то что массив вместо объекта дает 400

    @Test
    void post_rejectsNullBody() throws Exception {
        error(post("null"), 400);
    }
    // проверка на то что null вместо объекта дает 400

    @Test
    void post_rejectsUnfinishedJson() throws Exception {
        error(post("{\"title\":"), 400);
    }
    // проверка на то что незаконченный JSON дает 400

    @Test
    void post_rejectsMultipleObjects() throws Exception {
        error(post("{} {}"), 400);
    }
    // проверка на то что два объекта подряд дает 400

    @Test
    void post_rejectsUnquotedFields() throws Exception {
        error(post("{title:'A',year:2000}"), 400);
    }
    // проверка на то что поля без двойных кавычек дает 400

    @Test
    void post_rejectsWrongContentType() throws Exception {
        error(request("POST", "/movies", "{}", "text/plain"), 415);
        error(request("POST", "/movies", "{}", null), 415);
    }
    // проверка на то что сервер не принимает POST с неверным или отсутствующим Content-Type

    @Test
    void getMovie_returnsStoredMovie() throws Exception {
        Movie movie = add("Фильм", 2000);
        HttpResponse<String> response = request("GET", "/movies/" + movie.getId(), null, null);
        assertEquals(200, response.statusCode());
        assertEquals(GSON.toJson(movie), response.body());
    }
    // проверка на то что по ID возвращается тот же фильм который добавили

    @Test
    void deleteMovie_removesOnlyRequestedMovie() throws Exception {
        Movie first = add("Первый", 2000);
        Movie second = add("Второй", 2001);
        HttpResponse<String> response = request("DELETE", "/movies/" + first.getId(), null, null);
        assertEquals(204, response.statusCode());
        assertEquals("", response.body());
        error(request("GET", "/movies/" + first.getId(), null, null), 404);
        assertEquals(200, request("GET", "/movies/" + second.getId(), null, null).statusCode());
    }
    // проверка на то что удаляется только нужный фильм а ответ 204 приходит без тела

    @Test
    void itemErrors_areJson() throws Exception {
        for (String method : new String[]{"GET", "DELETE"}) {
            error(request(method, "/movies/999", null, null), 404);
            error(request(method, "/movies/abc", null, null), 400);
        }
    }
    // проверка на то что GET и DELETE одинаково обрабатывают отсутствующий фильм и ID не числом

    @Test
    void filter_returnsMatchingMoviesOrEmptyList() throws Exception {
        Movie movie = add("A", 2000);
        add("B", 2001);
        HttpResponse<String> response = request("GET", "/movies?year=2000", null, null);
        assertEquals(200, response.statusCode());
        assertEquals("[" + GSON.toJson(movie) + "]", response.body());
        assertEquals("[]", request("GET", "/movies?year=1999", null, null).body());
    }
    // проверка на то что фильтр оставляет фильмы нужного года а без совпадений возвращает пустой массив

    @Test
    void filter_rejectsNonNumericYear() throws Exception {
        error(request("GET", "/movies?year=abc", null, null), 400);
    }
    // проверка на то что год не числом дает 400

    @Test
    void filter_rejectsEmptyYear() throws Exception {
        error(request("GET", "/movies?year=", null, null), 400);
    }
    // проверка на то что пустой год дает 400

    @Test
    void filter_rejectsYearBeforeMinimum() throws Exception {
        error(request("GET", "/movies?year=1887", null, null), 400);
    }
    // проверка на то что год меньше 1888 дает 400

    @Test
    void filter_rejectsYearAboveMaximum() throws Exception {
        error(request("GET", "/movies?year=9999", null, null), 400);
    }
    // проверка на то что слишком большой год дает 400

    @Test
    void filter_rejectsFractionalYear() throws Exception {
        error(request("GET", "/movies?year=2000.5", null, null), 400);
    }
    // проверка на то что дробный год дает 400

    @Test
    void filter_rejectsRepeatedYear() throws Exception {
        error(request("GET", "/movies?year=2000&year=2001", null, null), 400);
    }
    // проверка на то что повтор параметра year дает 400

    @Test
    void filter_rejectsUnknownParameter() throws Exception {
        error(request("GET", "/movies?other=2000", null, null), 400);
    }
    // проверка на то что неизвестный параметр дает 400

    @Test
    void unsupportedMethodsAndUnknownPaths() throws Exception {
        HttpResponse<String> response = request("PUT", "/movies", null, null);
        error(response, 405);
        assertEquals("GET, POST", response.headers().firstValue("Allow").orElse(""));
        error(request("POST", "/movies/1", "{}", "application/json"), 405);
        error(request("GET", "/moviesExtra", null, null), 404);
        error(request("GET", "/movies/1/extra", null, null), 404);
    }
    // проверка на то что неподдерживаемый метод дает 405 с Allow а лишняя часть пути дает 404
}
