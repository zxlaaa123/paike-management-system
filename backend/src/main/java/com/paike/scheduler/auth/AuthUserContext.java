package com.paike.scheduler.auth;

import com.paike.scheduler.entity.SysUser;

public final class AuthUserContext {
    private static final ThreadLocal<SysUser> CURRENT_USER = new ThreadLocal<>();

    private AuthUserContext() {
    }

    public static void set(SysUser user) {
        CURRENT_USER.set(user);
    }

    public static SysUser get() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
