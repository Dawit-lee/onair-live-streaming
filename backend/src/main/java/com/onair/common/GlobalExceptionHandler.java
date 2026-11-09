package com.onair.common;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 모든 오류를 {"message": "..."} 형태로 통일한다. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        var error = e.getBindingResult().getFieldErrors().stream().findFirst();
        String message =
                error.map(f -> f.getField() + ": " + f.getDefaultMessage()).orElse("잘못된 요청입니다.");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message", "요청 본문 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(Map.of("message", "업로드 크기 제한을 초과했습니다."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unknown(Exception e) {
        // 404, 405, 400(파싱 실패) 같은 Spring 기본 예외는 원래 상태 코드를 유지한다.
        if (e instanceof org.springframework.web.ErrorResponse er) {
            return ResponseEntity.status(er.getStatusCode())
                    .body(Map.of("message", String.valueOf(er.getBody().getDetail())));
        }
        log.error("Unhandled error", e);
        return ResponseEntity.internalServerError().body(Map.of("message", "서버 오류가 발생했습니다."));
    }
}
