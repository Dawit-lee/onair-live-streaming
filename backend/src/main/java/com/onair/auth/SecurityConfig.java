package com.onair.auth;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** Meetbowl의 stateless JWT 보안 구성을 단순화해 가져왔다. 프론트는 항상 같은 출처(Caddy/Vite 프록시)라 CORS는 열지 않는다. */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_GET = {
        "/api/health",
        "/api/auth/demo-accounts",
        "/api/broadcasts",
        "/api/broadcasts/*",
        "/api/broadcasts/*/comments",
        "/api/broadcasts/*/donations",
        "/api/broadcasts/*/thumbnail",
        "/api/recordings",
        "/api/recordings/*",
        "/api/recordings/*/video",
        "/api/donations/config"
    };

    private static final String[] PUBLIC_POST = {
        "/api/auth/signup",
        "/api/auth/login",
        "/api/auth/demo-login/*",
        "/api/broadcasts/*/viewer-token",
        "/api/livekit/webhook",
        "/api/donations/webhook"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers(HttpMethod.GET, PUBLIC_GET)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, PUBLIC_POST)
                                        .permitAll()
                                        .requestMatchers("/error")
                                        .permitAll()
                                        .requestMatchers("/api/**")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                                (req, res, ex) ->
                                                        writeError(res, HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."))
                                        .accessDeniedHandler(
                                                (req, res, ex) ->
                                                        writeError(res, HttpStatus.FORBIDDEN, "권한이 없습니다.")))
                .oauth2ResourceServer(
                        o ->
                                o.jwt(Customizer.withDefaults())
                                        .authenticationEntryPoint(
                                                (req, res, ex) ->
                                                        writeError(
                                                                res,
                                                                HttpStatus.UNAUTHORIZED,
                                                                "인증이 만료되었거나 유효하지 않습니다.")))
                .build();
    }

    private static void writeError(
            jakarta.servlet.http.HttpServletResponse res, HttpStatus status, String message)
            throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    @Bean
    SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("app.jwt.secret must be at least 32 bytes");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, @Lazy TokenService tokenService) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(TokenService.ISSUER),
                        jwt ->
                                tokenService.isRevoked(jwt.getId())
                                        ? OAuth2TokenValidatorResult.failure(
                                                new OAuth2Error("invalid_token", "로그아웃된 토큰입니다.", null))
                                        : OAuth2TokenValidatorResult.success()));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
