# 高校排课管理系统

基于 `Spring Boot 3 + Vue 3` 的高校排课管理系统。

当前仓库已经完成 `V1` 与 `V2` 两个阶段的主线功能，覆盖基础数据维护、教学任务管理、手动排课、自动排课、未排任务追踪、冲突报告、课表评分与课表导出等核心流程。

## 项目概览

本项目面向高校排课业务，目标不是做学术型最优解求解器，而是先落地一套可运行、可维护、可演示、可扩展的排课管理系统。

系统当前支持的核心闭环：

```text
基础数据维护
-> 教学任务配置
-> 手动排课 / 自动排课
-> 冲突检测与未排任务追踪
-> 课表查询与导出
-> 课表评分与优化建议
```

## 阶段状态

### V1 已完成

`V1` 解决“能把基础排课系统跑起来”的问题，已覆盖：

- 登录认证
- 教师、班级、教室、课程、时间段等基础数据管理
- 教学任务管理
- 手动排课
- 排课冲突检测
- 班级 / 教师 / 教室课表查询
- 首页统计与基础运行闭环

### V2 已完成

`V2` 在 V1 基础上补齐“排课增强能力”，已覆盖：

- 教师禁排时间管理
- 排课规则管理
- 自动排课批次运行
- 未排任务列表与失败原因追踪
- 排课冲突报告
- 课表评分报告
- Excel 课表导出

### V2 算法定位

当前自动排课采用的是`规则驱动 + 启发式排序`方案，目标是先满足业务可用性和解释性，而不是引入复杂求解器。

当前版本明确不追求：

- 遗传算法
- 模拟退火
- Timefold Solver
- 全局最优排课求解

## 功能矩阵

| 模块 | 当前状态 | 说明 |
| --- | --- | --- |
| 登录认证 | 已完成 | JWT 登录、登录态校验、默认管理员初始化 |
| 基础数据管理 | 已完成 | 教师、班级、教室、课程、时间段 |
| 教学任务管理 | 已完成 | 维护课程-教师-班级任务与周课时 |
| 手动排课 | 已完成 | 创建排课记录、冲突校验 |
| 课表查询 | 已完成 | 班级 / 教师 / 教室三种视角 |
| 教师禁排时间 | 已完成 | 启停禁排规则、参与排课约束 |
| 排课规则 | 已完成 | 教师日上限、班级日上限、上午优先、避免周五下午等 |
| 自动排课 | 已完成 | 批次执行、结果落库 |
| 未排任务追踪 | 已完成 | 记录失败原因类型与详细说明 |
| 冲突报告 | 已完成 | 生成冲突明细与建议 |
| 评分报告 | 已完成 | 生成课表评分、扣分详情、优化建议 |
| Excel 导出 | 已完成 | 班级 / 教师 / 教室课表导出 |

## 技术栈

### 后端

- Java 17
- Spring Boot 3.3.5
- MyBatis Plus 3.5.7
- MySQL
- JWT
- Apache POI
- Maven

### 前端

- Vue 3
- Vite
- TypeScript
- Element Plus
- Vue Router
- Pinia
- Axios

### 测试与辅助

- Playwright
- Node.js 脚本化 API smoke test

## 项目结构

```text
.
├─ backend/                     # Spring Boot 后端
│  ├─ src/main/java/com/paike/scheduler
│  │  ├─ auth/                  # 登录认证与 JWT
│  │  ├─ config/                # CORS、MyBatis、初始化配置
│  │  ├─ controller/            # REST API
│  │  ├─ entity/                # 实体模型
│  │  ├─ mapper/                # MyBatis Plus Mapper
│  │  └─ service/               # 核心业务逻辑
│  └─ src/main/resources
│     ├─ application.yml        # 后端配置
│     └─ db/                    # schema.sql / v2_schema.sql
├─ frontend/                    # Vue 3 前端
│  └─ src/
│     ├─ api/                   # 前端 API 封装
│     ├─ router/                # 路由定义
│     ├─ stores/                # Pinia 状态
│     └─ views/                 # 页面视图
├─ docs/
│  ├─ v1/                       # V1 阶段文档
│  └─ v2/                       # V2 阶段文档
├─ scripts/                     # 辅助脚本
├─ tests/                       # Playwright 测试
├─ CLAUDE.md                    # 项目协作约束
└─ README.md
```

## 主要页面与接口

### 前端页面

- `/login`：登录页
- `/`：首页仪表盘
- `/teachers`：教师管理
- `/classes`：班级管理
- `/classrooms`：教室管理
- `/courses`：课程管理
- `/teaching-tasks`：教学任务管理
- `/teacher-unavailable-times`：教师禁排时间
- `/schedule-rules`：排课规则
- `/schedule`：手动排课
- `/auto-schedule`：自动排课
- `/unscheduled-tasks`：未排任务
- `/schedule-conflict-reports`：冲突报告
- `/schedule-score`：课表评分
- `/timetable/class`：班级课表
- `/timetable/teacher`：教师课表
- `/timetable/classroom`：教室课表

### 关键后端接口

- `/api/auth/login`
- `/api/teachers`
- `/api/classes`
- `/api/classrooms`
- `/api/courses`
- `/api/time-slots`
- `/api/teaching-tasks`
- `/api/teacher-unavailable-times`
- `/api/schedule-rules`
- `/api/schedules`
- `/api/auto-schedule/run`
- `/api/unscheduled-tasks`
- `/api/schedule-conflict-reports/generate`
- `/api/schedule-score/generate`
- `/api/timetables/*/export`
- `/api/health`

## 环境要求

- JDK 17
- Maven 3.9+
- Node.js 18+
- MySQL 8.x

### Java 版本说明

- 项目编译目标为 `Java 17`
- 本地开发使用 `Java 21` 通常也能运行 Spring Boot 3
- 但提交前仍应保证代码不依赖 `Java 21` 独占语法

## 快速启动

### 1. 准备数据库

先创建数据库：

```sql
CREATE DATABASE paike CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

后端启动时会自动执行：

- `classpath:db/schema.sql`
- `classpath:db/v2_schema.sql`

### 2. 配置后端

参考 [backend/.env.example](backend/.env.example)：

```env
DB_URL=jdbc:mysql://127.0.0.1:3306/paike?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=root
DB_PASSWORD=your_password
JWT_SECRET=replace_with_a_strong_secret
JWT_EXPIRATION_MS=86400000
```

默认后端端口来自 [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)：

- `http://127.0.0.1:8090`

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

健康检查：

```text
GET http://127.0.0.1:8090/api/health
```

### 4. 配置前端

参考 [frontend/.env.example](frontend/.env.example)：

```env
VITE_API_BASE_URL=http://127.0.0.1:8090/api
```

如果你使用默认 `Vite proxy` 开发模式，也可以不额外配置环境变量。

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认访问地址：

- `http://127.0.0.1:5173`

## 默认账号

系统会初始化默认管理员：

- 用户名：`admin`
- 密码：`123456`

建议首次登录后尽快修改。

## 测试与验证

### 后端单测

```bash
cd backend
mvn test
```

### API smoke test

```bash
npm run smoke:api
```

### Playwright E2E

```bash
npx playwright test
```

当前仓库中已经包含阶段性测试脚本，例如：

- `tests/stage7.spec.ts`
- `tests/stage9.spec.ts`

## 文档导航

如果你想按阶段理解项目，建议优先阅读：

- [docs/v1/V1_README.md](docs/v1/V1_README.md)
- [docs/v2/V2_README.md](docs/v2/V2_README.md)

更细的设计文档位于：

- `docs/v1/`：V1 PRD、架构、数据库、接口、页面、测试清单
- `docs/v2/`：V2 功能设计、自动排课算法说明、测试清单

## 当前边界与说明

- 当前自动排课已经具备业务可用性，但仍属于规则型实现，不是全局最优求解器
- 当前 README 以“本地开发和阶段交付”为主，不是面向生产部署的运维文档
- 仓库中保留了阶段文档、测试产物和调试文件，便于回溯开发过程

## 后续可扩展方向

- 更细粒度的排课优先级策略
- 更强的自动排课搜索与回溯能力
- 更完整的报表与导出模板
- 更规范的 CI / 自动化测试流水线
- 更接近生产环境的部署文档与容器化方案
