# V7 Final 最终验收记录

日期：2026-06-09

分支：`main`

## 阶段提交

1. 阶段 1：`17def3a` - `fix: close v7 final stage1 semester boundaries`
2. 阶段 2：`c432038` - `test: stabilize v7 acceptance e2e cleanup`
3. 阶段 3：`59e1ad9` - `fix: harden v7 migration and security boundaries`
4. 阶段 4：本提交 - `docs: finalize v7 closeout records`

## 当前验证

1. 后端目标回归：

```powershell
cd D:\paike\backend
$env:DB_USERNAME="root"
$env:DB_PASSWORD="123456"
$env:JWT_SECRET="dev_local_secret_please_change_32_chars_minimum"
mvn -q "-Dtest=DatabaseSchemaScriptTest,SchedulePlanServiceTest,ScheduleServiceAuditTest,TimetableServiceSemesterBoundaryTest,RequestBodySizeLimitFilterTest,AuthControllerClientIpTest" test
```

结果：通过，exit code 0。

2. 前端类型检查：

```powershell
cd D:\paike\frontend
npx vue-tsc -b --pretty false
```

结果：通过，exit code 0。

3. E2E 验收入口加载：

```powershell
cd D:\paike
npm test -- --list
```

结果：通过，列出 52 个测试。

## 说明

- 本记录的 E2E 使用 `--list` 验证入口和规格文件加载，不启动后端/前端服务。
- 需要完整浏览器验收时，按 README 启动后端 `8090` 和前端 `5173` 后运行 `npm test`。
- 阶段执行明细见 `docs/v7/V7_FINAL_问题收口执行计划.md`。

## 最终实机验收

执行时间：2026-06-09 20:21-20:25

环境：

- 后端：`http://127.0.0.1:8090`
- 前端：`http://127.0.0.1:5173`
- 验收库：`paike_v7_acceptance_20260609_2025`
- 默认管理员：通过 `APP_ADMIN_DEFAULT_PASSWORD=123456` 显式固定，避免空库随机密码影响 E2E 登录。

启动前修复：

1. 将注册到 `spring.sql.init.schema-locations` 的旧 `DELIMITER + CREATE PROCEDURE` 迁移改写为 `information_schema + PREPARE/EXECUTE`，匹配 `continue-on-error: false`。
2. 给 `v6_bugfix_constraints.sql` 和 `v11_soft_delete_three_tables.sql` 增加表存在判断，避免新库在 `v14_missing_v4_v5_tables_and_schedule_keys.sql` 建表前执行早期补丁时失败。
3. 修复两处 E2E 脆弱断言：不再依赖 Element Plus 内部 `.el-alert__title` 类；排课列表页直接进入 `/schedule` 验证页面能力。

完整验收命令：

```powershell
cd D:\paike
npm test
```

结果：通过，`52 passed (3.8m)`，exit code 0。

日志：`C:\Users\zxl\AppData\Local\Temp\paike-final-acceptance-20260609-194513\npm-test-acceptance-3.log`

清理结果：

- 已停止本轮后端进程树：父 PID `13460`，Java PID `15656`。
- 已停止本轮前端进程树：父 PID `14532`，Vite PID `13872`。
- 清理后 `8090` / `5173` 均无监听残留。

默认库复验：

1. 默认本地库 `paike` 已删除并重建为空库。
2. 后端使用默认库 `paike` 启动成功。
3. `APP_ADMIN_DEFAULT_PASSWORD=123456` 固定管理员密码，`admin/123456` 登录成功。
4. 前端 `5173` 启动成功。
5. `cd D:\paike; npm test`：通过，`52 passed (4.6m)`，exit code 0。
6. 复验后已停止本轮后端/前端进程树，`8090` / `5173` 均无监听残留。

说明：

- 最终隔离库验收用于验证迁移和 E2E 在独立环境可复现。
- 默认库复验用于确认日常开发库不再被历史 AI 测试脏数据阻塞。
