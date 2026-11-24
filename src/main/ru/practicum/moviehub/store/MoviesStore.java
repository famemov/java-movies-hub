package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MoviesStore {
    // LinkedHashMap сохраняет порядок добавления чтобы список фильмов возвращался в том же порядке по этому использовал
    // его а не просто HashMap
    private final Map<Integer, Movie> movies = new LinkedHashMap<>();
    private int nextId = 1;

    public Movie add(String title, int year) {
        Movie movie = new Movie(nextId++, title, year);
        movies.put(movie.getId(), movie);
        return movie;
    }

    public List<Movie> findAll() {
        // Возвращаем копию списка, чтобы снаружи не меняли хранилище.
        return new ArrayList<>(movies.values());
    }

    public List<Movie> findByYear(int year) {
        List<Movie> result = new ArrayList<>();
        for (Movie movie : movies.values()) {
            if (movie.getYear() == year) {
                result.add(movie);
            }
        }
        return result;
    }

    public Movie findById(int id) {
        return movies.get(id);
    }

    public boolean delete(int id) {
        return movies.remove(id) != null;
    }

    public void clear() {
        // Счетчик остается, чтобы старый ID не достался новому фильму.
        movies.clear();
    }
}
