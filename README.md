# 高校排课管理系统

基于 Spring Boot 3、Vue 3、MyBatis Plus、MySQL 的高校排课管理系统。

当前 `main` 已完成 V1-V7。V6 已收口系统治理能力（审计日志、回归测试中心、一致性检查、性能基线、迁移状态、错误码中心），V7 已完成统计与展示补全、首页治理摘要、性能趋势和 E2E Cookie 认证统一，并完成总体验收。

## 当前状态

- 后端：Spring Boot 3.3.x、Java 17、Maven、MyBatis Plus、MySQL 8。
- 前端：Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router、Axios。
- 包管理：前端使用 npm，提交 `frontend/package-lock.json`。不要提交 `frontend/pnpm-lock.yaml`，除非项目明确切换到 pnpm。
- 默认后端端口：`8090`。
- 默认前端端口：`5173`。
- 验收状态：后端全量测试、前端类型检查、前端构建、V6 smoke、旧 stage E2E Cookie 认证回归均已通过。

## 功能范围

- V1：基础数据管理、教学任务、手动排课、冲突检测、课表查询。
- V2：自动排课、教师禁排、规则配置、未排任务、冲突报告、评分报告、Excel 导出。
- V3：学期管理、排课方案、多方案生成、评分明细、方案对比、方案应用、历史回滚、排课日志。
- V4：排课质量分析、风险诊断、图表、局部调整、课程锁定、报告导出、AI 辅助分析。
- V5：智能修复建议、候选位置推荐、试算方案、局部重排、优化前后对比、一致性检查、AI 修复解释、最终回归验收。
- V6：系统治理中心，包括审计日志、回归测试中心、一致性检查、性能基线、迁移状态和错误码中心。
- V7：统计与展示补全，包括方案展示字段补齐、教师连续节次统计、首页治理摘要、性能趋势、E2E Cookie 认证统一和总体验收。

## 目录结构

```text
.
├─ backend/                  # Spring Boot 后端
│  ├─ src/main/java/com/paike/scheduler
│  │  ├─ auth/               # JWT、登录、拦截器、CSRF 校验
│  │  │  ├─ dto/             # 登录请求 DTO
│  │  │  └─ vo/              # 登录响应 VO
│  │  ├─ common/             # 响应、异常、枚举、工具
│  │  │  ├─ enums/
│  │  │  ├─ exception/
│  │  │  ├─ response/
│  │  │  └─ util/
│  │  ├─ config/             # CORS、安全、初始化配置
│  │  ├─ controller/         # REST 接口（35 个 Controller）
│  │  │  └─ vo/              # Controller 层响应 VO
│  │  ├─ entity/             # 实体（30 个，纯持久化列，无 view 字段）
│  │  ├─ mapper/             # MyBatis Mapper
│  │  └─ service/            # 业务逻辑（~50 个 Service）
│  │     ├─ dto/             # Service 层请求 DTO
│  │     ├─ vo/              # Service 层响应 VO（~60 个，承载派生字段）
│  │     └─ scheduling/      # 排课算法核心
│  └─ src/main/resources
│     ├─ application.yml
│     └─ db/                 # schema 与阶段 SQL
├─ frontend/                 # Vue 前端
│  └─ src
│     ├─ api/
│     ├─ components/
│     ├─ router/
│     ├─ stores/
│     ├─ utils/
│     └─ views/
├─ docs/                     # 需求、设计、测试文档
├─ scripts/                  # 冒烟脚本
└─ tests/                    # Playwright E2E
```

## 本地环境

需要：

- JDK 17+
- Maven 3.9+
- Node.js 18+
- MySQL 8.x
- PowerShell 7+ 推荐

初始化数据库：

```sql
CREATE DATABASE paike CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

后端启动时会执行 `backend/src/main/resources/db/*.sql` 中配置到 `application.yml` 的脚本；迁移失败会中断启动，新增脚本必须保持幂等。

## 后端启动

当前分支不再保留不安全默认值。启动前必须显式设置数据库地址、数据库用户名、数据库密码、JWT 密钥。

必需环境变量：

| 变量 | 说明 | 示例 |
|------|------|------|
| `DB_URL` | MySQL 连接地址 | `jdbc:mysql://127.0.0.1:3306/paike?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai` |
| `DB_USERNAME` | MySQL 用户名，不是系统登录用户名 | `root` |
| `DB_PASSWORD` | MySQL 密码 | `你的MySQL密码` |
| `JWT_SECRET` | JWT 签名密钥，至少 32 字节 | `dev_local_secret_please_change_32_chars_minimum` |

PowerShell 示例：

```powershell
cd D:\paike\backend

$env:DB_URL="jdbc:mysql://127.0.0.1:3306/paike?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的MySQL密码"
$env:JWT_SECRET="dev_local_secret_please_change_32_chars_minimum"

mvn spring-boot:run
```

可选环境变量：

```powershell
$env:JWT_EXPIRATION_MS="86400000"
$env:ADMIN_DEFAULT_PASSWORD="仅本地开发可设置的管理员初始密码"
$env:COOKIE_SECURE="false"
$env:CORS_ALLOWED_ORIGINS="http://localhost:5173,http://127.0.0.1:5173"
$env:CORS_ALLOW_CREDENTIALS="false"
```

说明：

- `JWT_SECRET` 必须至少 32 字节，不能使用占位符。
- `DB_USERNAME` 和 `DB_PASSWORD` 必须显式提供；`DB_USERNAME` 是 MySQL 账号。
- `ADMIN_DEFAULT_PASSWORD` 不设置时，首次创建 admin 会生成随机密码并打印到后端启动日志。
- 生产 HTTPS 部署时应设置 `COOKIE_SECURE=true`。

健康检查：

```text
GET http://127.0.0.1:8090/api/health
```

## 前端启动

```powershell
cd D:\paike\frontend
npm install
npm run dev
```

访问：

```text
http://127.0.0.1:5173
```

前端通过 Vite 代理访问后端 `/api`。如果后端端口变更，需要同步检查 `frontend/vite.config.ts`。

## 登录账号

默认用户名：

```text
admin
```

密码来源：

- 如果设置了 `ADMIN_DEFAULT_PASSWORD`，使用该值。
- 如果未设置，查看后端首次启动日志中的随机密码。

旧版 `admin / 123456` 已废弃。

## 验证命令

前端构建：

```powershell
cd D:\paike\frontend
npm run build
```

后端编译打包：

```powershell
cd D:\paike\backend
mvn -DskipTests clean package
```

后端测试：

```powershell
cd D:\paike\backend
$env:JWT_SECRET="dev_local_secret_please_change_32_chars_minimum"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的MySQL密码"
mvn test
```

如果未设置 `JWT_SECRET`，`mvn test` 会因安全校验失败，这是预期行为。

API 冒烟：

```powershell
cd D:\paike
npm run smoke:api
```

Playwright E2E：

```powershell
cd D:\paike
npm test
```

如果本机尚未安装 Playwright 浏览器：

```powershell
cd D:\paike
npx playwright install chromium
```

当前 V7 总体验收已验证通过：

```powershell
cd D:\paike\backend
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/paike?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:JWT_SECRET="dev_local_secret_please_change_32_chars_minimum"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的MySQL密码"
mvn -q test

cd D:\paike\frontend
npx vue-tsc -b
npx vite build

cd D:\paike
npm run test:v6
npm run test:stage6
npm run test:stage7
npm run test:stage9
```

说明：`npm test` 等价于 `npm run test:acceptance`，会串行运行 V6 governance、stage6、stage7、stage9。E2E 页面用例统一使用 API 登录后拿到的 `paike_token` 与 `XSRF-TOKEN` Cookie，不再向 `localStorage` 注入旧 token。运行 Playwright 前需按本文启动后端 `8090` 和前端 `5173`。

## 安全与部署注意

- JWT 使用 httpOnly Cookie；浏览器鉴权以 `paike_token` Cookie 为准，不再依赖 `localStorage` token。
- Cookie 登录下，POST、PUT、DELETE、PATCH 请求需要 `X-CSRF-Token`。
- CORS origin 通过 `CORS_ALLOWED_ORIGINS` 配置，不要在生产中使用不受控的通配配置。
- 生产 HTTPS 部署应设置 `COOKIE_SECURE=true`，并确保反向代理保留 `Set-Cookie`。
- 登录 IP 限流默认只使用 `remoteAddr`。只有受信任反向代理已清洗 `X-Forwarded-For` / `X-Real-IP` 时，才设置 `TRUST_FORWARDED_HEADERS=true`。
- JSON 请求体由 `MAX_REQUEST_BODY_SIZE` 做应用层限制，含无 `Content-Length` 的 chunked 请求；生产 nginx 仍应同步设置 `client_max_body_size`。
- 正式课表写入路径已事务化，冲突检测仍以前端预检 + 后端保存时二次校验 + 数据库唯一约束兜底。
- `semester.is_current` 已增加数据库唯一性兜底，避免多个当前学期。

## 关键业务边界

- 自动排课、局部重排、智能修复默认产出方案，不直接覆盖正式课表。
- 应用到正式课表必须由用户显式触发，并由后端二次校验。
- 课程锁定项必须在候选过滤、试算、应用前后全链路保护。
- AI 分析和修复建议只辅助决策，不直接修改正式课表。
- 正式课表变更必须可追溯：事务、日志、来源方案。

## 常见问题

后端启动报 `JWT_SECRET` 缺失：

```powershell
$env:JWT_SECRET="dev_local_secret_please_change_32_chars_minimum"
```

后端启动报数据库账号密码缺失：

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的MySQL密码"
```

不知道 admin 密码：

- 看后端首次启动日志。
- 或删除本地开发库后设置 `ADMIN_DEFAULT_PASSWORD` 再重新启动初始化。

出现 `frontend/pnpm-lock.yaml`：

- 这是 pnpm 锁文件。
- 当前项目使用 npm，不要提交它。
- 需要清理时直接删除该未跟踪文件即可。

## 文档入口

- `docs/v1/`
- `docs/v2/`
- `docs/v3/`
- `docs/v4/`
- `docs/v5/`
- `docs/v6/`
- `docs/v7/`
- `claude-opus-4.7-bug验证报告.md`
- `claude-opus-4.7-bug修复建议.md`

> 以上两个文件已纳入 `.gitignore`，仅本地保留。
