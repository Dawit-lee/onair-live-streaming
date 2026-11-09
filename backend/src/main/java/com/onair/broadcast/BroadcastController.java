package com.onair.broadcast;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.nio.file.Files;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.onair.auth.AuthUser;

@RestController
@RequestMapping("/api/broadcasts")
public class BroadcastController {
    private final BroadcastService service;

    public BroadcastController(BroadcastService service) {
        this.service = service;
    }

    public record BroadcastRequest(
            @NotBlank @Size(max = 100) String title,
            @Size(max = 1000) String description,
            @Size(max = 30) String category) {}

    public record StartRequest(@NotNull SourceType sourceType) {}

    @GetMapping
    public List<BroadcastResponse> list(@RequestParam(required = false) BroadcastStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    public BroadcastResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BroadcastResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BroadcastRequest req) {
        return service.create(AuthUser.id(jwt), req.title(), req.description(), req.category());
    }

    @PatchMapping("/{id}")
    public BroadcastResponse update(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody BroadcastRequest req) {
        return service.update(AuthUser.id(jwt), id, req.title(), req.description(), req.category());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        service.delete(AuthUser.id(jwt), id);
    }

    @PostMapping("/{id}/start")
    public BroadcastService.Connection start(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody StartRequest req) {
        return service.start(AuthUser.id(jwt), id, req.sourceType());
    }

    @PostMapping("/{id}/end")
    public BroadcastResponse end(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return service.end(AuthUser.id(jwt), id);
    }

    @PostMapping("/{id}/viewer-token")
    public BroadcastService.Connection viewerToken(@PathVariable long id) {
        return service.viewerToken(id);
    }

    @PostMapping(value = "/{id}/thumbnail", consumes = MediaType.ALL_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadThumbnail(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody byte[] jpeg) {
        service.saveThumbnail(AuthUser.id(jwt), id, jpeg);
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<Resource> thumbnail(@PathVariable long id) {
        var file = service.thumbnail(id);
        if (!Files.exists(file)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noCache())
                .body(new FileSystemResource(file));
    }
}
