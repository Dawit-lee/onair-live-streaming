package com.onair.comment;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    /** 최근 댓글 200개. 화면은 이 목록을 주기적으로 다시 받아 수정/삭제도 반영한다. */
    @EntityGraph(attributePaths = "author")
    List<Comment> findTop200ByBroadcastIdOrderByIdDesc(long broadcastId);
}
