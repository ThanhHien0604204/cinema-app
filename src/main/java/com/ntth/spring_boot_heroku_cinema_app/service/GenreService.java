package com.ntth.spring_boot_heroku_cinema_app.service;

import com.ntth.spring_boot_heroku_cinema_app.dto.GenreRequest;
import com.ntth.spring_boot_heroku_cinema_app.dto.GenreResponse;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Genre;
import com.ntth.spring_boot_heroku_cinema_app.repository.GenreRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;


@Service
public class GenreService {
    private final GenreRepository repo;
    public GenreService(GenreRepository repo) { this.repo = repo; }

    public List<GenreResponse> listAll() {
        return repo.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public GenreResponse getById(String id) {
        return toDto(repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Genre not found")));
    }

    public GenreResponse create(GenreRequest r) {
        String name = r.getName().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        if (repo.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Genre name already exists");
        }
        Genre g = new Genre();
        g.setName(name);
        repo.save(g);
        return toDto(g);
    }

    public GenreResponse update(String id, GenreRequest r) {
        Genre g = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Genre not found"));

        String name = r.getName().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        if (!g.getName().equalsIgnoreCase(name) && repo.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Genre name already exists");
        }
        g.setName(name);
        repo.save(g);
        return toDto(g);
    }

    public void delete(String id) {
        if (!repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Genre not found");
        }
        // (tùy chọn) kiểm tra Movie còn tham chiếu genreIds không rồi mới xóa
        repo.deleteById(id);
    }

    public List<GenreResponse> searchByName(String q) {
        String regex = ".*" + java.util.regex.Pattern.quote(q) + ".*";
        return repo.findByNameRegexIgnoreCase(regex).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    private GenreResponse toDto(Genre g) {
        return new GenreResponse(g.getId(), g.getName());
    }
}
