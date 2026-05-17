package com.paike.scheduler.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paike.scheduler.auth.dto.LoginRequest;
import com.paike.scheduler.auth.vo.LoginResponse;
import com.paike.scheduler.auth.vo.UserInfoVo;
import com.paike.scheduler.common.exception.BusinessException;
import com.paike.scheduler.entity.SysUser;
import com.paike.scheduler.mapper.SysUserMapper;
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

    public LoginResponse login(LoginRequest request) {
        // 登录限流：同一用户名每分钟最多 5 次尝试
        if (rateLimiterService.isRateLimited("login:" + request.getUsername(), 5, 60_000)) {
            throw new BusinessException(429, "登录尝试过于频繁，请 1 分钟后再试");
        }

        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, request.getUsername()));

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(403, "账号已停用，无法登录");
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
