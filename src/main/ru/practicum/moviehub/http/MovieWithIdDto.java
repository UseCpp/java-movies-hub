package ru.practicum.moviehub.http;

import ru.practicum.moviehub.model.Movie;

public record MovieWithIdDto(String title, int year, Integer id) {
    public static MovieWithIdDto from(Movie movie, Integer id) {
        return new MovieWithIdDto(movie.title(), movie.year(), id);
    }
}