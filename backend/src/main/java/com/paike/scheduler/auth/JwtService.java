package com.paike.scheduler.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private static final String DEFAULT_SECRET = "replace_with_a_strong_secret_key_for_stage3_v1_auth";

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        if (secret == null || secret.isBlank() || DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                "JWT_SECRET 未配置或仍为默认占位值；拒绝启动。请通过环境变量 JWT_SECRET 注入一个长度 >= 32 字节的强密钥。"
            );
        }
        int secretLength = secret.getBytes(StandardCharsets.UTF_8).length;
        if (secretLength < 32) {
            throw new IllegalStateException("JWT_SECRET 长度不足：当前仅 " + secretLength + " 字节，要求至少 32 字节。请配置新的强密钥后重启服务。");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("username", username)
            .issuedAt(now)
            .expiration(expireAt)
            .signWith(secretKey)
            .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
