# V7 Final 最终验收记录

日期：2026-06-09

分支：`feature/v7-final-fixes`

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
