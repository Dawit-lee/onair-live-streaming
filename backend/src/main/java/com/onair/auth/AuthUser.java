package com.onair.auth;

import org.springframework.security.oauth2.jwt.Jwt;

import com.onair.common.ApiException;

/** JWT의 subject(사용자 ID)를 꺼내는 헬퍼. 인증이 없으면 401. */
public final class AuthUser {
    private AuthUser() {}

    public static long id(Jwt jwt) {
        if (jwt == null) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return Long.parseLong(jwt.getSubject());
    }

    public static String nickname(Jwt jwt) {
        return jwt.getClaimAsString("nickname");
    }
}
