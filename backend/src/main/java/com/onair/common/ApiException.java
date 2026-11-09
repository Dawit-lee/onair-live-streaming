package com.onair.common;

import org.springframework.http.HttpStatus;

/** 서비스 계층에서 HTTP 상태와 메시지를 함께 던지기 위한 예외. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, what + "을(를) 찾을 수 없습니다.");
    }

    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
