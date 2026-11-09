package com.onair.livekit;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.nimbusds.jose.util.JSONObjectUtils;
import com.onair.broadcast.BroadcastService;

@RestController
public class LiveKitWebhookController {
    private final LiveKitService liveKit;
    private final BroadcastService broadcasts;

    public LiveKitWebhookController(LiveKitService liveKit, BroadcastService broadcasts) {
        this.liveKit = liveKit;
        this.broadcasts = broadcasts;
    }

    @PostMapping(value = "/api/livekit/webhook", consumes = "*/*")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "Authorization", required = false) String auth, @RequestBody String body)
            throws java.text.ParseException {
        if (!liveKit.verifyWebhook(auth, body)) {
            return ResponseEntity.status(401).build();
        }
        Map<String, Object> event = JSONObjectUtils.parse(body);
        Map<String, Object> room = JSONObjectUtils.getJSONObject(event, "room");
        Map<String, Object> participant = JSONObjectUtils.getJSONObject(event, "participant");
        broadcasts.onRoomEvent(
                JSONObjectUtils.getString(event, "event"),
                room == null ? null : JSONObjectUtils.getString(room, "name"),
                participant == null ? null : JSONObjectUtils.getString(participant, "identity"));
        return ResponseEntity.ok().build();
    }
}
