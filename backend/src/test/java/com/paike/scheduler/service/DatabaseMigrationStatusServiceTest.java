package com.paike.scheduler.service;

import com.paike.scheduler.service.vo.MigrationScriptStatusVo;
import com.paike.scheduler.service.vo.MigrationStatusOverviewVo;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationStatusServiceTest {

    @Test
    void getStatus_reportsConfiguredSqlScriptsAndInitializers() {
        DatabaseMigrationStatusService service = new DatabaseMigrationStatusService(
                "classpath:db/schema.sql,classpath:db/v21_performance_baseline_record.sql,classpath:db/missing_stage.sql");

        MigrationStatusOverviewVo status = service.getStatus();

        assertEquals("Spring SQL Init", status.getMigrationTool());
        assertTrue(status.getTotalScriptCount() >= 29);
        assertEquals(3, status.getConfiguredScriptCount());
        assertEquals(1, status.getMissingScriptCount());
        assertTrue(status.getUnconfiguredScriptCount() >= 1);
        assertEquals(2, status.getInitializers().size());

        Map<String, MigrationScriptStatusVo> byPath = status.getScripts().stream()
                .collect(Collectors.toMap(MigrationScriptStatusVo::getResourcePath, script -> script));
        assertEquals("CONFIGURED", byPath.get("db/schema.sql").getStatus());
        assertEquals(1, byPath.get("db/schema.sql").getConfiguredOrder());
        assertEquals("CONFIGURED", byPath.get("db/v21_performance_baseline_record.sql").getStatus());
        assertEquals("MISSING", byPath.get("db/missing_stage.sql").getStatus());
        assertEquals("UNCONFIGURED", byPath.get("db/v2_schema.sql").getStatus());
        assertNotNull(byPath.get("db/v21_performance_baseline_record.sql").getIdempotentHint());
    }
}
