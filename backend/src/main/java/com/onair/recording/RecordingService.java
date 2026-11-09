package com.onair.recording;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onair.auth.UserRepository;
import com.onair.broadcast.BroadcastRepository;
import com.onair.broadcast.BroadcastStatus;
import com.onair.common.ApiException;

/**
 * 송출 브라우저의 MediaRecorder 조각을 받아 하나의 파일로 이어 붙인다.
 *
 * <p>shortcut: 녹화가 송출자 브라우저에서 이뤄지므로 송출자 회선으로 업로드 대역이 추가로 든다. 서버 측 녹화가 필요하면
 * LiveKit Egress(RoomComposite → 파일/S3)로 교체한다.
 */
@Service
public class RecordingService {
    static final Set<String> ALLOWED_TYPES = Set.of("video/webm", "video/mp4");
    static final int MAX_CHUNK_BYTES = 16 * 1024 * 1024;

    private final RecordingRepository recordings;
    private final BroadcastRepository broadcasts;
    private final UserRepository users;
    private final Path dir;

    public RecordingService(
            RecordingRepository recordings,
            BroadcastRepository broadcasts,
            UserRepository users,
            @Value("${app.recording.dir:./data/recordings}") String dir) {
        this.recordings = recordings;
        this.broadcasts = broadcasts;
        this.users = users;
        this.dir = Path.of(dir).toAbsolutePath().normalize();
    }

    @Transactional
    public Recording start(long userId, long broadcastId, String contentType) {
        String type = contentType == null ? "" : contentType.split(";")[0].trim();
        if (!ALLOWED_TYPES.contains(type)) throw ApiException.badRequest("지원하지 않는 녹화 형식입니다.");
        var b = broadcasts.findById(broadcastId).orElseThrow(() -> ApiException.notFound("방송"));
        if (!b.isOwnedBy(userId)) throw ApiException.forbidden();
        if (b.getStatus() != BroadcastStatus.LIVE) throw ApiException.conflict("방송 중일 때만 녹화할 수 있습니다.");
        Recording r = recordings.save(new Recording(broadcastId, users.getReferenceById(userId), b.getTitle(), type));
        r.assignFile(r.getId() + (type.equals("video/mp4") ? ".mp4" : ".webm"));
        try {
            Files.createDirectories(dir);
            Files.deleteIfExists(file(r));
            Files.createFile(file(r));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return r;
    }

    /** seq는 0부터 증가. 이미 받은 조각(재전송)은 무시하고, 건너뛴 조각은 409로 거절해 파일이 깨지지 않게 한다. */
    @Transactional
    public void append(long userId, long recordingId, int seq, byte[] data) {
        if (data.length == 0 || data.length > MAX_CHUNK_BYTES) throw ApiException.badRequest("조각 크기가 올바르지 않습니다.");
        Recording r = recordings.lockById(recordingId).orElseThrow(() -> ApiException.notFound("녹화"));
        if (r.getOwner().getId() != userId) throw ApiException.forbidden();
        if (r.getStatus() != RecordingStatus.RECORDING) throw ApiException.conflict("이미 완료된 녹화입니다.");
        if (seq < r.getChunkCount()) return;
        if (seq > r.getChunkCount()) throw ApiException.conflict("조각 순서가 맞지 않습니다. expected=" + r.getChunkCount());
        try {
            Files.write(file(r), data, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        r.appended(data.length);
    }

    @Transactional
    public Recording finish(long userId, long recordingId) {
        Recording r = recordings.lockById(recordingId).orElseThrow(() -> ApiException.notFound("녹화"));
        if (r.getOwner().getId() != userId) throw ApiException.forbidden();
        finishOrDiscard(r);
        return r;
    }

    /** 방송 종료 시 송출자가 완료 호출을 못 했어도(탭 닫기) 받은 데까지 확정한다. */
    @Transactional
    public void finishAll(long broadcastId) {
        recordings.findByBroadcastIdAndStatus(broadcastId, RecordingStatus.RECORDING).forEach(this::finishOrDiscard);
    }

    /** 방송 삭제 전에 녹화 파일을 지운다 (DB 행은 FK cascade로 삭제). */
    public void deleteFiles(long broadcastId) {
        recordings.findByBroadcastId(broadcastId).forEach(this::deleteFileQuietly);
    }

    @Transactional
    public void delete(long userId, long recordingId) {
        Recording r = recordings.findById(recordingId).orElseThrow(() -> ApiException.notFound("녹화"));
        if (r.getOwner().getId() != userId) throw ApiException.forbidden();
        recordings.delete(r);
        deleteFileQuietly(r);
    }

    @Transactional(readOnly = true)
    public List<Recording> listReady() {
        return recordings.findByStatusOrderByStartedAtDesc(RecordingStatus.READY);
    }

    @Transactional(readOnly = true)
    public Recording getReady(long id) {
        return recordings.findById(id)
                .filter(r -> r.getStatus() == RecordingStatus.READY)
                .orElseThrow(() -> ApiException.notFound("녹화"));
    }

    public Path file(Recording r) {
        Path p = dir.resolve(r.getFilePath()).normalize();
        if (!p.startsWith(dir)) throw ApiException.badRequest("잘못된 경로입니다.");
        return p;
    }

    private void finishOrDiscard(Recording r) {
        if (r.getChunkCount() == 0) {
            recordings.delete(r);
            deleteFileQuietly(r);
        } else {
            r.finish();
        }
    }

    private void deleteFileQuietly(Recording r) {
        try {
            if (!r.getFilePath().isEmpty()) Files.deleteIfExists(file(r));
        } catch (IOException ignored) {
            // 파일 정리 실패는 응답을 막지 않는다.
        }
    }
}
