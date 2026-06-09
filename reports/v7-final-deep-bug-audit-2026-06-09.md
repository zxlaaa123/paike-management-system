# V7 final deep bug audit - 2026-06-09

## Scope

- Main thread: repository static audit, backend compile, frontend type check, selected high-risk source verification.
- Subagents: 4 spawned.
  - Completed: tests/scripts/docs audit.
  - Stopped: backend core, backend security/db, frontend audits after long runtime to reduce CPU pressure.
- This report only lists issues with file/line evidence.

## Verification run in this audit

- PASS: `D:\paike\backend` - `mvn -q -DskipTests compile`
- PASS: `D:\paike\frontend` - `npx vue-tsc -b --pretty false`
- Not completed in this audit: full `mvn -q test` + frontend build in one combined run timed out at the MCP wrapper limit.

## P1 - Must fix before calling V7 final

### 1. `schedule` unique keys ignore `semester_id`

Evidence:

- `backend/src/main/resources/db/v6_schedule_index.sql:67`
- `backend/src/main/resources/db/v6_schedule_index.sql:79`
- `backend/src/main/resources/db/v6_schedule_index.sql:91`

Current unique keys:

- `uk_schedule_teacher_slot (time_slot_id, teacher_id, active_key)`
- `uk_schedule_class_slot (time_slot_id, class_id, active_key)`
- `uk_schedule_classroom_slot (time_slot_id, classroom_id, active_key)`

Problem:

Business conflict check filters by task semester (`ScheduleConflictService.java:109-111`, `155-157`), but database unique keys do not include `semester_id`. Same teacher/class/classroom at same time slot in different semesters can pass service validation then fail with duplicate key at insert.

Impact:

Multi-semester use breaks. New semester cannot reuse normal timetable slots for same teacher/classroom/class.

Recommendation:

Replace those unique keys with semester-scoped equivalents:

- `(semester_id, time_slot_id, teacher_id, active_key)`
- `(semester_id, time_slot_id, class_id, active_key)`
- `(semester_id, time_slot_id, classroom_id, active_key)`

Add regression test: create same teacher/classroom/time slot schedules in two different semesters and assert both succeed.

### 2. Applying a schedule plan does not clear existing manual schedules in the same semester

Evidence:

- `backend/src/main/java/com/paike/scheduler/service/SchedulePlanService.java:386-398`
- `backend/src/main/java/com/paike/scheduler/service/SchedulePlanService.java:420-433`
- `backend/src/main/java/com/paike/scheduler/service/ScheduleService.java:111-123`

Problem:

`applyPlanInternal` only deletes schedules whose `planId` belongs to old applied plans. Manual schedules have `planId = null` and `sourceType = MANUAL`, so they remain when a plan is applied.

Impact:

Final schedule list/timetable can contain old manual records plus newly applied plan records. If the manual row does not collide on teacher/class/room time, it silently pollutes the applied result. If it collides, the apply operation can fail at DB unique key.

Recommendation:

Define final semantics:

- If applying a plan means replacing the semester timetable, delete or soft-delete all active `schedule` rows for that `semester_id` before inserting plan rows, after lock checks.
- If manual rows must be preserved, they need explicit source-layer UI and conflict semantics.

Add regression test: create manual schedule, apply plan in same semester, assert active schedule table equals applied plan rows only.

### 3. Timetable and object schedule APIs have no semester boundary

Evidence:

- `backend/src/main/java/com/paike/scheduler/controller/TimetableController.java:24-38`
- `backend/src/main/java/com/paike/scheduler/service/TimetableService.java:151-165`
- `backend/src/main/java/com/paike/scheduler/controller/ScheduleController.java:68-82`
- `backend/src/main/java/com/paike/scheduler/service/ScheduleService.java:176-218`
- `frontend/src/views/timetable/ClassTimetableView.vue:28-55`

Problem:

Class/teacher/classroom timetable endpoints and legacy schedule-by-object endpoints accept only object id. Service queries `schedule` by class/teacher/classroom without `semester_id`.

Impact:

Teacher/class/classroom timetable and schedule object views can aggregate schedules from every semester. In a multi-semester system, exported timetables and object schedule views are not trustworthy.

Recommendation:

Add `semesterId` request param or default to current semester. Filter all timetable queries and exports by semester. Add test covering two semesters with same teacher/classroom.

### 4. Stage 7 E2E can delete the wrong schedule row

Evidence:

- `tests/stage7.spec.ts:441-444`
- `tests/stage7.spec.ts:457-468`

Problem:

Test creates a schedule through API, then a later test clicks `button:has-text("删除").first()` without binding deletion to the created row.

Impact:

Shared/dev database can lose unrelated schedule data. Test can also pass/fail based on row order.

Recommendation:

Store created schedule id and clean through API, or locate the row by unique test data before clicking delete.

### 5. Stage 9 E2E leaves all created business data behind

Evidence:

- `tests/stage9.spec.ts:41-63`
- `tests/stage9.spec.ts:170`

Problem:

Test creates teacher, class, classroom, course, teaching task, and two schedules. No `afterAll` / `afterEach` cleanup exists.

Impact:

Every run pollutes the database. Later tests and manual demos can become false-positive/false-negative.

Recommendation:

Clean in reverse dependency order:

schedule -> teaching task -> course -> classroom -> class -> teacher.

## P2 - Should fix for final delivery quality

### 6. Root `npm test` always fails

Evidence:

- `package.json:10-13`

Problem:

`npm test` is still `echo "Error: no test specified" && exit 1`.

Impact:

Standard test entry fails. CI/new reviewer/final acceptance may mark the project broken.

Recommendation:

Replace with a real command, or add explicit `test:acceptance` and document that as canonical.

### 7. Playwright configured port conflicts with README and tests

Evidence:

- `playwright.config.ts:9`
- `README.md:143`
- `README.md:231`
- `tests/stage9.spec.ts:4-5`

Problem:

Playwright config uses `http://127.0.0.1:5174`; README and tests use `5173`.

Impact:

Relative-url tests will hit a different frontend than documented. Configuration looks stale.

Recommendation:

Use one source of truth: `BASE_URL` env var with default `http://127.0.0.1:5173`, and make Playwright config/tests read it.

### 8. V7 acceptance claims are broader than reproducible scripts

Evidence:

- `docs/v7/V7_06_测试与验收清单.md:42-45`
- `docs/v7/V7_06_测试与验收清单.md:57-61`
- `README.md:226-228`
- `package.json:12-13`

Problem:

Docs say V7 page smoke/home/statistics checks passed, but reproducible scripts only expose `test:v6`, plus README manually runs `stage9`.

Impact:

Final acceptance cannot replay the claimed V7 coverage from one command.

Recommendation:

Add `test:v7` or `test:acceptance` covering V7 homepage stats, performance trend, and key schedule display fields.

### 9. V6 smoke checks are shallow

Evidence:

- `tests/v6-governance.spec.ts:27-58`

Problem:

API smoke mostly checks `code === 200`; page smoke checks titles/no console error.

Impact:

Empty payloads, missing fields, or broken core tables can still pass.

Recommendation:

Add schema/key-field assertions for every V6 governance endpoint and one visible table/card assertion per page.

### 10. Login IP rate limit trusts forwarded headers directly

Evidence:

- `backend/src/main/java/com/paike/scheduler/controller/AuthController.java:96-110`
- `backend/src/main/java/com/paike/scheduler/auth/AuthService.java:35-43`

Problem:

`resolveClientIp` trusts `X-Forwarded-For` and `X-Real-IP`. If the deployment proxy does not strip external headers, clients can spoof IPs and bypass the IP rate limit.

Impact:

Brute-force protection is weaker outside local/dev deployment.

Recommendation:

Only trust forwarded headers behind a known reverse proxy, or gate it behind config. Otherwise use `request.getRemoteAddr()`.

### 11. SQL init masks non-idempotent migration errors

Evidence:

- `backend/src/main/resources/application.yml:30-34`
- `backend/src/main/resources/db/v5_stage1.sql:6-13`
- `backend/src/main/resources/db/v5_stage3.sql:5-17`
- `backend/src/main/resources/db/v5_stage6.sql:5-12`

Problem:

`spring.sql.init.continue-on-error` is `true`, while several migration scripts still contain direct `ALTER TABLE ... ADD COLUMN` / `CREATE INDEX` statements without per-statement existence checks. Re-running on an existing database depends on error masking.

Impact:

Final acceptance can hide real schema failures among expected duplicate-column/index errors. A partially migrated database may start but behave incorrectly.

Recommendation:

Make remaining migration scripts fully idempotent, then make final acceptance include a strict migration/status check. Long term: switch to Flyway/Liquibase.

## P3 - Cleanup / audit trust

### 12. Final delivery contains stale failed/empty artifacts

Evidence:

- `tests/test-results/.last-run.json`
- `reports/test_result.json`
- historical reports under `reports/`

Problem:

Some artifacts indicate prior failed runs or empty report files while V7 docs say acceptance passed.

Impact:

Delivery package looks inconsistent during review.

Recommendation:

Move stale reports to archive, delete empty generated files, regenerate final acceptance log.

### 13. Frontend monitoring TODO remains

Evidence:

- `frontend/src/components/ErrorBoundary.vue:21`

Problem:

`TODO: 接入前端监控（Sentry / 自建 errorLogApi）` remains.

Impact:

Not a functional blocker for V7 final, but confirms production observability is incomplete.

Recommendation:

Either implement lightweight error reporting or mark it explicitly as post-final non-goal.

### 14. JSON request body limit relies on `Content-Length`

Evidence:

- `backend/src/main/java/com/paike/scheduler/config/RequestBodySizeLimitFilter.java:30-31`
- `backend/src/main/java/com/paike/scheduler/config/RequestBodySizeLimitFilter.java:50-55`

Problem:

The application-level JSON body limit only checks `Content-Length`. Chunked requests without `Content-Length` are allowed through, with the comment saying nginx should enforce the outer limit.

Impact:

Acceptable for a proxy-backed deployment. Risky if Spring Boot is exposed directly.

Recommendation:

For final local/demo delivery, document the proxy assumption. For production-style delivery, enforce body size while reading or require nginx `client_max_body_size`.

### 15. Header can show non-admin users as admin

Evidence:

- `frontend/src/layout/BaseLayout.vue:155`

Problem:

The header renders `authStore.userInfo?.realName || '管理员'`. If a logged-in user has blank `realName`, or user info is temporarily unavailable, the UI labels the current account as admin.

Impact:

Low functional risk, but misleading in a system that already has `ADMIN` / `USER` roles.

Recommendation:

Fallback to `authStore.userInfo?.username || '未加载用户'` instead of `管理员`.

## Backend core self-audit pass - 2026-06-09

Scope:

- `backend/src/main/java/com/paike/scheduler/service`
- `backend/src/main/java/com/paike/scheduler/service/scheduling`
- schedule/V4/V5/V6-related controllers and services

Method:

- Static scan for `scheduleMapper` reads/writes missing semester filters.
- Static scan for active schedule inserts missing nearby `semesterId`.
- Static scan for global `selectList(null)` and dynamic `last("LIMIT ...")` candidates.
- Targeted source verification for the returned candidates.

Result:

- Confirmed and expanded P1 issue 3 above: legacy schedule object endpoints also lack semester boundary.
- No new confirmed P0/P1 beyond issues already listed in this report.

## Frontend self-audit pass - 2026-06-09

Scope:

- `frontend/src/api`
- `frontend/src/router`
- `frontend/src/stores`
- `frontend/src/views`
- `frontend/src/components`
- `frontend/src/utils`

Method:

- Static scan for dangerous HTML rendering, hardcoded hosts, auth storage/cookie/router flow, unsafe array access, silent catch blocks, TypeScript escape hatches, download/blob handling, and API request strings.
- Targeted verification for router guard, auth store, report download URL, timetable views, and layout user display.

Result:

- No `v-html` / `innerHTML` / hardcoded localhost host in frontend source.
- Login state flow is consistent with httpOnly cookie + readable CSRF cookie design.
- Report download URL is generated by backend as `/api/v4/schedule-reports/{id}/download`, so no confirmed external URL injection.
- Confirmed frontend side of P1 issue 3: timetable UI has no semester selection and calls semester-blind API.
- Added P3 issue 15: header fallback can label users as admin.

## Backend security/db self-audit pass - 2026-06-09

Scope:

- `backend/src/main/java/com/paike/scheduler/auth`
- `backend/src/main/java/com/paike/scheduler/config`
- `backend/src/main/java/com/paike/scheduler/common`
- `backend/src/main/resources/db`
- mapper XML and backend application configuration

Method:

- Static scan for secrets/default credentials, forwarded-IP trust, CORS/cookie/CSRF config, raw SQL substitution, non-idempotent SQL, and logging leaks.
- Targeted source verification for returned candidates.

Result:

- Confirmed existing P2 issue 10: forwarded-IP trust affects login IP rate limiting.
- Added P2 issue 11: SQL init masks non-idempotent migration errors.
- Added P3 issue 14: request body limit relies on deployment proxy for chunked requests.
- No new confirmed P0/P1 in this scope beyond the already listed DB unique-key issue.

## Final judgement

V7 is still viable as the final feature version, but not yet clean enough to tag as `v7-final`.

Minimum finalization gate:

1. Fix semester-scoped `schedule` unique keys.
2. Decide and fix plan-apply semantics around manual schedules.
3. Add semester filtering to timetable APIs.
4. Fix destructive/polluting E2E tests.
5. Add one reproducible final acceptance command.
