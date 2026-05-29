package com.paike.scheduler.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class C09LockTransactionOrderInvestigationTest {

    @Test
    void scheduleLockServiceDoesNotUseJvmLocalLocks() throws IOException {
        String source = source("src/main/java/com/paike/scheduler/service/V4ScheduleLockService.java");

        assertFalse(source.contains("synchronized"));
        assertFalse(source.contains("ReentrantLock"));
        assertFalse(source.contains("ConcurrentHashMap"));
        assertFalse(source.contains("java.util.concurrent.locks"));
    }

    @Test
    void lockAndUnlockRunInsideTransactionTemplate() throws IOException {
        String source = source("src/main/java/com/paike/scheduler/service/V4ScheduleLockService.java");

        assertTrue(source.contains("private final TransactionTemplate transactionTemplate;"));
        assertTrue(source.contains("return Objects.requireNonNull(transactionTemplate.execute(status -> lockInternal(request)))"));
        assertTrue(source.contains("return Objects.requireNonNull(transactionTemplate.execute(status -> unlockInternal(request)))"));
    }

    @Test
    void duplicateActiveLocksAreGuardedByDatabaseConstraint() throws IOException {
        String service = source("src/main/java/com/paike/scheduler/service/V4ScheduleLockService.java");
        String v14 = source("src/main/resources/db/v14_missing_v4_v5_tables_and_schedule_keys.sql");
        String v6 = source("src/main/resources/db/v6_bugfix_constraints.sql");
        String initializer = source("src/main/java/com/paike/scheduler/config/SemesterSchemaInitializer.java");

        assertTrue(service.contains("catch (DuplicateKeyException e)"));
        assertTrue(v14.contains("active_key BIGINT GENERATED ALWAYS AS (CASE WHEN active_flag = 1 THEN 0 ELSE NULL END) STORED"));
        assertTrue(v14.contains("UNIQUE KEY uk_locked_plan_item (plan_item_id, active_key)"));
        assertTrue(v14.contains("UNIQUE KEY uk_locked_schedule (schedule_id, active_key)"));
        assertTrue(v6.contains("ADD COLUMN active_key BIGINT GENERATED ALWAYS AS (CASE WHEN active_flag = 1 THEN 0 ELSE NULL END) STORED"));
        assertTrue(v6.contains("ADD UNIQUE KEY uk_locked_plan_item (plan_item_id, active_key)"));
        assertTrue(v6.contains("ADD UNIQUE KEY uk_locked_schedule (schedule_id, active_key)"));
        assertTrue(initializer.contains("CREATE UNIQUE INDEX uk_locked_plan_item ON schedule_locked_item(plan_item_id, active_key)"));
        assertTrue(initializer.contains("CREATE UNIQUE INDEX uk_locked_schedule ON schedule_locked_item(schedule_id, active_key)"));
    }

    private String source(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct, StandardCharsets.UTF_8);
        }

        Path fromRoot = Path.of("backend").resolve(relativePath);
        return Files.readString(fromRoot, StandardCharsets.UTF_8);
    }
}

