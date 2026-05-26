package com.paike.scheduler.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.auth.dto.LoginRequest;
import com.paike.scheduler.auth.vo.LoginResponse;
import com.paike.scheduler.auth.vo.UserInfoVo;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.SysUser;
import com.paike.scheduler.mapper.SysUserMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RateLimiterService rateLimiterService;

    /**
     * 用户不存在时的占位哈希，让 BCrypt.matches 仍然执行，
     * 避免根据响应耗时枚举出"用户存在与否"的侧信道。
     */
    private String dummyHash;

    @PostConstruct
    void initDummyHash() {
        this.dummyHash = passwordEncoder.encode("constant_time_dummy_password");
    }

    public LoginResponse login(LoginRequest request, String clientIp) {
        // 双键限流：用户名维度（防单账号撞库）+ IP 维度（防轮换用户名绕过）
        if (rateLimiterService.isRateLimited("login:user:" + request.getUsername(), 5, 60_000)) {
            throw new BusinessException(429, "登录尝试过于频繁，请 1 分钟后再试");
        }
        if (clientIp != null && !clientIp.isBlank()
                && rateLimiterService.isRateLimited("login:ip:" + clientIp, 20, 60_000)) {
            throw new BusinessException(429, "登录尝试过于频繁，请 1 分钟后再试");
        }

        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, request.getUsername()));

        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            passwordEncoder.matches(request.getPassword(), dummyHash);
            throw new BusinessException(401, "用户名或密码错误");
        }

        boolean passwordOk = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!passwordOk) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        String token = jwtService.generateToken(user.getId(), user.getUsername());
        UserInfoVo userInfo = new UserInfoVo(user.getId(), user.getUsername(), user.getRealName());
        return new LoginResponse(token, userInfo);
    }

    public UserInfoVo currentUser() {
        SysUser user = AuthUserContext.get();
        if (user == null) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        return new UserInfoVo(user.getId(), user.getUsername(), user.getRealName());
    }
}
