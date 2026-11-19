package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.*;

public class MoviesStore {

    private int currentFreeId;

    private final Set<Movie> movies = new HashSet<>();
    private final HashMap<Integer, Movie> idMap = new HashMap<>();

    /// Вернет ID добавленного фильма или Optional.empty, если фильм уже есть.
    public Optional<Integer> addMovie(Movie movie) {
        if (movies.contains(movie)) {
            return Optional.empty();
        } else {
            int newId = currentFreeId++;
            movies.add(movie);
            idMap.put(newId, movie);
            return Optional.of(newId);
        }
    }

    public Optional<Movie> get(Integer id) {
        return Optional.ofNullable(idMap.get(id));
    }

    public List<Movie> getAllMovies() {
        return movies.stream().toList();
    }

    public boolean remove(Integer id) {
        if (!idMap.containsKey(id)) {
            return false;
        }
        movies.remove(idMap.get(id));
        idMap.remove(id);
        return true;
    }
}