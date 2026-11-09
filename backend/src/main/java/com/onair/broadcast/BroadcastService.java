package com.onair.broadcast;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onair.auth.UserRepository;
import com.onair.common.ApiException;
import com.onair.livekit.LiveKitService;
import com.onair.recording.RecordingService;

@Service
public class BroadcastService {
    private final BroadcastRepository broadcasts;
    private final UserRepository users;
    private final LiveKitService liveKit;
    private final RecordingService recordings;
    private final Path thumbnailDir;

    public BroadcastService(
            BroadcastRepository broadcasts,
            UserRepository users,
            LiveKitService liveKit,
            RecordingService recordings,
            @Value("${app.recording.dir:./data/recordings}") String recordingDir) {
        this.broadcasts = broadcasts;
        this.users = users;
        this.liveKit = liveKit;
        this.recordings = recordings;
        // 녹화 볼륨 안에 둬서 컨테이너를 다시 만들어도 유지된다.
        this.thumbnailDir = Path.of(recordingDir, "thumbnails").toAbsolutePath().normalize();
    }

    static final int MAX_THUMBNAIL_BYTES = 2 * 1024 * 1024;

    public record Connection(String livekitUrl, String token, String roomName) {}

    @Transactional(readOnly = true)
    public List<BroadcastResponse> list(BroadcastStatus status) {
        var found =
                status == null
                        ? broadcasts.findAllByOrderByCreatedAtDesc()
                        : broadcasts.findByStatusOrderByCreatedAtDesc(status);
        // 방송 중인 것을 먼저 보여준다 (stable sort라 같은 상태 안에서는 최신순 유지).
        return found.stream()
                .sorted((a, b) -> Boolean.compare(b.getStatus() == BroadcastStatus.LIVE, a.getStatus() == BroadcastStatus.LIVE))
                .map(this::withViewers)
                .toList();
    }

    @Transactional(readOnly = true)
    public BroadcastResponse get(long id) {
        return withViewers(find(id));
    }

    private BroadcastResponse withViewers(Broadcast b) {
        int viewers = b.getStatus() == BroadcastStatus.LIVE ? liveKit.viewerCount(b.getRoomName()) : 0;
        return BroadcastResponse.of(b, viewers);
    }

    /** 송출 화면에서 주기적으로 올리는 라이브 썸네일(JPEG). 소유자만 올릴 수 있다. */
    @Transactional(readOnly = true)
    public void saveThumbnail(long userId, long id, byte[] jpeg) {
        findOwned(userId, id);
        boolean isJpeg = jpeg.length > 3 && (jpeg[0] & 0xFF) == 0xFF && (jpeg[1] & 0xFF) == 0xD8;
        if (!isJpeg || jpeg.length > MAX_THUMBNAIL_BYTES) throw ApiException.badRequest("JPEG 이미지(2MB 이하)만 올릴 수 있습니다.");
        try {
            Files.createDirectories(thumbnailDir);
            Path tmp = thumbnailDir.resolve(id + ".jpg.tmp");
            Files.write(tmp, jpeg);
            Files.move(tmp, thumbnail(id), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path thumbnail(long id) {
        return thumbnailDir.resolve(id + ".jpg");
    }

    @Transactional
    public BroadcastResponse create(long userId, String title, String description, String category) {
        var owner = users.findById(userId).orElseThrow(() -> ApiException.notFound("사용자"));
        return BroadcastResponse.of(broadcasts.save(new Broadcast(owner, title, description, category)));
    }

    @Transactional
    public BroadcastResponse update(long userId, long id, String title, String description, String category) {
        Broadcast b = findOwned(userId, id);
        b.update(title, description, category);
        return BroadcastResponse.of(b);
    }

    @Transactional
    public void delete(long userId, long id) {
        Broadcast b = findOwned(userId, id);
        if (b.getStatus() == BroadcastStatus.LIVE) {
            liveKit.deleteRoom(b.getRoomName());
        }
        recordings.deleteFiles(id);
        try {
            Files.deleteIfExists(thumbnail(id));
        } catch (IOException ignored) {
            // 썸네일 정리 실패는 삭제를 막지 않는다.
        }
        broadcasts.delete(b);
    }

    @Transactional
    public Connection start(long userId, long id, SourceType sourceType) {
        Broadcast b = findOwned(userId, id);
        b.start(sourceType);
        String token =
                liveKit.publisherToken(b.getRoomName(), "owner-" + userId, b.getOwner().getNickname());
        return new Connection(liveKit.publicUrl(), token, b.getRoomName());
    }

    @Transactional
    public BroadcastResponse end(long userId, long id) {
        Broadcast b = findOwned(userId, id);
        String room = b.getRoomName();
        if (b.end()) {
            liveKit.deleteRoom(room);
            recordings.finishAll(id);
        }
        return BroadcastResponse.of(b);
    }

    /** 비로그인 사용자도 시청할 수 있도록 구독 전용 토큰을 발급한다. */
    @Transactional(readOnly = true)
    public Connection viewerToken(long id) {
        Broadcast b = find(id);
        if (b.getStatus() != BroadcastStatus.LIVE) {
            throw ApiException.conflict("방송 중이 아닙니다.");
        }
        String identity = "viewer-" + UUID.randomUUID();
        return new Connection(liveKit.publicUrl(), liveKit.viewerToken(b.getRoomName(), identity), b.getRoomName());
    }

    /** 웹훅: 송출자가 방을 나가거나(탭 닫기, 네트워크 끊김) 방이 사라지면 방송을 종료 상태로 바꾼다. */
    @Transactional
    public void onRoomEvent(String event, String roomName, String identity) {
        boolean publisherLeft = "participant_left".equals(event) && identity != null && identity.startsWith("owner-");
        if (!publisherLeft && !"room_finished".equals(event)) return;
        broadcasts.findByRoomName(roomName).ifPresent(b -> {
            if (!b.end()) return;
            recordings.finishAll(b.getId());
            if (publisherLeft) liveKit.deleteRoom(roomName);
        });
    }

    Broadcast find(long id) {
        return broadcasts.findById(id).orElseThrow(() -> ApiException.notFound("방송"));
    }

    /** 수정, 삭제, 송출 제어 공통: 존재 확인 후 소유자가 아니면 403. */
    public Broadcast findOwned(long userId, long id) {
        Broadcast b = find(id);
        if (!b.isOwnedBy(userId)) throw ApiException.forbidden();
        return b;
    }
}
