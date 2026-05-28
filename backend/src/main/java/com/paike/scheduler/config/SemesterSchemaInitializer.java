package com.paike.scheduler.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 三套 schema 初始化入口的第三套：运行时兜底。
 *
 * 执行时机：CommandLineRunner，在 spring.sql.init 跑完 schema.sql + v2~v7 之后。
 * 职责：兼容旧库（早于某些 v*.sql 就跑过、缺列缺索引的库），不做新数据库初始化。
 *
 * 每个 ensure* 方法与 v*.sql 文件的重叠关系详见 backend/src/main/resources/db/README.md 第 3 节。
 *
 * 维护约定：
 *  - 新表/新列优先写在 v8_*.sql 文件里，不要扩本类
 *  - ensureStage7Tables / ensureStage9Tables 是历史遗留（4 张表无对应 SQL 文件），保留不动
 *  - schedule_locked_item.active_key 在三处定义（本类 CREATE TABLE / 本类 ALTER / v6_bugfix_constraints.sql），
 *    全部幂等结果一致，接受冗余不收敛
 */
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
        ensureStage9Tables();
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
            Integer deletedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_score_detail' " +
                "AND COLUMN_NAME = 'deleted'",
                Integer.class);
            if (deletedCount != null && deletedCount == 0) {
                jdbcTemplate.execute(
                    "ALTER TABLE schedule_score_detail " +
                    "ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除' " +
                    "AFTER created_at");
                log.info("Added deleted column to schedule_score_detail");
            }
            ensureIndex("schedule_score_detail", "idx_score_detail_plan_deleted",
                "CREATE INDEX idx_score_detail_plan_deleted ON schedule_score_detail(plan_id, deleted)");
            ensureIndex("schedule_score_detail", "idx_score_detail_semester_deleted",
                "CREATE INDEX idx_score_detail_semester_deleted ON schedule_score_detail(semester_id, deleted)");
        } catch (Exception e) {
            log.warn("Failed to ensure schedule_score_detail columns: {}", e.getMessage());
        }
    }

    private void ensureStage7Tables() {
        ensureScheduleGenerateLogTable();
        ensureScheduleUnassignedTaskTable();
        ensureScheduleAdjustLogTable();
        ensureScheduleLockedItemTable();
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
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除'
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
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除'
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

    private void ensureScheduleLockedItemTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS schedule_locked_item (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '锁定记录ID',
                    target_type VARCHAR(20) NOT NULL COMMENT '锁定目标类型：PLAN/SCHEDULE',
                    plan_id BIGINT NULL COMMENT '排课方案ID',
                    plan_item_id BIGINT NULL COMMENT '方案明细ID',
                    schedule_id BIGINT NULL COMMENT '正式课表ID',
                    lock_reason VARCHAR(500) NOT NULL COMMENT '锁定原因',
                    active_flag TINYINT NOT NULL DEFAULT 1 COMMENT '是否当前生效',
                    active_key BIGINT GENERATED ALWAYS AS (CASE WHEN active_flag = 1 THEN 0 ELSE NULL END) STORED,
                    unlocked_at DATETIME NULL COMMENT '取消锁定时间',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除'
                ) COMMENT='课程锁定记录表'
                """);
            Integer activeKeyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_locked_item' AND COLUMN_NAME = 'active_key'",
                Integer.class);
            if (activeKeyCount != null && activeKeyCount == 0) {
                jdbcTemplate.execute(
                    "ALTER TABLE schedule_locked_item " +
                        "ADD COLUMN active_key BIGINT GENERATED ALWAYS AS (CASE WHEN active_flag = 1 THEN 0 ELSE NULL END) STORED");
            }
            ensureIndex("schedule_locked_item", "uk_locked_plan_item", "CREATE UNIQUE INDEX uk_locked_plan_item ON schedule_locked_item(plan_item_id, active_key)");
            ensureIndex("schedule_locked_item", "uk_locked_schedule", "CREATE UNIQUE INDEX uk_locked_schedule ON schedule_locked_item(schedule_id, active_key)");
            ensureIndex("schedule_locked_item", "idx_locked_plan", "CREATE INDEX idx_locked_plan ON schedule_locked_item(plan_id)");
            ensureIndex("schedule_locked_item", "idx_locked_plan_item", "CREATE INDEX idx_locked_plan_item ON schedule_locked_item(plan_item_id)");
            ensureIndex("schedule_locked_item", "idx_locked_schedule", "CREATE INDEX idx_locked_schedule ON schedule_locked_item(schedule_id)");
            ensureIndex("schedule_locked_item", "idx_locked_active", "CREATE INDEX idx_locked_active ON schedule_locked_item(active_flag)");
        } catch (Exception e) {
            log.warn("Failed to ensure schedule_locked_item: {}", e.getMessage());
        }
    }

    private void ensureStage9Tables() {
        ensureScheduleReportTable();
    }

    private void ensureScheduleReportTable() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS schedule_report (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '报告ID',
                    plan_id BIGINT NOT NULL COMMENT '排课方案ID',
                    semester_id BIGINT NULL COMMENT '所属学期ID',
                    report_type VARCHAR(40) NOT NULL COMMENT '报告类型',
                    format VARCHAR(20) NOT NULL COMMENT '导出格式',
                    status VARCHAR(20) NOT NULL DEFAULT 'GENERATED' COMMENT '生成状态',
                    include_charts TINYINT NOT NULL DEFAULT 1 COMMENT '是否包含图表',
                    include_risks TINYINT NOT NULL DEFAULT 1 COMMENT '是否包含风险',
                    include_suggestions TINYINT NOT NULL DEFAULT 1 COMMENT '是否包含建议',
                    file_path VARCHAR(500) NOT NULL COMMENT '报告文件路径',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除'
                ) COMMENT='V4 排课分析报告表'
                """);
            Integer semesterIdCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_report' AND COLUMN_NAME = 'semester_id'",
                Integer.class);
            if (semesterIdCount != null && semesterIdCount == 0) {
                jdbcTemplate.execute(
                    "ALTER TABLE schedule_report " +
                        "ADD COLUMN semester_id BIGINT NULL COMMENT '所属学期ID' AFTER plan_id");
            }
            Integer deletedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_report' AND COLUMN_NAME = 'deleted'",
                Integer.class);
            if (deletedCount != null && deletedCount == 0) {
                jdbcTemplate.execute(
                    "ALTER TABLE schedule_report " +
                        "ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除' AFTER updated_at");
            }
            jdbcTemplate.execute("""
                UPDATE schedule_report r
                JOIN schedule_plan p ON p.id = r.plan_id
                SET r.semester_id = p.semester_id
                WHERE r.semester_id IS NULL
                  AND p.semester_id IS NOT NULL
                """);
            ensureIndex("schedule_report", "idx_schedule_report_plan", "CREATE INDEX idx_schedule_report_plan ON schedule_report(plan_id)");
            ensureIndex("schedule_report", "idx_schedule_report_plan_deleted", "CREATE INDEX idx_schedule_report_plan_deleted ON schedule_report(plan_id, deleted)");
            ensureIndex("schedule_report", "idx_schedule_report_semester_deleted_created", "CREATE INDEX idx_schedule_report_semester_deleted_created ON schedule_report(semester_id, deleted, created_at)");
            ensureIndex("schedule_report", "idx_schedule_report_type", "CREATE INDEX idx_schedule_report_type ON schedule_report(report_type)");
            ensureIndex("schedule_report", "idx_schedule_report_created", "CREATE INDEX idx_schedule_report_created ON schedule_report(created_at)");
        } catch (Exception e) {
            log.warn("Failed to ensure schedule_report: {}", e.getMessage());
        }
    }

    private void ensureIndex(String tableName, String indexName, String createSql) {
        try {
            validateSqlIdentifier(tableName);
            validateSqlIdentifier(indexName);
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class,
                tableName,
                indexName);
            if (count != null && count == 0) {
                jdbcTemplate.execute(Objects.requireNonNull(createSql));
            }
        } catch (Exception e) {
            log.warn("Failed to ensure index {} on {}: {}", indexName, tableName, e.getMessage());
        }
    }

    private void validateSqlIdentifier(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + value);
        }
    }
}
