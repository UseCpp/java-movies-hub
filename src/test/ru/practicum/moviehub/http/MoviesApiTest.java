package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private static final HttpResponse.BodyHandler<String> BODY_HANDLER = HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
    private static final Movie DUMMY_MOVIE = new Movie("тестовый фильм", 1999);
    private static final int PORT = 8080;
    private static final String BASE = "http://localhost:" + PORT;
    private static final String MOVIES_URI_BASE = BASE + "/movies";
    private static final Gson gson = new Gson();
    private static final ListOfMoviesTypeToken token = new ListOfMoviesTypeToken();
    private static MoviesServer server;
    private static HttpClient client;
    private static ProxyMoviesStore proxyStore;

    @BeforeAll
    static void beforeAll() throws IOException {
        proxyStore = new ProxyMoviesStore();
        server = new MoviesServer(proxyStore, PORT);
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    public HttpRequest get(URI uri) {
        return HttpRequest.newBuilder().uri(uri).GET().build();
    }

    @BeforeEach
    public void beforeEach() {
        proxyStore.clear();
    }

    private static void assertJsonContentType(HttpResponse<?> response) {
        String contentTypeHeaderValue = response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");
    }

    private static void assertEmptyJson(HttpResponse<String> response) {
        String body = response.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается пустой JSON-массив");
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> resp = client.send(get(URI.create(MOVIES_URI_BASE)), BODY_HANDLER);

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        assertJsonContentType(resp);
        assertEmptyJson(resp);
    }

    @Test
    void getMovies_whenOneMovieExists_returnOneMovie() throws Exception {
        proxyStore.addMovie(DUMMY_MOVIE);

        HttpResponse<String> resp = client.send(get(URI.create(MOVIES_URI_BASE)), BODY_HANDLER);

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        assertJsonContentType(resp);

        String body = resp.body().trim();
        assertEquals(List.of(DUMMY_MOVIE), gson.fromJson(body, token.getType()));
    }

    @Test
    void getMovies_whenTwoMoviesExist_returnTwoMovies() throws Exception {
        Movie another = new Movie("другой тестовый фильм", 1992);
        proxyStore.addMovie(DUMMY_MOVIE);
        proxyStore.addMovie(another);

        HttpResponse<String> resp = client.send(get(URI.create(MOVIES_URI_BASE)), BODY_HANDLER);

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        assertJsonContentType(resp);

        String body = resp.body().trim();
        assertEquals(proxyStore.getAllMovies(), gson.fromJson(body, token.getType()));
    }

    public HttpRequest postWithJsonContent(URI uri, String requestBody) {
        return post(uri, requestBody, "application/json; charset=UTF-8");
    }

    public HttpRequest post(URI uri, String requestBody, String contentType) {
        return HttpRequest.newBuilder().uri(uri)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
    }

    private static Stream<Arguments> validMovies() {
        return Stream.of(
                Arguments.of(DUMMY_MOVIE, "Фильм 1999 года"),
                Arguments.of(new Movie("Фильм", LocalDate.now().getYear() + 1), "Фильм следующего года")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("validMovies")
    void whenAddNormalMovies_return201(Movie movie, String name) throws Exception {
        HttpRequest request = postWithJsonContent(URI.create(MOVIES_URI_BASE), gson.toJson(movie));
        HttpResponse<String> resp = client.send(request, BODY_HANDLER);
        assertEquals(201, resp.statusCode());
        assertJsonContentType(resp);

        String body = resp.body().trim();
        assertDoesNotThrow(() -> gson.fromJson(body, MovieWithIdDto.class));
    }

    private static Stream<Arguments> badMovies() {
        return Stream.of(
                Arguments.of(new Movie("", 1999), "Фильм без названия"),
                Arguments.of(new Movie(String.valueOf('_').repeat(10_000), 1999), "Длинное название фильма"),
                Arguments.of(new Movie("Фильм", 1000), "Год выхода фильма раньше 1888"),
                Arguments.of(new Movie("Фильм", LocalDate.now().getYear() + 2), "Фильм, который выходит через 2 года")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("badMovies")
    void whenAddBadMovies_return422(Movie movie, String name) throws Exception {
        HttpRequest request = postWithJsonContent(URI.create(MOVIES_URI_BASE), gson.toJson(movie));
        HttpResponse<String> resp = client.send(request, BODY_HANDLER);
        assertEquals(422, resp.statusCode());
        assertJsonContentType(resp);

        String body = resp.body().trim();
        assertDoesNotThrow(() -> gson.fromJson(body, ErrorResponse.class));
    }

    @Test
    void whenAddDuplicateMovie_return422() throws Exception {
        Movie movie = new Movie("тестовый фильм", 1999);
        HttpRequest request = postWithJsonContent(URI.create(MOVIES_URI_BASE), gson.toJson(movie));
        HttpResponse<String> resp = client.send(request, BODY_HANDLER);

        assertEquals(201, resp.statusCode());

        resp = client.send(request, BODY_HANDLER);
        assertEquals(422, resp.statusCode());
    }


    @Test
    void whenGetExistedId_thenReturnMovie() throws Exception {
        int id = proxyStore.addMovie(DUMMY_MOVIE).get();
        HttpResponse<String> resp =
                client.send(get(URI.create(MOVIES_URI_BASE + "/" + id)), BODY_HANDLER);

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        assertJsonContentType(resp);

        String body = resp.body().trim();
        assertEquals(DUMMY_MOVIE, gson.fromJson(body, Movie.class));
    }

    @Test
    void whenGetNotExistedMovie_thenReturn404() throws Exception {
        HttpResponse<String> resp =
                client.send(get(URI.create(MOVIES_URI_BASE + "/100")), BODY_HANDLER);

        assertEquals(404, resp.statusCode());
    }

    @Test
    void whenGetBadId_thenReturn400() throws Exception {
        HttpResponse<String> resp =
                client.send(get(URI.create(MOVIES_URI_BASE + "/trash")), BODY_HANDLER);

        assertEquals(400, resp.statusCode());
    }


    public HttpRequest delete(URI uri) {
        return HttpRequest.newBuilder().uri(uri).DELETE().build();
    }

    @Test
    void whenDeleteExistedId_thenReturn204() throws Exception {
        int id = proxyStore.addMovie(DUMMY_MOVIE).get();
        HttpResponse<String> resp =
                client.send(delete(URI.create(MOVIES_URI_BASE + "/" + id)), BODY_HANDLER);

        assertEquals(204, resp.statusCode());
    }

    @Test
    void whenDeleteNotExistedId_thenReturn404() throws Exception {
        HttpResponse<String> resp =
                client.send(delete(URI.create(MOVIES_URI_BASE + "/1000")), BODY_HANDLER);

        assertEquals(404, resp.statusCode());
    }

    @Test
    void whenDeleteAlreadyDeletedId_thenReturn404() throws Exception {
        int id = proxyStore.addMovie(DUMMY_MOVIE).get();
        HttpResponse<String> resp =
                client.send(delete(URI.create(MOVIES_URI_BASE + "/" + id)), BODY_HANDLER);

        assertEquals(204, resp.statusCode());

        resp = client.send(delete(URI.create(MOVIES_URI_BASE + "/" + id)), BODY_HANDLER);
        assertEquals(404, resp.statusCode());
    }

    @Test
    void whenGetMoviesByCorrectData_thenReturnArray() throws Exception {
        Movie movie1 = new Movie("Name1", 1999);
        Movie movie2 = new Movie("Name2", 1999);
        Movie movie3 = new Movie("Name3", 2000);
        proxyStore.addMovie(movie1);
        proxyStore.addMovie(movie2);
        proxyStore.addMovie(movie3);

        HttpResponse<String> resp = client.send(get(URI.create(BASE + "/movies?year=1999")), BODY_HANDLER);
        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        assertJsonContentType(resp);

        String body = resp.body().trim();
        assertEquals(List.of(movie1, movie2), gson.fromJson(body, token.getType()));
    }

    @Test
    void whenGetMoviesByCorrectData_andThereAreNoMovies_thenReturnEmptyArray() throws Exception {
        HttpResponse<String> resp = client.send(get(URI.create(MOVIES_URI_BASE + "?year=2000")), BODY_HANDLER);
        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        assertJsonContentType(resp);
        assertEmptyJson(resp);
    }

    @Test
    void whenGetMoviesByIncorrectData_thenReturn400() throws Exception {
        HttpResponse<String> resp = client.send(get(URI.create(MOVIES_URI_BASE + "?year=trash")), BODY_HANDLER);
        assertEquals(400, resp.statusCode(), "GET /movies должен вернуть 400");

        client.send(get(URI.create(MOVIES_URI_BASE + "?year=")), BODY_HANDLER);
        assertEquals(400, resp.statusCode(), "GET /movies должен вернуть 400");
    }

    @Test
    void whenGetWithUnexpectedParameters_thenReturn400() throws Exception {
        HttpResponse<String> resp = client.send(get(URI.create(MOVIES_URI_BASE + "?anything=trash")), BODY_HANDLER);
        assertEquals(400, resp.statusCode(), "GET /movies должен вернуть 400");

        client.send(get(URI.create(MOVIES_URI_BASE + "?trash")), BODY_HANDLER);
        assertEquals(400, resp.statusCode(), "GET /movies должен вернуть 400");
    }

    @Test
    void whenIncorrectPostContentType_thenReturn415() throws Exception {
        HttpRequest request = post(URI.create(MOVIES_URI_BASE), "", "trash");
        HttpResponse<String> resp = client.send(request, BODY_HANDLER);
        assertEquals(415, resp.statusCode(), "POST /movies должен вернуть 415");
    }

    @Test
    void whenInvalidJsonInPost_thenReturn422() throws Exception {
        HttpRequest request = postWithJsonContent(URI.create(MOVIES_URI_BASE), "{ \"title: }");
        HttpResponse<String> resp = client.send(request, BODY_HANDLER);
        assertEquals(422, resp.statusCode(), "POST /movies должен вернуть 422");
    }

    @Test
    void whenNullFieldOfMovieName_thenReturn422() throws Exception {
        Movie movie = new Movie(null, 1999);
        HttpRequest request = postWithJsonContent(URI.create(MOVIES_URI_BASE), gson.toJson(movie));
        HttpResponse<String> resp = client.send(request, BODY_HANDLER);

        assertEquals(422, resp.statusCode());
    }

    @Test
    void whenNullFieldOfMovieYear_thenReturn422() throws Exception {
        HttpRequest request = postWithJsonContent(URI.create(MOVIES_URI_BASE), "{\"title\": \"name\"}");
        HttpResponse<String> resp = client.send(request, BODY_HANDLER);
        assertEquals(422, resp.statusCode());
    }

    @Test
    void whenUnsupportedMethodRequest_thenReturn405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(MOVIES_URI_BASE)).HEAD().build();
        HttpResponse<String> resp = client.send(request, BODY_HANDLER);
        assertEquals(405, resp.statusCode());
    }

    @Test
    void whenGetMoviesByUnsupportedFilerCombinations_thenReturn400() throws Exception {
        HttpResponse<String> resp = client.send(get(URI.create(MOVIES_URI_BASE + "?year=1999&anything=12")), BODY_HANDLER);
        assertEquals(400, resp.statusCode(), "GET /movies должен вернуть 400");

        resp = client.send(get(URI.create(MOVIES_URI_BASE + "?year=1999&flag")), BODY_HANDLER);
        assertEquals(400, resp.statusCode(), "GET /movies должен вернуть 400");

        resp = client.send(get(URI.create(MOVIES_URI_BASE + "?wow=flag")), BODY_HANDLER);
        assertEquals(400, resp.statusCode(), "GET /movies должен вернуть 400");
    }
}