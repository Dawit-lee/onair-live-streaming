package com.onair.auth;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.onair.common.ApiException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final TokenService tokenService;
    private final UserRepository users;
    private final DemoAccounts demoAccounts;

    public AuthController(
            AuthService authService,
            TokenService tokenService,
            UserRepository users,
            DemoAccounts demoAccounts) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.users = users;
        this.demoAccounts = demoAccounts;
    }

    public record SignupRequest(
            @NotBlank @Size(min = 4, max = 20) @Pattern(regexp = "^[a-zA-Z0-9_]+$") String username,
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank @Size(max = 20) String nickname) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record TokenResponse(String accessToken, MeResponse user) {}

    public record MeResponse(long id, String username, String nickname) {
        static MeResponse of(User u) {
            return new MeResponse(u.getId(), u.getUsername(), u.getNickname());
        }
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse signup(@Valid @RequestBody SignupRequest req) {
        User user = authService.signup(req.username(), req.password(), req.nickname());
        return new TokenResponse(tokenService.issue(user), MeResponse.of(user));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        String token = authService.login(req.username(), req.password());
        return new TokenResponse(token, MeResponse.of(users.findByUsername(req.username()).orElseThrow()));
    }

    /** 평가용: 비밀번호 입력 없이 데모 계정으로 바로 로그인한다. */
    @PostMapping("/demo-login/{index}")
    public TokenResponse demoLogin(@PathVariable int index) {
        User user = demoAccounts.find(index);
        return new TokenResponse(tokenService.issue(user), MeResponse.of(user));
    }

    @GetMapping("/demo-accounts")
    public List<Map<String, String>> demoAccountList() {
        return demoAccounts.list();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt) {
        tokenService.revoke(jwt);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(AuthUser.id(jwt))
                .map(MeResponse::of)
                .orElseThrow(() -> ApiException.notFound("사용자"));
    }
}
