# V6_11_后续任务与 Goal 模式计划

更新时间：2026-06-08

## 一、当前基线

当前 `main` 已完成并推送 V6 第一阶段审计日志最小闭环：

1. 后端 `system_audit_log` 表、实体、Mapper、Service、Controller 已存在。
2. 审计查询接口已完成：
   - `GET /api/v6/audit-logs`
   - `GET /api/v6/audit-logs/{id}`
3. 前端 `/v6/audit-logs` 已完成：
   - 菜单：`V6 系统治理 -> 审计日志`
   - 支持操作类型、学期、方案、成功/失败筛选
   - 支持分页列表和详情弹窗
4. P0/P1 审计覆盖已完成：
   - 应用方案、回滚方案
   - 锁定/解除锁定
   - 手动排课新增、删除
   - V4 局部调整
   - V4 创建局部重排方案
   - V5 试算生成、局部重排试算、应用试算方案
   - 自动排课运行
5. 自动排课失败审计已修复，不再记录会被回滚掉的批次 ID。

已验证：

1. `cd D:\paike\backend; mvn test` 通过，208 个用例全绿。
2. `cd D:\paike\frontend; npx vue-tsc -b` 通过。
3. `cd D:\paike\frontend; npx vite build` 通过。
4. 浏览器联调 `/v6/audit-logs` 通过。

## 二、推荐下一阶段

建议 V6 第二阶段优先做“回归测试中心”，原因：

1. 当前 README 已经把 V1-V5 主链路可回归列为 V6 成功标准。
2. 现有项目已经有 `schedule_regression_test` 相近基础，不应直接新建旧文档里的 `regression_test_run` / `regression_test_case_result`。
3. 回归中心能支撑后续审计、性能和一致性治理的验收闭环。

## 三、Goal 模式推荐目标

可以直接使用下面目标：

```text
调查并实现 V6 第二阶段“回归测试中心”的最小可交付版本：先核对现有 schedule_regression_test 及测试脚本/报告能力，避免重复造表；再补齐后端只读查询接口和前端只读页面；完成后跑后端测试、前端类型检查、前端构建和浏览器联调，发现问题及时修复，直到验证通过并合并。
```

## 四、任务拆分

### 任务 1：现状调查

目标：确认回归测试中心应复用什么，而不是按旧 V6 文档从零做。

检查范围：

1. 后端实体、Mapper、Service、Controller 中是否已有 `ScheduleRegressionTest` 或相关能力。
2. `tests/`、`scripts/`、`docs/` 中已有回归测试脚本和报告。
3. `docs/v6/V6_04_API接口设计.md`、`V6_06_回归自动化与性能治理规则设计.md`、`V6_08_测试与验收清单.md` 是否过时。

产出：

1. 写一份调查记录，建议文件：
   - `docs/v6/V6_13_回归测试中心现状调查.md`
2. 明确：
   - 已有能力
   - 缺口
   - 不应重复实现的内容
   - 最小可交付范围

验收：

1. 文档列出证据文件和结论。
2. 没有直接新增表或接口。

### 任务 2：后端最小接口

目标：提供回归测试中心只读数据能力。

建议先做只读接口，不触发真实测试执行：

1. 查询回归记录列表。
2. 查询回归记录详情。
3. 返回状态、用例名、结果、耗时、时间等已有字段。

注意：

1. 优先复用现有表和实体。
2. 如果现有表字段不足，先写清楚缺口；不要贸然大改 schema。
3. 接口命名可参考旧文档，但必须和当前代码实际一致。

验收：

1. 后端新增/调整接口有服务层或控制器测试。
2. `mvn test` 通过。

### 任务 3：前端只读页面

目标：在 V6 菜单下增加回归测试中心只读页面。

建议路径：

```text
/v6/regression-tests
```

功能：

1. 列表展示回归记录。
2. 支持状态筛选。
3. 支持查看详情。
4. 不做运行按钮，不做复杂统计，不做大屏。

验收：

1. 页面可从 `V6 系统治理` 菜单进入。
2. 列表接口返回 200。
3. 详情接口返回 200。
4. 空状态可读。
5. 控制台无错误。

### 任务 4：文档校准

目标：让 V6 文档状态继续跟上实现。

需要更新：

1. `docs/v6/V6_README.md`
2. `docs/v6/V6_04_API接口设计.md`
3. `docs/v6/V6_05_前端页面设计.md`
4. `docs/v6/V6_08_测试与验收清单.md`
5. 新增的调查记录文档

验收：

1. 文档明确“回归测试中心最小只读版”的完成范围。
2. 文档明确哪些旧规划仍未实现。

### 任务 5：验证与合并

验证命令：

```powershell
cd D:\paike\backend
$env:JWT_SECRET="dev_local_secret_please_change_32_chars_minimum"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的MySQL密码"
mvn test
```

```powershell
cd D:\paike\frontend
npx vue-tsc -b
npx vite build
```

浏览器联调：

1. 用户手动启动后端：
   - `cd D:\paike\backend; mvn spring-boot:run`
2. 前端运行：
   - `cd D:\paike\frontend; npm run dev`
3. 打开：
   - `http://127.0.0.1:5173/v6/regression-tests`

合并要求：

1. 工作区干净。
2. 后端测试通过。
3. 前端类型检查通过。
4. 前端构建通过。
5. 浏览器联调通过。
6. 合并到 `main` 后再次确认 `git status --short --branch`。

## 五、暂缓任务

以下任务不要在下一个 Goal 里顺手做：

1. 完整治理大屏。
2. 性能基线趋势图。
3. 数据库迁移治理页。
4. 错误码中心。
5. Flyway/Liquibase 迁移体系切换。
6. 真实一键运行全部回归测试的调度器。
7. V5 修复建议 P2 审计覆盖。

这些任务适合后续独立开 Goal。

## 六、风险提示

1. 旧 V6 文档里的 `regression_test_run` / `regression_test_case_result` 不一定适合当前代码，必须先调查。
2. 旧 E2E 文档里仍可能残留 `admin / 123456`，当前 README 已废弃该默认密码。
3. 后端启动需要显式配置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`。
4. 后端原则上由用户在独立 PowerShell 终端手动启动；AI 不要擅自后台启动 Spring Boot。
5. 如果 Vite dev server 出现 `504 Outdated Optimize Dep`，优先刷新或重启前端 dev server，不要先改代码。
