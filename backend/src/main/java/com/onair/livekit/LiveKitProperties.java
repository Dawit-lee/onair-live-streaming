package com.onair.livekit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param publicUrl 브라우저가 접속할 wss 주소
 * @param apiUrl 백엔드가 Twirp API를 호출할 내부 주소 (예: http://livekit:7880)
 */
@ConfigurationProperties("app.livekit")
public record LiveKitProperties(
        String publicUrl, String apiUrl, String apiKey, String apiSecret, long tokenTtlSeconds) {}
