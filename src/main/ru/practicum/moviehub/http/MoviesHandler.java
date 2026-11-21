package ru.practicum.moviehub.http;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

public class MoviesHandler extends BaseHttpHandler {

    private static final String YEAR_FILTER_KEY = "year";

    private final MoviesStore moviesStore;

    public MoviesHandler(MoviesStore store) {
        moviesStore = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        switch (ex.getRequestMethod().toLowerCase()) {
            case "get" -> processGet(ex);
            case "post" -> processPost(ex);
            case "delete" -> processDelete(ex);
            default -> sendNoContent(ex, 405);
        }
    }

    private void processGet(HttpExchange ex) throws IOException {
        String uri = ex.getRequestURI().getPath().substring(1);
        String[] parsed = uri.split("/");
        if (parsed.length == 1) {
            Map<String, String> requestArguments = getQueryParameters(ex);
            if (requestArguments.isEmpty()) {
                sendAsJson(ex, 200, moviesStore.getAllMovies());
            } else {
                if (requestArguments.containsKey(YEAR_FILTER_KEY)) {
                    Optional<Integer> yearOpt = parseWithNotification(requestArguments.get(YEAR_FILTER_KEY), "Некорректный параметр запроса — 'year'", ex);
                    if (yearOpt.isPresent()) {
                        int year = yearOpt.get();
                        sendAsJson(ex, 200, moviesStore.getAllMovies().stream().filter(movie -> movie.year() == year).toList());
                    }
                } else {
                    sendAsJson(ex, 400, ErrorResponse.of("Неожиданные параметры запроса - " + requestArguments.keySet()));
                }
            }
        } else {
            Optional<Integer> idOpt = parseWithNotification(parsed[1], "Указанный ID не является целым числом", ex);
            if (idOpt.isPresent()) {
                Optional<Movie> movieOpt = moviesStore.get(idOpt.get());
                if (movieOpt.isPresent()) {
                    sendAsJson(ex, 200, movieOpt.get());
                } else {
                    sendAsJson(ex, 404, ErrorResponse.of("Указанного фильма нет в базе"));
                }
            }
        }
    }

    /// Выполняет парсинг переданного аргумента. Если парсинг не удался, отправляет код ошибки 400
    public Optional<Integer> parseWithNotification(
            String argument,
            String errorMessage,
            HttpExchange ex
    ) throws IOException {
        try {
            return Optional.of(Integer.parseInt(argument));
        } catch (NumberFormatException e) {
            sendAsJson(ex, 400, ErrorResponse.of(errorMessage));
            return Optional.empty();
        }
    }

    private Map<String, String> getQueryParameters(HttpExchange exchange) {
        URI uri = exchange.getRequestURI();
        String query = uri.getRawQuery();
        Map<String, String> params = new HashMap<>();

        if (query == null || query.isEmpty()) {
            return params;
        }

        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            String key;
            String value;

            if (idx == -1) {
                key = URLDecoder.decode(pair, StandardCharsets.UTF_8);
                value = "";
            } else {
                key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            }

            params.put(key, value);
        }
        return params;
    }

    private void processPost(HttpExchange ex) throws IOException {
        boolean acceptedJson = Optional.ofNullable(ex.getRequestHeaders().get("Content-Type"))
                .map(List::getFirst)
                .filter(CT_JSON::equals)
                .isPresent();
        if (!acceptedJson) {
            sendNoContent(ex, 415);
            return;
        }

        Movie movie;
        try {
            var jsonElement = JsonParser.parseString(new String(ex.getRequestBody().readAllBytes(), DEFAULT_CHARSET));
            if (!jsonElement.isJsonObject()) {
                sendAsJson(ex, 422, ErrorResponse.of("Ожидался JSON-объект в теле запроса"));
                return;
            }

            movie = gson.fromJson(jsonElement, Movie.class);
        } catch (JsonSyntaxException e) {
            sendAsJson(ex, 422, ErrorResponse.of("Ошибка в формате JSON"));
            return;
        }

        List<String> problems = validate(movie);
        if (!problems.isEmpty()) {
            sendAsJson(ex, 422, new ErrorResponse("Ошибка во входных данных", problems));
            return;
        }

        Optional<Integer> idOpt = moviesStore.addMovie(movie);
        if (idOpt.isPresent()) {
            sendAsJson(ex, 201, MovieWithIdDto.from(movie, idOpt.get()));
        } else {
            sendAsJson(ex, 422, ErrorResponse.of("Не удалось добавить фильм в базу.", "Такой фильм уже существует."));
        }
    }

    private List<String> validate(Movie movie) {
        List<String> problems = new ArrayList<>();
        if (movie.title() == null || movie.title().isEmpty()) {
            problems.add("Название фильма не должно быть пустым");
        } else if (movie.title().length() > 100) {
            problems.add("Название фильма слишком длинное");
        }

        if (movie.year() < 1888) {
            problems.add("Год выпуска фильма не может быть меньше 1888");
        } else if (movie.year() > (LocalDate.now().getYear() + 1)) {
            problems.add("Нельзя добавлять фильмы, которые выйдут через 2 года и позже");
        }
        return problems;
    }

    private void processDelete(HttpExchange ex) throws IOException {
        String uri = ex.getRequestURI().getPath().substring(1);
        String[] parsed = uri.split("/");
        if (parsed.length == 1) {
            sendNoContent(ex, 404);
        } else {
            Optional<Integer> idOpt = parseWithNotification(parsed[1], "Указанный ID не является целым числом", ex);
            if (idOpt.isPresent()) {
                if (moviesStore.remove(idOpt.get())) {
                    sendNoContent(ex, 204);
                } else {
                    sendAsJson(ex, 404, ErrorResponse.of("Указанного фильма нет в базе"));
                }
            }
        }
    }
}