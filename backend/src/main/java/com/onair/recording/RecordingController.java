package com.onair.recording;

import java.time.Instant;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.onair.auth.AuthUser;

@RestController
public class RecordingController {
    private final RecordingService service;

    public RecordingController(RecordingService service) {
        this.service = service;
    }

    public record StartRequest(String contentType) {}

    public record RecordingResponse(
            long id, long broadcastId, long ownerId, String ownerNickname, String title, String contentType,
            long sizeBytes, RecordingStatus status, Instant startedAt, Instant endedAt, String videoUrl) {
        static RecordingResponse of(Recording r) {
            return new RecordingResponse(
                    r.getId(), r.getBroadcastId(), r.getOwner().getId(), r.getOwner().getNickname(), r.getTitle(),
                    r.getContentType(), r.getSizeBytes(), r.getStatus(), r.getStartedAt(), r.getEndedAt(),
                    "/api/recordings/" + r.getId() + "/video");
        }
    }

    @PostMapping("/api/broadcasts/{broadcastId}/recordings")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RecordingResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable long broadcastId, @RequestBody StartRequest req) {
        return RecordingResponse.of(service.start(AuthUser.id(jwt), broadcastId, req.contentType()));
    }

    @PostMapping(value = "/api/recordings/{id}/chunks", consumes = MediaType.ALL_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void chunk(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestParam int seq, @RequestBody byte[] data) {
        service.append(AuthUser.id(jwt), id, seq, data);
    }

    @PostMapping("/api/recordings/{id}/complete")
    @Transactional
    public RecordingResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return RecordingResponse.of(service.finish(AuthUser.id(jwt), id));
    }

    @GetMapping("/api/recordings")
    @Transactional(readOnly = true)
    public List<RecordingResponse> list() {
        return service.listReady().stream().map(RecordingResponse::of).toList();
    }

    @GetMapping("/api/recordings/{id}")
    @Transactional(readOnly = true)
    public RecordingResponse get(@PathVariable long id) {
        return RecordingResponse.of(service.getReady(id));
    }

    /** Resource 반환 시 Spring MVC가 Range 요청(구간 탐색)을 자동 처리한다. */
    @GetMapping("/api/recordings/{id}/video")
    public ResponseEntity<Resource> video(@PathVariable long id) {
        Recording r = service.getReady(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(r.getContentType()))
                .body(new FileSystemResource(service.file(r)));
    }

    @DeleteMapping("/api/recordings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        service.delete(AuthUser.id(jwt), id);
    }
}
