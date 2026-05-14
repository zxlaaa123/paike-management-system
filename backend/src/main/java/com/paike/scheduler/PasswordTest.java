package com.paike.scheduler;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordTest {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        // 生成 123456 的正确哈希
        String hash = encoder.encode("123456");
        System.out.println("Generated hash for '123456': " + hash);
        // 验证
        System.out.println("Verify: " + encoder.matches("123456", hash));
    }
}
