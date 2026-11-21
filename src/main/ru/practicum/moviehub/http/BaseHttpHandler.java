package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

abstract class BaseHttpHandler implements HttpHandler {
    protected static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    protected static final String CT_JSON = "application/json; charset=UTF-8";
    protected final Gson gson = new Gson();

    protected void sendAsJson(HttpExchange ex, int status, Object object) throws IOException {
        String json = gson.toJson(object);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        setContentType(ex);

        ex.sendResponseHeaders(status, bytes.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
        ex.close();
    }

    protected void sendNoContent(HttpExchange ex, int code) throws java.io.IOException {
        setContentType(ex);
        ex.sendResponseHeaders(code, -1);
        ex.close();
    }

    private void setContentType(HttpExchange ex) {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
    }
}