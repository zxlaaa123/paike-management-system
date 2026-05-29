CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码（加密）',
    real_name VARCHAR(50) NOT NULL COMMENT '真实姓名',
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN管理员 USER普通用户',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_sys_user_username (username)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS teacher (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    teacher_no VARCHAR(50) NOT NULL COMMENT '教师编号',
    name VARCHAR(50) NOT NULL COMMENT '教师姓名',
    department VARCHAR(100) DEFAULT NULL COMMENT '所属学院/部门',
    phone VARCHAR(30) DEFAULT NULL COMMENT '联系电话',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_teacher_no (teacher_no)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师表';

CREATE TABLE IF NOT EXISTS class_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    class_name VARCHAR(100) NOT NULL COMMENT '班级名称',
    major VARCHAR(100) DEFAULT NULL COMMENT '专业名称',
    grade VARCHAR(20) DEFAULT NULL COMMENT '年级',
    student_count INT NOT NULL DEFAULT 0 COMMENT '班级人数',
    head_teacher VARCHAR(50) DEFAULT NULL COMMENT '班主任',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_class_name (class_name)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级表';

CREATE TABLE IF NOT EXISTS classroom (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    room_name VARCHAR(100) NOT NULL COMMENT '教室名称',
    building VARCHAR(100) DEFAULT NULL COMMENT '教学楼',
    capacity INT NOT NULL DEFAULT 0 COMMENT '教室容量',
    room_type VARCHAR(30) NOT NULL DEFAULT 'NORMAL' COMMENT '教室类型：NORMAL普通教室 MULTIMEDIA多媒体教室 LAB实验室 COMPUTER机房',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_room_name (room_name)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教室表';

CREATE TABLE IF NOT EXISTS course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    course_no VARCHAR(50) NOT NULL COMMENT '课程编号',
    course_name VARCHAR(100) NOT NULL COMMENT '课程名称',
    course_type VARCHAR(30) NOT NULL DEFAULT 'NORMAL' COMMENT '课程类型：NORMAL普通课 EXPERIMENT实验课 COMPUTER机房课 PE体育课',
    course_nature VARCHAR(50) DEFAULT NULL COMMENT '课程性质',
    total_hours INT NOT NULL DEFAULT 0 COMMENT '总学时',
    weekly_hours INT NOT NULL DEFAULT 0 COMMENT '每周课时',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_course_no (course_no)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';

CREATE TABLE IF NOT EXISTS teaching_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    course_id BIGINT NOT NULL COMMENT '课程ID',
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    class_id BIGINT NOT NULL COMMENT '班级ID',
    weekly_hours INT NOT NULL DEFAULT 0 COMMENT '每周课时',
    need_continuous TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要连续排课：0否 1是',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教学任务表';

CREATE TABLE IF NOT EXISTS time_slot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    day_of_week TINYINT NOT NULL COMMENT '星期几：1周一 2周二 3周三 4周四 5周五',
    period_no TINYINT NOT NULL COMMENT '大节序号：1第1-2节 2第3-4节 3第5-6节 4第7-8节',
    time_label VARCHAR(50) NOT NULL COMMENT '时间段标签，如：周一 第1-2节',
    sort_order TINYINT NOT NULL COMMENT '排序序号：1~20',
    UNIQUE KEY uk_day_period (day_of_week, period_no)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时间段表';

INSERT IGNORE INTO time_slot (day_of_week, period_no, time_label, sort_order) VALUES
(1, 1, '周一 第1-2节', 1),
(1, 2, '周一 第3-4节', 2),
(1, 3, '周一 第5-6节', 3),
(1, 4, '周一 第7-8节', 4),
(2, 1, '周二 第1-2节', 5),
(2, 2, '周二 第3-4节', 6),
(2, 3, '周二 第5-6节', 7),
(2, 4, '周二 第7-8节', 8),
(3, 1, '周三 第1-2节', 9),
(3, 2, '周三 第3-4节', 10),
(3, 3, '周三 第5-6节', 11),
(3, 4, '周三 第7-8节', 12),
(4, 1, '周四 第1-2节', 13),
(4, 2, '周四 第3-4节', 14),
(4, 3, '周四 第5-6节', 15),
(4, 4, '周四 第7-8节', 16),
(5, 1, '周五 第1-2节', 17),
(5, 2, '周五 第3-4节', 18),
(5, 3, '周五 第5-6节', 19),
(5, 4, '周五 第7-8节', 20);

-- V1 schedule table（使用 IF NOT EXISTS 避免重建）
CREATE TABLE IF NOT EXISTS schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    teaching_task_id BIGINT NOT NULL COMMENT '教学任务ID',
    course_id BIGINT NOT NULL COMMENT '课程ID（冗余）',
    teacher_id BIGINT NOT NULL COMMENT '教师ID（冗余）',
    class_id BIGINT NOT NULL COMMENT '班级ID（冗余）',
    time_slot_id BIGINT NOT NULL COMMENT '时间段ID',
    classroom_id BIGINT NOT NULL COMMENT '教室ID',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排课表';
