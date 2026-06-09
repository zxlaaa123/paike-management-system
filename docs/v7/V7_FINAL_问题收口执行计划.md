# V7 Final 问题收口执行计划

## 执行原则

1. 先完成剩余深度审查，再开始修复。
2. 审查阶段不改业务代码，只记录有文件和行号证据的问题。
3. 剩余审查单线程执行：优先由 Codex 主线程亲自跑轻量审查；不再默认启用 agent，避免 CPU 压力过高。
4. 全部审查结果合并到 `reports/v7-final-deep-bug-audit-2026-06-09.md`。
5. 审查完成后，再按 P1 -> P2 -> P3 顺序修复。

## 剩余审查范围

| 顺序 | 范围 | 执行方式 | 状态 |
| --- | --- | --- | --- |
| 1 | 后端核心排课/评分/冲突/方案/修复 | Codex 主线程轻量审查 | 已完成并记录 |
| 2 | 后端接口/认证/安全/配置/数据库/SQL | Codex 主线程轻量审查 | 已完成并记录 |
| 3 | 前端 API/router/store/view/component/utils | Codex 主线程轻量审查 | 已完成并记录 |

## 已确认阻塞项

详见 `reports/v7-final-deep-bug-audit-2026-06-09.md`。

当前已确认的 P1：

1. `schedule` 唯一索引缺少 `semester_id`。
2. 应用排课方案时不清理同学期手动排课。
3. 课表查询接口缺少学期边界。
4. `stage7` E2E 删除按钮未绑定测试行。
5. `stage9` E2E 缺少数据清理。

## 审查补充结果

补充审查结果已写入：

- `reports/v7-final-deep-bug-audit-2026-06-09.md`

新增/扩展记录：

1. 课表接口问题扩展到 `ScheduleController` 旧按对象查询接口。
2. SQL 初始化存在 `continue-on-error: true` 掩盖非幂等迁移错误的交付风险。
3. JSON 请求体大小限制依赖 `Content-Length`，直连 Spring Boot 时 chunked 请求需要额外防护。
4. 前端课表页面无学期选择，确认了课表无学期边界的前端侧证据。
5. 头部用户显示可能把非管理员显示为“管理员”。

## 修复阶段划分

### 阶段 1：学期边界与正式课表正确性

目标：先修会影响核心业务正确性的 P1 问题。

修复问题：

1. `schedule` 唯一索引缺少 `semester_id`。
2. 应用排课方案时不清理同学期手动排课。
3. 课表查询接口缺少学期边界。
4. `ScheduleController` 旧按班级/教师/教室查询接口缺少学期边界。
5. 前端课表页面缺少学期选择或当前学期过滤。

验收口径：

1. 不同学期可以复用同一教师、班级、教室、时间段。
2. 应用方案后，同学期正式课表只保留目标方案结果。
3. 班级/教师/教室课表只展示指定学期或当前学期数据。
4. 新增后端回归测试覆盖跨学期场景。

### 阶段 2：E2E 数据隔离与验收入口

目标：修复测试会污染数据、误删数据、验收入口不可复现的问题。

修复问题：

1. `tests/stage7.spec.ts` 删除按钮未绑定测试行，可能删错真实排课。
2. `tests/stage9.spec.ts` 创建业务数据后不清理。
3. `tests/stage6.spec.ts` 基础数据清理不完整。
4. 根目录 `npm test` 仍是失败占位。
5. Playwright `baseURL` 使用 `5174`，README 和测试使用 `5173`。
6. V7 验收缺少统一可复现命令。

验收口径：

1. E2E 每个测试用例或 suite 有独立数据和反向清理。
2. 不再用 `.first()` 删除未定位的业务行。
3. `npm test` 或 `npm run test:acceptance` 可以作为最终验收入口。
4. README、Playwright config、测试常量端口一致。

### 阶段 3：迁移可靠性与安全交付边界

目标：修复交付时容易被质疑的后端配置、迁移、安全边界问题。

修复问题：

1. `spring.sql.init.continue-on-error: true` 掩盖非幂等迁移错误。
2. `v5_stage1.sql`、`v5_stage3.sql`、`v5_stage6.sql` 等脚本存在直接 `ALTER TABLE` / `CREATE INDEX`。
3. 登录 IP 限流直接信任 `X-Forwarded-For` / `X-Real-IP`。
4. JSON 请求体大小限制依赖 `Content-Length`，直连 Spring Boot 时 chunked 请求需要额外说明或防护。

验收口径：

1. 迁移脚本重复执行不会依赖错误吞掉机制。
2. 迁移状态中心能说明当前 schema 关键约束有效。
3. 登录限流的真实 IP 信任边界有配置或文档说明。
4. 生产部署说明明确 nginx/body-size/cookie-secure/CORS 前提。

### 阶段 4：最终交付材料与轻量清理

目标：让 V7 可以正式封版。

修复问题：

1. V6/V7 验收文档与实际脚本不一致。
2. 旧 `reports/` 中失败、空报告和历史 bug 报告容易干扰最终判断。
3. `frontend/src/components/ErrorBoundary.vue` 前端监控 TODO 需要标注为非目标或实现轻量记录。
4. `frontend/src/layout/BaseLayout.vue` 用户名 fallback 会把用户显示成“管理员”。

验收口径：

1. README 只保留真实可复现的最终验收命令。
2. 旧报告归档或标注历史状态。
3. 生成最终验收记录。
4. `git status` 只包含本轮预期文件。

## 修复阶段入口条件

以下条件全部满足后再开始修复：

1. 剩余 3 个审查范围完成或明确放弃。当前：已完成。
2. 审查结果已合并到报告。当前：已完成。
3. 最终 P1/P2/P3 清单已排序。当前：待修复前最终排序。

## 阶段执行记录

### 阶段 1：学期边界与正式课表正确性

状态：已完成，已提交 `17def3a`。

完成修复：

1. 新增 `db/v22_schedule_semester_unique.sql`，将 `schedule` 活跃唯一约束调整为 `(semester_id, time_slot_id, *, active_key)`。
2. `SchedulePlanService.applyPlan` 应用方案前检查同学期正式课表锁定状态，并清理同学期已有正式课表，再写入目标方案结果。
3. `SchedulePlanService.syncAppliedSchedule`、`ScheduleService`、`TimetableService` 查询增加学期边界。
4. `ScheduleController` 旧按对象查询接口和 `TimetableController` 课表/导出接口增加可选 `semesterId`。
5. 前端班级/教师/教室课表页加载当前学期并随查询、导出传递 `semesterId`。
6. 新增/扩展后端回归测试：迁移脚本注册、应用方案清理同学期正式课表、旧查询接口学期过滤、TimetableService 学期过滤。

验证记录：

1. `cd D:\paike\backend; mvn -q -DskipTests compile`：通过。
2. `cd D:\paike\backend; mvn -q "-Dtest=DatabaseSchemaScriptTest,SchedulePlanServiceTest,ScheduleServiceAuditTest,TimetableServiceSemesterBoundaryTest" test`：通过，exit code 0。
3. `cd D:\paike\frontend; npx vue-tsc -b --pretty false`：通过，exit code 0。
4. 未启动长期运行的前端/后端服务；测试后确认无残留 Java/Maven 测试进程。

### 阶段 2：E2E 数据隔离与验收入口

状态：已完成，已提交 `c432038`。

完成修复：

1. 新增 `tests/helpers/e2e-cleanup.ts`，提供按班级反查排课和按资源 ID 幂等删除的清理工具。
2. `tests/stage6.spec.ts` 增加 `afterAll` 兜底清理，原清理测试改为复用完整清理流程，覆盖方案、排课、教学任务、课程、教室、班级、教师。
3. `tests/stage7.spec.ts` 增加 `afterAll` 兜底清理，记录临时教学任务，删除 UI 排课时限定到包含目标课程和教室的表格行，不再点击全局第一个“删除”按钮。
4. `tests/stage9.spec.ts` 增加 `afterAll` 清理，清理课表、教学任务、课程、教室、班级、教师。
5. 根目录 `npm test` 和 `npm run test:acceptance` 改为统一 Playwright 验收入口；保留 `test:v6` 并新增 `test:stage6`、`test:stage7`、`test:stage9`。
6. `playwright.config.ts` 的 `baseURL` 改为 `http://127.0.0.1:5173`，与 README 和测试常量一致。
7. README 验证命令更新为真实可复现的 V7 验收入口。

验证记录：

1. `cd D:\paike; npx playwright test tests/v6-governance.spec.ts tests/stage6.spec.ts tests/stage7.spec.ts tests/stage9.spec.ts --reporter=line --list`：通过，列出 52 个测试。
2. `cd D:\paike; npm test -- --list`：通过，列出 52 个测试，验证根 `npm test` 不再是失败占位。
3. `cd D:\paike; npm run test:acceptance -- --list`：通过，验证统一验收别名可用。
4. `git diff --check`：通过。
5. 未启动长期运行的前端/后端服务；误触发的一次 E2E 因后端未启动仅产生本地 `test-results`，已删除并确认无残留输出目录。

### 阶段 3：迁移可靠性与安全交付边界

状态：已完成，已提交 `59e1ad9`。

完成修复：

1. `spring.sql.init.continue-on-error` 改为 `false`，迁移失败会中断启动。
2. `v5_stage1.sql`、`v5_stage3.sql`、`v5_stage6.sql` 改为 `information_schema` 条件判断 + 存储过程幂等 DDL。
3. `v6_schedule_index.sql`、`v14_missing_v4_v5_tables_and_schedule_keys.sql` 新建 schedule 唯一键时纳入 `semester_id`；`v22` 继续负责修复老库已有旧索引。
4. 登录 IP 限流默认只使用 `remoteAddr`，仅在 `TRUST_FORWARDED_HEADERS=true` 时信任 `X-Forwarded-For` / `X-Real-IP`。
5. `RequestBodySizeLimitFilter` 对无 `Content-Length` 的请求先限量缓存 body，超过上限直接返回统一 413。
6. README 和 `db/README.md` 更新迁移失败策略、可信代理、Cookie secure、nginx body-size 等交付边界说明。

验证记录：

1. `cd D:\paike\backend; mvn -q "-Dtest=DatabaseSchemaScriptTest,RequestBodySizeLimitFilterTest,AuthControllerClientIpTest" test`：通过，exit code 0。
2. 静态扫描：`continue-on-error: false` 存在，`continue-on-error: true` 不存在；v5_stage1/3/6 无未保护裸 DDL；v6/v14 schedule 唯一键均包含 `semester_id`。
3. `git diff --check`：通过。
4. 未启动长期运行的前端/后端服务；测试后无需要保留的后台进程。

### 阶段 4：最终交付材料与轻量清理

状态：已完成，已随本提交记录。

完成修复：

1. 新增 `docs/v7/V7_FINAL_最终验收记录.md`，记录阶段提交、验证命令和当前验收状态。
2. 新增 `reports/README.md`，标明历史报告与 V7 final 当前判断来源，避免旧失败报告干扰最终判断。
3. `frontend/src/components/ErrorBoundary.vue` 移除监控 TODO，改为将最近一次错误轻量记录到 `sessionStorage`。
4. `frontend/src/layout/BaseLayout.vue` 修复当前登录用户 fallback，不再把缺少真实姓名的用户显示为“管理员”。
5. README 已在阶段 2/3 更新为真实验收入口和生产安全边界。

验证记录：

1. `cd D:\paike\backend; mvn -q "-Dtest=DatabaseSchemaScriptTest,SchedulePlanServiceTest,ScheduleServiceAuditTest,TimetableServiceSemesterBoundaryTest,RequestBodySizeLimitFilterTest,AuthControllerClientIpTest" test`：通过，exit code 0。
2. `cd D:\paike\frontend; npx vue-tsc -b --pretty false`：通过，exit code 0。
3. `cd D:\paike; npm test -- --list`：通过，列出 52 个测试。
4. 未启动长期运行的前端/后端服务；当前无需清理后台服务。
