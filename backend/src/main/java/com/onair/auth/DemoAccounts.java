package com.onair.auth;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.onair.common.ApiException;

/**
 * 평가용 데모 계정(streamer1, viewer1)을 기동 시 만들어 둔다. app.demo.enabled=false면 비활성화.
 * 데모 로그인은 비밀번호 없이 토큰을 발급하므로 운영 서비스에서는 꺼야 한다.
 */
@Component
public class DemoAccounts {
    private static final List<String[]> ACCOUNTS =
            List.of(new String[] {"streamer1", "데모 방송자"}, new String[] {"viewer1", "데모 시청자"});

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String password;

    public DemoAccounts(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo.enabled:true}") boolean enabled,
            @Value("${app.demo.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.password = password.isBlank() ? UUID.randomUUID().toString() : password;
    }

    @EventListener(ApplicationReadyEvent.class)
    void seed() {
        if (!enabled) return;
        for (String[] a : ACCOUNTS) {
            if (!users.existsByUsername(a[0])) {
                users.save(new User(a[0], passwordEncoder.encode(password), a[1]));
            }
        }
    }

    User find(int index) {
        if (!enabled || index < 0 || index >= ACCOUNTS.size()) {
            throw ApiException.notFound("데모 계정");
        }
        return users.findByUsername(ACCOUNTS.get(index)[0]).orElseThrow(() -> ApiException.notFound("데모 계정"));
    }

    List<Map<String, String>> list() {
        if (!enabled) return List.of();
        return ACCOUNTS.stream().map(a -> Map.of("username", a[0], "nickname", a[1])).toList();
    }
}
