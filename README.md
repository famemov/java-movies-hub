# MovieHub

Учебный REST API на Java 21: стандартный HttpServer, Gson 2.10.1 и JUnit 5.
Фильмы хранятся в памяти и исчезают после остановки приложения.

## Запуск

В IntelliJ IDEA запустите `ru.practicum.moviehub.MovieHubApp`.
Сервер слушает `http://localhost:8080`.

Либо из корня проекта (macOS/Linux):

```sh
mkdir -p out/main
javac -encoding UTF-8 -cp 'lib/*' -d out/main $(find src/main -name '*.java')
java -cp 'out/main:lib/*' ru.practicum.moviehub.MovieHubApp
```

## API

Все ответы имеют `Content-Type: application/json; charset=UTF-8`.

| Метод и путь | Результат |
| --- | --- |
| GET /movies | 200, массив фильмов |
| POST /movies | 201, созданный фильм с ID |
| GET /movies/{id} | 200, фильм; 404, если отсутствует |
| DELETE /movies/{id} | 204 без тела; 404, если отсутствует |
| GET /movies?year=2000 | 200, массив фильмов указанного года |

POST принимает JSON с непустым строковым `title` длиной до 100 символов
и целочисленным `year` от 1888 до текущего года + 1 включительно.
ID назначается сервером. Ошибки имеют поле `error`, при валидации также `details`.
Некорректный JSON/ID/параметр запроса возвращает 400, неверные поля — 422,
неподдерживаемый Content-Type — 415, неподдерживаемый метод — 405 с `Allow`.
Фильтр принимает один параметр `year` в том же диапазоне лет.

Примеры для терминала или импорта в Insomnia/Postman:

```sh
curl -i -X POST http://localhost:8080/movies \
  -H 'Content-Type: application/json; charset=UTF-8' \
  -d '{"title":"Начало","year":2010}'
curl -i http://localhost:8080/movies
curl -i 'http://localhost:8080/movies?year=2010'
curl -i http://localhost:8080/movies/1
curl -i -X DELETE http://localhost:8080/movies/1
```

## Тесты

Запустите класс `MoviesApiTest` в IntelliJ IDEA с JUnit 5.
Порт 8080 должен быть свободен. Сервер запускается в `@BeforeAll`,
останавливается в `@AfterAll`, хранилище очищается в `@BeforeEach`.
Тесты отправляют реальные HTTP-запросы и проверяют успешные ответы,
ошибки, граничные значения, UTF-8 и заголовки.
