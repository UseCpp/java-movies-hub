package ru.practicum.moviehub.http;

import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.util.List;
import java.util.Optional;

public class ProxyMoviesStore extends MoviesStore {

    public MoviesStore real = new MoviesStore();

    public void clear() {
        real = new MoviesStore();
    }

    @Override
    public Optional<Integer> addMovie(Movie movie) {
        return real.addMovie(movie);
    }

    @Override
    public Optional<Movie> get(Integer id) {
        return real.get(id);
    }

    @Override
    public List<Movie> getAllMovies() {
        return real.getAllMovies();
    }

    @Override
    public boolean remove(Integer id) {
        return real.remove(id);
    }
}
