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
}
