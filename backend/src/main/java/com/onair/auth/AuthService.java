package com.onair.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onair.common.ApiException;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @Transactional
    public User signup(String username, String password, String nickname) {
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("이미 사용 중인 아이디입니다.");
        }
        return users.save(new User(username, passwordEncoder.encode(password), nickname));
    }

    @Transactional(readOnly = true)
    public String login(String username, String password) {
        User user =
                users.findByUsername(username)
                        .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."));
        return tokens.issue(user);
    }
}
