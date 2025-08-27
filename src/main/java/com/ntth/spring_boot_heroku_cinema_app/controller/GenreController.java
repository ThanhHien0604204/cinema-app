package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.dto.GenreRequest;
import com.ntth.spring_boot_heroku_cinema_app.dto.GenreResponse;
import com.ntth.spring_boot_heroku_cinema_app.service.GenreService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/genres")
public class GenreController {
    private final GenreService service;
    public GenreController(GenreService service) { this.service = service; }

    @GetMapping
    public List<GenreResponse> list() { return service.listAll(); }

    @GetMapping("/{id}")
    public GenreResponse get(@PathVariable String id) { return service.getById(id); }

    @GetMapping("/search")
    public List<GenreResponse> search(@RequestParam("q") String q) { return service.searchByName(q); }

    // Admin only
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<GenreResponse> create(@Valid @RequestBody GenreRequest r) {
        GenreResponse created = service.create(r);
        return ResponseEntity.created(URI.create("/api/genres/" + created.getId())).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public GenreResponse update(@PathVariable String id, @Valid @RequestBody GenreRequest r) {
        return service.update(id, r);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
