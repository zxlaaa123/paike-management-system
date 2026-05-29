package com.paike.scheduler.service;

import com.paike.scheduler.config.AdminUserInitializer;
import com.paike.scheduler.entity.ScheduleRuleWeight;
import com.paike.scheduler.entity.SysUser;
import com.paike.scheduler.mapper.ScheduleRuleWeightMapper;
import com.paike.scheduler.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class M49M51ContractClosureTest {

    @Test
    void ruleWeightSingleUpdateKeepsPartialUpdateContract() {
        ScheduleRuleWeightMapper mapper = mock(ScheduleRuleWeightMapper.class);
        ScheduleRuleWeightService service = new ScheduleRuleWeightService(mapper);
        ScheduleRuleWeight rule = new ScheduleRuleWeight();
        rule.setId(1L);
        rule.setRuleCode("SOFT_RULE");
        rule.setRuleType("SOFT");
        rule.setWeight(new BigDecimal("75"));
        rule.setEnabled(1);
        rule.setDescription("旧说明");
        when(mapper.selectById(1L)).thenReturn(rule);

        service.updateWeight(1L, null, 0, "新说明");

        assertEquals(new BigDecimal("75"), rule.getWeight());
        assertEquals(0, rule.getEnabled());
        assertEquals("新说明", rule.getDescription());
        verify(mapper).updateById(rule);
    }

    @Test
    void ruleWeightRequestAndFrontendTypeKeepWeightOptional() throws Exception {
        String controller = source("src/main/java/com/paike/scheduler/controller/ScheduleRuleWeightController.java");
        String frontendApi = source("frontend/src/api/scheduleRuleWeight.ts");

        assertTrue(controller.contains("private BigDecimal weight;"));
        assertFalse(controller.contains("@NotNull\n        private BigDecimal weight;"));
        assertTrue(frontendApi.contains("weight?: number"));
    }

    @Test
    void adminInitializerPrintsRandomPasswordOnlyWhenDefaultPasswordMissing() throws Exception {
        SysUserMapper mapper = mock(SysUserMapper.class);
        BCryptPasswordEncoder encoder = mock(BCryptPasswordEncoder.class);
        when(mapper.selectOne(any())).thenReturn(null);
        when(encoder.encode(anyString())).thenReturn("encoded");
        AdminUserInitializer initializer = new AdminUserInitializer(mapper, encoder);
        setConfiguredPassword(initializer, "");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            initializer.run();
        } finally {
            System.setOut(originalOut);
        }

        String stdout = output.toString(StandardCharsets.UTF_8);
        assertTrue(stdout.contains("随机初始密码："));
        assertTrue(stdout.contains("ADMIN_DEFAULT_PASSWORD"));
        verify(mapper).insert(any(SysUser.class));
    }

    @Test
    void adminInitializerUsesConfiguredPasswordWithoutPrintingRandomPassword() throws Exception {
        SysUserMapper mapper = mock(SysUserMapper.class);
        BCryptPasswordEncoder encoder = mock(BCryptPasswordEncoder.class);
        when(mapper.selectOne(any())).thenReturn(null);
        when(encoder.encode(anyString())).thenReturn("encoded-configured");
        AdminUserInitializer initializer = new AdminUserInitializer(mapper, encoder);
        setConfiguredPassword(initializer, "configured-secret");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            initializer.run();
        } finally {
            System.setOut(originalOut);
        }

        assertFalse(output.toString(StandardCharsets.UTF_8).contains("随机初始密码："));
        verify(encoder).encode("configured-secret");
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(mapper).insert(captor.capture());
        assertEquals("encoded-configured", captor.getValue().getPassword());
    }

    private void setConfiguredPassword(AdminUserInitializer initializer, String value) throws Exception {
        Field field = AdminUserInitializer.class.getDeclaredField("configuredPassword");
        field.setAccessible(true);
        field.set(initializer, value);
    }

    private String source(String relativePath) throws Exception {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct);
        }

        Path fromBackendParent = Path.of("..").resolve(relativePath);
        if (Files.exists(fromBackendParent)) {
            return Files.readString(fromBackendParent);
        }

        Path fromProjectRoot = Path.of("backend").resolve(relativePath);
        if (Files.exists(fromProjectRoot)) {
            return Files.readString(fromProjectRoot);
        }

        return Files.readString(direct);
    }
}
