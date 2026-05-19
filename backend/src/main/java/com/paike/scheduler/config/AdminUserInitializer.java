package com.paike.scheduler.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.entity.SysUser;
import com.paike.scheduler.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements CommandLineRunner {

    private static final String PASSWORD_POOL =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SysUserMapper sysUserMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${app.admin.default-password:}")
    private String configuredPassword;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        SysUser existed = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, "admin"));
        if (existed != null) {
            return;
        }

        String password;
        boolean generated;
        if (configuredPassword != null && !configuredPassword.isBlank()) {
            password = configuredPassword;
            generated = false;
        } else {
            password = generateRandomPassword();
            generated = true;
        }

        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRealName("管理员");
        admin.setStatus(1);
        admin.setDeleted(0);
        admin.setCreateTime(LocalDateTime.now());
        admin.setUpdateTime(LocalDateTime.now());
        try {
            sysUserMapper.insert(admin);
        } catch (DuplicateKeyException e) {
            // 多实例并发启动 / CommandLineRunner 偶发并发触发时，两个事务都拿到 selectOne=null，
            // 第二个 insert 会撞 unique(username)。这里直接放过，让另一个实例的种子结果生效。
            log.info("admin 用户在并发初始化中已被另一实例创建，跳过当前 insert。", e);
            return;
        }

        if (generated) {
            // 走 stdout 而非日志文件，避免明文密码持久化到 logs/ 目录
            String banner = "=".repeat(72);
            System.out.println(banner);
            System.out.println("默认管理员账号已创建：admin");
            System.out.println("随机初始密码：" + password);
            System.out.println("请立刻记录并在首次登录后修改！本提示不会再次出现。");
            System.out.println("生产环境请通过环境变量 ADMIN_DEFAULT_PASSWORD 显式指定。");
            System.out.println(banner);
        }
        log.info("默认管理员账号初始化完成（admin）。");
    }

    private static String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(PASSWORD_POOL.charAt(RANDOM.nextInt(PASSWORD_POOL.length())));
        }
        return sb.toString();
    }
}
