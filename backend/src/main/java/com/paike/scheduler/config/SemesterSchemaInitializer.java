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
}
