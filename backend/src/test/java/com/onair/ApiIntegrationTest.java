package com.onair;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.onair.donation.TossPaymentsClient;

/** 비로그인 / 소유자 / 다른 사용자 별 접근 허용·거절과 핵심 상태 전이를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean TossPaymentsClient toss;

    String owner;
    String other;

    @BeforeEach
    void setUp() throws Exception {
        owner = signup();
        other = signup();
        when(toss.enabled()).thenReturn(true);
        when(toss.clientKey()).thenReturn("test_ck_dummy");
    }

    @Test
    void authFlow_signupLoginMeLogout() throws Exception {
        String name = "u" + UUID.randomUUID().toString().substring(0, 8);
        post("/api/auth/signup", null, "{\"username\":\"" + name + "\",\"password\":\"password1\",\"nickname\":\"n\"}")
                .andExpect(status().isCreated());
        post("/api/auth/login", null, "{\"username\":\"" + name + "\",\"password\":\"wrong-pass\"}")
                .andExpect(status().isUnauthorized());
        String token = JsonPath.read(
                post("/api/auth/login", null, "{\"username\":\"" + name + "\",\"password\":\"password1\"}")
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.accessToken");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name));
        post("/api/auth/logout", token, "").andExpect(status().isNoContent());
        // 로그아웃한 토큰은 서버에서 거부된다.
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void demoLogin_issuesToken() throws Exception {
        post("/api/auth/demo-login/0", null, "").andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value("streamer1"));
    }

    @Test
    void broadcastPermissions() throws Exception {
        // 비로그인: 등록 불가
        post("/api/broadcasts", null, "{\"title\":\"t\"}").andExpect(status().isUnauthorized());

        long id = createBroadcast(owner);

        // 비로그인: 목록/상세 조회 가능
        mvc.perform(get("/api/broadcasts")).andExpect(status().isOk());
        mvc.perform(get("/api/broadcasts/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));

        // 다른 사용자: 수정/삭제/송출 시작/종료 거절
        patch("/api/broadcasts/" + id, other, "{\"title\":\"hack\"}").andExpect(status().isForbidden());
        mvc.perform(delete("/api/broadcasts/" + id).header("Authorization", "Bearer " + other)).andExpect(status().isForbidden());
        post("/api/broadcasts/" + id + "/start", other, "{\"sourceType\":\"SCREEN\"}").andExpect(status().isForbidden());
        post("/api/broadcasts/" + id + "/end", other, "").andExpect(status().isForbidden());
        // 비로그인: 송출 제어 거절
        post("/api/broadcasts/" + id + "/start", null, "{\"sourceType\":\"SCREEN\"}").andExpect(status().isUnauthorized());

        // 방송 전에는 시청 토큰 없음
        post("/api/broadcasts/" + id + "/viewer-token", null, "").andExpect(status().isConflict());

        // 소유자: 수정, 송출 시작
        patch("/api/broadcasts/" + id, owner, "{\"title\":\"new title\"}").andExpect(jsonPath("$.title").value("new title"));
        post("/api/broadcasts/" + id + "/start", owner, "{\"sourceType\":\"CAMERA\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
        post("/api/broadcasts/" + id + "/start", owner, "{\"sourceType\":\"CAMERA\"}").andExpect(status().isConflict());

        // 비로그인 시청 가능 (구독 전용 토큰)
        String viewerToken = JsonPath.read(
                post("/api/broadcasts/" + id + "/viewer-token", null, "").andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(), "$.token");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(viewerToken.split("\\.")[1]));
        org.assertj.core.api.Assertions.assertThat(payload).contains("\"canPublish\":false");

        // 소유자 종료 → ENDED, 시청 토큰 발급 중단
        post("/api/broadcasts/" + id + "/end", owner, "").andExpect(jsonPath("$.status").value("ENDED"));
        post("/api/broadcasts/" + id + "/viewer-token", null, "").andExpect(status().isConflict());

        // 소유자 삭제
        mvc.perform(delete("/api/broadcasts/" + id).header("Authorization", "Bearer " + owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/broadcasts/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void commentPermissions() throws Exception {
        long id = createBroadcast(owner);
        post("/api/broadcasts/" + id + "/comments", null, "{\"content\":\"hi\"}").andExpect(status().isUnauthorized());
        Number commentId = JsonPath.read(
                post("/api/broadcasts/" + id + "/comments", other, "{\"content\":\"hi\"}").andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/broadcasts/" + id + "/comments")).andExpect(jsonPath("$[0].content").value("hi"));

        patch("/api/comments/" + commentId, owner, "{\"content\":\"x\"}").andExpect(status().isForbidden());
        mvc.perform(delete("/api/comments/" + commentId).header("Authorization", "Bearer " + owner)).andExpect(status().isForbidden());
        patch("/api/comments/" + commentId, other, "{\"content\":\"edited\"}").andExpect(jsonPath("$.content").value("edited"));
        mvc.perform(delete("/api/comments/" + commentId).header("Authorization", "Bearer " + other)).andExpect(status().isNoContent());
    }

    @Test
    void donation_confirmIsIdempotentAndVerifiesAmount() throws Exception {
        long id = createBroadcast(owner);
        post("/api/broadcasts/" + id + "/donations/orders", owner, "{\"amount\":5000}").andExpect(status().isBadRequest());

        String orderId = JsonPath.read(
                post("/api/broadcasts/" + id + "/donations/orders", other, "{\"amount\":5000,\"message\":\"화이팅\"}")
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.orderId");

        // 금액 위변조: 토스 호출 전에 거절
        post("/api/donations/confirm", other, confirmBody("pk_1", orderId, 100)).andExpect(status().isBadRequest());
        verify(toss, never()).confirm(any(), any(), anyLong());

        when(toss.confirm("pk_1", orderId, 5000))
                .thenReturn(Map.of("status", "DONE", "orderId", orderId, "totalAmount", 5000));
        when(toss.get("pk_1")).thenReturn(Map.of("status", "DONE", "orderId", orderId, "totalAmount", 5000));

        // 같은 승인 요청 2번 + 같은 웹훅 2번 → 한 번만 적립
        post("/api/donations/confirm", other, confirmBody("pk_1", orderId, 5000)).andExpect(status().isOk());
        post("/api/donations/confirm", other, confirmBody("pk_1", orderId, 5000)).andExpect(status().isOk());
        String webhook = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"paymentKey\":\"pk_1\"}}";
        post("/api/donations/webhook", null, webhook).andExpect(status().isOk());
        post("/api/donations/webhook", null, webhook).andExpect(status().isOk());

        verify(toss, times(1)).confirm(any(), any(), anyLong());
        mvc.perform(get("/api/broadcasts/" + id + "/donations"))
                .andExpect(jsonPath("$.total").value(5000))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void recording_chunksAppendInOrderAndPlayback() throws Exception {
        long id = createBroadcast(owner);
        post("/api/broadcasts/" + id + "/start", owner, "{\"sourceType\":\"SCREEN\"}").andExpect(status().isOk());
        post("/api/broadcasts/" + id + "/recordings", other, "{\"contentType\":\"video/webm\"}").andExpect(status().isForbidden());
        Number recId = JsonPath.read(
                post("/api/broadcasts/" + id + "/recordings", owner, "{\"contentType\":\"video/webm;codecs=vp8,opus\"}")
                        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        chunk(recId, 0, "abc").andExpect(status().isNoContent());
        chunk(recId, 0, "abc").andExpect(status().isNoContent()); // 재전송은 무시
        chunk(recId, 2, "zzz").andExpect(status().isConflict()); // 건너뛴 조각 거절
        chunk(recId, 1, "def").andExpect(status().isNoContent());

        // 방송 종료 시 녹화가 확정된다
        post("/api/broadcasts/" + id + "/end", owner, "").andExpect(status().isOk());
        mvc.perform(get("/api/recordings/" + recId)).andExpect(jsonPath("$.sizeBytes").value(6));
        mvc.perform(get("/api/recordings/" + recId + "/video"))
                .andExpect(status().isOk())
                .andExpect(content().string("abcdef"));
        mvc.perform(get("/api/recordings/" + recId + "/video").header("Range", "bytes=3-"))
                .andExpect(status().isPartialContent())
                .andExpect(content().string("def"));
    }

    private org.springframework.test.web.servlet.ResultActions chunk(Number recId, int seq, String data) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/recordings/" + recId + "/chunks?seq=" + seq)
                .header("Authorization", "Bearer " + owner)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(data.getBytes()));
    }

    private static String confirmBody(String key, String orderId, long amount) {
        return "{\"paymentKey\":\"" + key + "\",\"orderId\":\"" + orderId + "\",\"amount\":" + amount + "}";
    }

    private long createBroadcast(String token) throws Exception {
        Number id = JsonPath.read(
                post("/api/broadcasts", token, "{\"title\":\"방송\",\"category\":\"talk\"}")
                        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private String signup() throws Exception {
        String name = "u" + UUID.randomUUID().toString().substring(0, 8);
        return JsonPath.read(
                post("/api/auth/signup", null, "{\"username\":\"" + name + "\",\"password\":\"password1\",\"nickname\":\"" + name + "\"}")
                        .andReturn().getResponse().getContentAsString(),
                "$.accessToken");
    }

    private org.springframework.test.web.servlet.ResultActions post(String url, String token, String body) throws Exception {
        return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url), token, body);
    }

    private org.springframework.test.web.servlet.ResultActions patch(String url, String token, String body) throws Exception {
        return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url), token, body);
    }

    private org.springframework.test.web.servlet.ResultActions send(MockHttpServletRequestBuilder req, String token, String body)
            throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        return mvc.perform(req.contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
