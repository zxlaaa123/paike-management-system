# 高校排课管理系统 V1

基于 `Spring Boot + Vue` 的高校排课管理系统第一版。

## 项目定位

```text
基础数据管理 + 教学任务管理 + 手动排课 + 冲突检测 + 课表查询
```

## 技术栈

- 后端：Java 17、Spring Boot 3、MyBatis Plus、MySQL、JWT、Maven
- 前端：Vue 3、Vite、TypeScript、Element Plus、Vue Router、Pinia、Axios

## Java 版本说明

- 项目基线与验收标准：`Java 17`
- 本地开发可临时使用 `Java 21` 运行与调试（Spring Boot 3 兼容）
- 提交前请确保代码不使用仅 `Java 21` 才支持的语法/特性，以保证在 `Java 17` 环境可构建与运行

## 目录结构

```text
.
├─ backend/      # Spring Boot 后端
├─ frontend/     # Vue 3 前端
├─ docs/
│  ├─ v1/        # V1 文档
│  └─ v2/        # 后续版本文档（当前不开发）
├─ CLAUDE.md     # 项目规则（最高优先级）
└─ README.md
```

## 本地启动（阶段 0 骨架）

### 1) 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认端口：`8080`  
数据库和 JWT 配置请参考：`backend/.env.example` 与 `backend/src/main/resources/application.yml`

### 2) 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认开发端口由 Vite 自动分配（通常为 `5173`）。

## 开发约束

- 严格按阶段推进，禁止跳阶段开发
- 若文档冲突，以 `CLAUDE.md` 为最高优先级
- 第一版禁止引入自动排课、学生端、多学期、多校区等超范围功能
