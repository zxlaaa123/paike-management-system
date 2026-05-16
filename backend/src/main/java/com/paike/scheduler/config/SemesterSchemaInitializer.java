package com.paike.scheduler.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SemesterSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        ensureSemesterIndexes();
        ensureTeachingTaskSemesterColumn();
        ensureScheduleSemesterColumns();
        ensureScheduleScoreDetailColumns();
        ensureScheduleRuleWeightUniqueIndex();
        ensureStage7Tables();
    }

    private void ensureSemesterIndexes() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'semester' AND INDEX_NAME = 'idx_semester_current'",
                Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute("CREATE INDEX idx_semester_current ON semester(is_current)");
                log.info("Created index idx_semester_current on semester");
            }
        } catch (Exception e) {
            log.warn("Failed to create idx_semester_current: {}", e.getMessage());
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'semester' AND INDEX_NAME = 'idx_semester_school_year'",
                Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute("CREATE INDEX idx_semester_school_year ON semester(school_year)");
                log.info("Created index idx_semester_school_year on semester");
            }
        } catch (Exception e) {
            log.warn("Failed to create idx_semester_school_year: {}", e.getMessage());
        }
    }

    private void ensureTeachingTaskSemesterColumn() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teaching_task' AND COLUMN_NAME = 'semester_id'",
                Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "ALTER TABLE teaching_task ADD COLUMN semester_id BIGINT NULL COMMENT '所属学期ID' AFTER id");
                log.info("Added semester_id column to teaching_task");
            }
        } catch (Exception e) {
            log.warn("Failed to add semester_id to teaching_task: {}", e.getMessage());
        }
    }

    private void ensureScheduleSemesterColumns() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'semester_id'",
                Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "ALTER TABLE schedule ADD COLUMN semester_id BIGINT NULL COMMENT '所属学期ID' AFTER id");
                log.info("Added semester_id column to schedule");
            }
        } catch (Exception e) {
            log.warn("Failed to add semester_id to schedule: {}", e.getMessage());
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'plan_id'",
                Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "ALTER TABLE schedule ADD COLUMN plan_id BIGINT NULL COMMENT '来源排课方案ID' AFTER batch_id");
                log.info("Added plan_id column to schedule");
            }
        } catch (Exception e) {
            log.warn("Failed to add plan_id to schedule: {}", e.getMessage());
        }
    }

    private void ensureScheduleRuleWeightUniqueIndex() {
        try {
            Integer tableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_rule_weight'",
                Integer.class);
            if (tableExists == null || tableExists == 0) {
                return;
            }

            Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_rule_weight' " +
                "AND INDEX_NAME = 'uk_rule_weight_semester_strategy_rule'",
                Integer.class);
            if (indexCount != null && indexCount > 0) {
                return;
            }

            // 清理历史重复数据，保留最早一条，避免唯一索引创建失败。
            jdbcTemplate.execute(
                "DELETE t1 FROM schedule_rule_weight t1 " +
                "INNER JOIN schedule_rule_weight t2 " +
                "ON t1.semester_id = t2.semester_id " +
                "AND t1.strategy_type = t2.strategy_type " +
                "AND t1.rule_code = t2.rule_code " +
                "AND t1.id > t2.id");

            jdbcTemplate.execute(
                "ALTER TABLE schedule_rule_weight " +
                "ADD CONSTRAINT uk_rule_weight_semester_strategy_rule " +
                "UNIQUE (semester_id, strategy_type, rule_code)");
            log.info("Created unique index uk_rule_weight_semester_strategy_rule on schedule_rule_weight");
        } catch (Exception e) {
            log.warn("Failed to create unique index on schedule_rule_weight: {}", e.getMessage());
        }
    }

    private void ensureScheduleScoreDetailColumns() {
        try {
            Integer tableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_score_detail'",
                Integer.class);
            if (tableExists == null || tableExists == 0) {
                return;
            }

            Integer ruleTypeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_score_detail' " +
                "AND COLUMN_NAME = 'rule_type'",
                Integer.class);
            if (ruleTypeCount != null && ruleTypeCount == 0) {
                jdbcTemplate.execute(
                    "ALTER TABLE schedule_score_detail " +
                    "ADD COLUMN rule_type VARCHAR(20) NOT NULL DEFAULT 'SOFT' COMMENT '规则类型：HARD硬约束、SOFT软约束' " +
                    "AFTER rule_code");
                log.info("Added rule_type column to schedule_score_detail");
            }
        } catch (Exception e) {
            log.warn("Failed to add rule_type to schedule_score_detail: {}", e.getMessage());
        }
    }

    private void ensureStage7Tables() {
        ensureScheduleGenerateLogTable();
        ensureScheduleUnassignedTaskTable();
        ensureScheduleAdjustLogTable();
    }

    private void ensureScheduleGenerateLogTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS schedule_generate_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '生成日志ID',
                    plan_id BIGINT NULL COMMENT '排课方案ID',
                    semester_id BIGINT NOT NULL COMMENT '所属学期ID',
                    teaching_task_id BIGINT NULL COMMENT '教学任务ID',
                    log_level VARCHAR(20) NOT NULL DEFAULT 'INFO' COMMENT '日志级别',
                    log_type VARCHAR(50) NOT NULL COMMENT '日志类型',
                    message VARCHAR(1000) NOT NULL COMMENT '日志内容',
                    step_no INT NULL COMMENT '步骤序号',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
                ) COMMENT='排课生成日志表'
                """);
            ensureIndex("schedule_generate_log", "idx_generate_log_plan", "CREATE INDEX idx_generate_log_plan ON schedule_generate_log(plan_id)");
            ensureIndex("schedule_generate_log", "idx_generate_log_semester", "CREATE INDEX idx_generate_log_semester ON schedule_generate_log(semester_id)");
            ensureIndex("schedule_generate_log", "idx_generate_log_task", "CREATE INDEX idx_generate_log_task ON schedule_generate_log(teaching_task_id)");
            ensureIndex("schedule_generate_log", "idx_generate_log_type", "CREATE INDEX idx_generate_log_type ON schedule_generate_log(log_type)");
        } catch (Exception e) {
            log.warn("Failed to ensure schedule_generate_log: {}", e.getMessage());
        }
    }

    private void ensureScheduleUnassignedTaskTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS schedule_unassigned_task (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '未排任务ID',
                    plan_id BIGINT NOT NULL COMMENT '排课方案ID',
                    semester_id BIGINT NOT NULL COMMENT '所属学期ID',
                    teaching_task_id BIGINT NOT NULL COMMENT '教学任务ID',
                    reason_code VARCHAR(100) NOT NULL COMMENT '未排原因编码',
                    reason_message VARCHAR(1000) NOT NULL COMMENT '未排原因说明',
                    suggestion VARCHAR(1000) NULL COMMENT '处理建议',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
                ) COMMENT='未排任务原因表'
                """);
            ensureIndex("schedule_unassigned_task", "idx_unassigned_plan", "CREATE INDEX idx_unassigned_plan ON schedule_unassigned_task(plan_id)");
            ensureIndex("schedule_unassigned_task", "idx_unassigned_semester", "CREATE INDEX idx_unassigned_semester ON schedule_unassigned_task(semester_id)");
            ensureIndex("schedule_unassigned_task", "idx_unassigned_task", "CREATE INDEX idx_unassigned_task ON schedule_unassigned_task(teaching_task_id)");
            ensureIndex("schedule_unassigned_task", "idx_unassigned_reason", "CREATE INDEX idx_unassigned_reason ON schedule_unassigned_task(reason_code)");
        } catch (Exception e) {
            log.warn("Failed to ensure schedule_unassigned_task: {}", e.getMessage());
        }
    }

    private void ensureScheduleAdjustLogTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS schedule_adjust_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '调整日志ID',
                    plan_id BIGINT NULL COMMENT '排课方案ID',
                    schedule_id BIGINT NULL COMMENT '正式课表ID',
                    semester_id BIGINT NOT NULL COMMENT '所属学期ID',
                    teaching_task_id BIGINT NOT NULL COMMENT '教学任务ID',
                    old_classroom_id BIGINT NULL COMMENT '调整前教室ID',
                    old_weekday INT NULL COMMENT '调整前星期',
                    old_start_period INT NULL COMMENT '调整前开始节次',
                    old_end_period INT NULL COMMENT '调整前结束节次',
                    new_classroom_id BIGINT NULL COMMENT '调整后教室ID',
                    new_weekday INT NULL COMMENT '调整后星期',
                    new_start_period INT NULL COMMENT '调整后开始节次',
                    new_end_period INT NULL COMMENT '调整后结束节次',
                    before_score DECIMAL(6,2) NULL COMMENT '调整前总分',
                    after_score DECIMAL(6,2) NULL COMMENT '调整后总分',
                    conflict_flag TINYINT NOT NULL DEFAULT 0 COMMENT '调整后是否冲突',
                    adjust_reason VARCHAR(500) NULL COMMENT '调整原因',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
                ) COMMENT='手动调整日志表'
                """);
            ensureIndex("schedule_adjust_log", "idx_adjust_plan", "CREATE INDEX idx_adjust_plan ON schedule_adjust_log(plan_id)");
            ensureIndex("schedule_adjust_log", "idx_adjust_schedule", "CREATE INDEX idx_adjust_schedule ON schedule_adjust_log(schedule_id)");
            ensureIndex("schedule_adjust_log", "idx_adjust_semester", "CREATE INDEX idx_adjust_semester ON schedule_adjust_log(semester_id)");
            ensureIndex("schedule_adjust_log", "idx_adjust_task", "CREATE INDEX idx_adjust_task ON schedule_adjust_log(teaching_task_id)");
        } catch (Exception e) {
            log.warn("Failed to ensure schedule_adjust_log: {}", e.getMessage());
        }
    }

    private void ensureIndex(String tableName, String indexName, String createSql) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "' AND INDEX_NAME = '" + indexName + "'",
                Integer.class);
            if (count != null && count == 0) {
                jdbcTemplate.execute(createSql);
            }
        } catch (Exception e) {
            log.warn("Failed to ensure index {} on {}: {}", indexName, tableName, e.getMessage());
        }
    }
}
