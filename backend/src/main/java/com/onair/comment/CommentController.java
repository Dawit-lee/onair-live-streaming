package com.onair.comment;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.onair.auth.AuthUser;
import com.onair.auth.UserRepository;
import com.onair.broadcast.BroadcastRepository;
import com.onair.common.ApiException;

@RestController
public class CommentController {
    private final CommentRepository comments;
    private final BroadcastRepository broadcasts;
    private final UserRepository users;

    public CommentController(CommentRepository comments, BroadcastRepository broadcasts, UserRepository users) {
        this.comments = comments;
        this.broadcasts = broadcasts;
        this.users = users;
    }

    public record CommentRequest(@NotBlank @Size(max = 500) String content) {}

    public record CommentResponse(
            long id, long broadcastId, long authorId, String authorNickname, String content, Instant createdAt, Instant updatedAt) {
        static CommentResponse of(Comment c) {
            return new CommentResponse(
                    c.getId(), c.getBroadcastId(), c.getAuthor().getId(), c.getAuthor().getNickname(),
                    c.getContent(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    @GetMapping("/api/broadcasts/{broadcastId}/comments")
    @Transactional(readOnly = true)
    public List<CommentResponse> list(@PathVariable long broadcastId) {
        return comments.findTop200ByBroadcastIdOrderByIdDesc(broadcastId).reversed().stream()
                .map(CommentResponse::of)
                .toList();
    }

    @PostMapping("/api/broadcasts/{broadcastId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CommentResponse create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long broadcastId, @Valid @RequestBody CommentRequest req) {
        if (!broadcasts.existsById(broadcastId)) throw ApiException.notFound("방송");
        var author = users.getReferenceById(AuthUser.id(jwt));
        return CommentResponse.of(comments.save(new Comment(broadcastId, author, req.content())));
    }

    @PatchMapping("/api/comments/{id}")
    @Transactional
    public CommentResponse edit(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody CommentRequest req) {
        Comment c = findOwned(jwt, id);
        c.edit(req.content());
        return CommentResponse.of(c);
    }

    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        comments.delete(findOwned(jwt, id));
    }

    /** 수정/삭제는 작성자만 가능하다. */
    private Comment findOwned(Jwt jwt, long id) {
        long userId = AuthUser.id(jwt);
        Comment c = comments.findById(id).orElseThrow(() -> ApiException.notFound("댓글"));
        if (c.getAuthor().getId() != userId) throw ApiException.forbidden();
        return c;
    }
}
