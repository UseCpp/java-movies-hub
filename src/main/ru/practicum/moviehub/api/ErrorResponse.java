package ru.practicum.moviehub.api;

import java.util.Arrays;
import java.util.List;

public record ErrorResponse(String error, List<String> details) {
    public static ErrorResponse of(String error) {
        return new ErrorResponse(error, List.of());
    }

    public static ErrorResponse of(String error, String... details) {
        return new ErrorResponse(error, Arrays.asList(details));
    }
}