# V7 Final 项目最终说明

日期：2026-06-09

当前版本：V7 Final

当前状态：最终可用版 / 准交付版

## 结论

高校排课管理系统已完成 V1-V7 主体开发和最终验收。当前 `main` 分支可以作为最终版本使用，适合个人项目、课程设计、毕业设计演示和阶段性交付。

V7 不建议继续扩展为 V8 功能迭代。后续工作应以稳定交付、演示材料、部署说明和少量产品化补充为主。

## 项目定位

本项目是面向高校教学排课场景的管理系统，覆盖基础数据、教学任务、自动排课、手动排课、方案管理、课表查询、质量分析、修复流程和系统治理。

系统以“当前学期”为主要业务边界，支持按学期隔离教学任务、排课方案、正式课表、统计数据和课表查询结果。

## 已完成范围

### V1-V2 基础能力

1. 教师、班级、教室、课程、时间段等基础数据管理。
2. 教学任务管理。
3. 手动排课和基础冲突检测。
4. 自动排课基础流程。

### V3 排课方案能力

1. 多策略排课方案生成。
2. 排课方案列表、详情、对比和应用。
3. 正式课表与方案课表分离。
4. 应用方案时按学期清理并写入正式课表。

### V4 质量分析

1. 排课质量评分。
2. 冲突、未排任务和治理摘要展示。
3. 分析视图和统计入口。

### V5 修复流程

1. 修复任务管理。
2. 修复建议和候选位置。
3. 局部重排、试算方案和优化对比。
4. 回归测试记录。

### V6 系统治理

1. 审计日志。
2. 回归测试中心。
3. 一致性检查。
4. 性能基线记录。
5. 迁移状态和错误码中心。
6. 登录、JWT、CSRF 和请求体大小限制。

### V7 统计与验收收口

1. 统计展示补全。
2. 首页治理摘要。
3. 性能趋势展示。
4. 课表查询学期边界收口。
5. E2E Cookie 认证统一。
6. 数据库迁移幂等化和最终验收入口收口。

## 技术栈

后端：

- Java 17+
- Spring Boot 3.3.x
- Maven
- MyBatis Plus
- MySQL 8
- JWT + Cookie 认证
- CSRF 防护

前端：

- Vue 3
- Vite
- TypeScript
- Element Plus
- Pinia
- Vue Router
- Axios
- Playwright E2E

## 默认端口

- 后端：`8090`
- 前端：`5173`

## 默认账号

空库首次启动会自动创建管理员账号 `admin`。

为了避免随机密码影响验收或演示，建议启动后端时固定默认密码：

```powershell
$env:APP_ADMIN_DEFAULT_PASSWORD="123456"
```

演示账号：

- 用户名：`admin`
- 密码：`123456`

## 本地启动

### 后端

```powershell
cd D:\paike\backend
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/paike?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="123456"
$env:JWT_SECRET="dev_local_secret_please_change_32_chars_minimum"
$env:COOKIE_SECURE="false"
$env:CORS_ALLOWED_ORIGINS="http://localhost:5173,http://127.0.0.1:5173"
$env:APP_ADMIN_DEFAULT_PASSWORD="123456"
mvn spring-boot:run
```

### 前端

```powershell
cd D:\paike\frontend
npm run dev -- --host 127.0.0.1
```

浏览器访问：

```text
http://127.0.0.1:5173
```

## 数据库说明

默认数据库名：`paike`

当前最终版本已验证：从空库启动时，`spring.sql.init` 注册的迁移脚本可以完整执行。

重建开发库命令：

```powershell
D:\MySQL\bin\mysql.exe -uroot -p123456 -e "DROP DATABASE IF EXISTS paike; CREATE DATABASE paike DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

注意：

1. 该命令会删除 `paike` 库全部数据。
2. 仅推荐在开发库、测试库或演示库使用。
3. 真实业务数据应先备份，再做迁移或清理。

## 验收结果

### 隔离库最终验收

验收库：`paike_v7_acceptance_20260609_2025`

命令：

```powershell
cd D:\paike
npm test
```

结果：

```text
52 passed (3.8m)
```

### 默认库复验

默认库 `paike` 已删除并重建为空库，随后完成后端启动、前端启动、管理员登录和完整 E2E 验收。

命令：

```powershell
cd D:\paike
npm test
```

结果：

```text
52 passed (4.6m)
```

## 最终提交

关键最终收口提交：

```text
36fa83d fix: harden final acceptance migrations
```

当前工作树在最终复验后保持干净。

## 当前完整度判断

当前完成度：90% 到 95%。

已达到：

1. 核心业务流程完整。
2. 默认库可从空库迁移并启动。
3. 前后端联调可用。
4. 完整 E2E 验收通过。
5. 主要历史 bug 已收口。
6. 文档和验收记录已成体系。

未覆盖或不建议继续扩展的部分：

1. 生产级权限体系仍然简化。
2. 真实大规模学校数据压力测试未做。
3. 生产部署、备份恢复和运维监控未正式产品化。
4. UI 细节和异常态还可以继续打磨。

## 后续建议

下一阶段建议命名为：V7 稳定交付阶段。

建议只做以下工作：

1. 整理演示脚本。
2. 准备截图和录屏。
3. 补充部署/启动手册。
4. 整理功能完成度清单。
5. 如需答辩或交付，准备一份项目汇报材料。

不建议继续新增 V8 功能。当前版本已经通过最终验收，继续大改会增加回归风险。

