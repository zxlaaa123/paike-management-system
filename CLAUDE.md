# CLAUDE.md

## 0. 行为准则

### 0.1 工具使用优先级
- **Windows 环境下，优先使用 PowerShell 执行命令**，而非 Bash
- Bash 仅用于：git 操作、mkdir/rm/mv 文件操作、cd 导航、简单脚本
- 遇到 taskkill、sleep、Start-Process、管道重定向等命令时，**必须用 PowerShell**
- 原因：Bash 在 Windows 上不兼容 PowerShell 语法（如 `$null`、`Start-Sleep`、`Get-Process` 等），会导致命令失败和调试时间浪费

### 0.2 后端启动方式
- **如果用户明确要求 AI 执行启动，可以暂时忽视以下两条限制**
- **后端必须由用户在独立 PowerShell 终端手动启动**，AI 不负责启动和等待
- 启动方式：`cd D:\paike\backend; mvn spring-boot:run`
- 用户看到 `Started` 或 `Tomcat started` 后告诉 AI
- AI 只负责：写代码、测试接口、看日志排查问题
- **永远不要** 尝试在 AI 工具调用里用 Start-Process/sleep/run_in_background 等方式启动或等待 Spring Boot

## 1. 项目身份

你正在开发一个毕业设计项目：

**基于 Spring Boot + Vue 的高校排课管理系统**

这是一个本地运行的前后端分离 Web 项目，不是云端 SaaS，不是移动 App，不是桌面软件。

第一版目标是完成：

```text
基础数据管理 + 教学任务管理 + 手动排课 + 冲突检测 + 课表查询
```

## 2. 技术栈固定

### 后端

- Java 17
- Spring Boot 3
- MyBatis Plus
- MySQL
- JWT
- Maven

### 前端

- Vue 3
- Vite
- TypeScript
- Element Plus
- Vue Router
- Pinia
- Axios

不得擅自更换技术栈。

## 3. 第一版必须做的功能

只做以下功能：

- 管理员登录
- 首页统计
- 教师管理
- 班级管理
- 教室管理
- 课程管理
- 教学任务管理
- 固定时间段数据
- 手动排课
- 排课冲突检测
- 班级课表
- 教师课表
- 教室课表

## 4. 第一版禁止开发的功能

不要提前开发：

- 自动排课
- 遗传算法
- Timefold Solver
- 学生选课
- 教师端
- 学生端
- 调课申请
- 考试排课
- Excel 导入导出
- 课表打印
- AI 排课建议
- 复杂权限菜单
- 操作日志
- 多学期管理
- 多校区管理
- 移动端适配

这些功能放到后续版本，不允许混进第一版。

## 5. 核心业务规则

排课保存前，后端必须检测：

1. 同一教师同一时间不能有两门课
2. 同一班级同一时间不能有两门课
3. 同一教室同一时间不能安排两门课
4. 班级人数不能大于教室容量
5. 实验课必须安排在实验室
6. 机房课必须安排在机房
7. 停用教师不能参与排课
8. 停用班级不能参与排课
9. 停用教室不能参与排课
10. 教学任务不能超过每周课时

冲突检测不能只写在前端，必须写在后端 service 层。

## 6. 数据库表固定

第一版核心表：

```text
sys_user
teacher
class_info
classroom
course
teaching_task
time_slot
schedule
```

不要在第一版引入大量无关表。

## 7. 时间段规则

第一版固定：

```text
周一到周五
每天 4 个大节
共 20 个时间段
```

大节包括：

- 第 1-2 节
- 第 3-4 节
- 第 5-6 节
- 第 7-8 节

不要在第一版做复杂时间段配置页面。

## 8. 开发顺序

必须按阶段推进：

```text
阶段 0：项目初始化
阶段 1：后端基础框架
阶段 2：前端基础框架
阶段 3：登录模块
阶段 4：基础数据管理
阶段 5：教学任务管理
阶段 6：时间段初始化
阶段 7：手动排课
阶段 8：冲突检测
阶段 9：课表查询
阶段 10：整体联调
```

每次只完成当前阶段，不要提前开发后续阶段。

## 9. 后端代码要求

- 使用统一返回格式
- 使用全局异常处理
- 使用参数校验
- Controller 不写复杂业务逻辑
- 业务逻辑写在 Service
- 冲突检测独立成 ScheduleConflictService
- 使用逻辑删除
- 重要字段添加唯一性校验
- 错误提示要清楚，适合前端展示

错误提示示例：

```text
排课失败：张老师在周一第1-2节已有课程
排课失败：A101 教室在该时间段已被占用
排课失败：计科1班人数为60，当前教室容量为40
排课失败：实验课必须安排在实验室
```

## 10. 前端代码要求

- 使用 Vue 3 Composition API
- 使用 TypeScript
- 使用 Element Plus
- 接口请求统一封装到 src/api
- 路由配置放到 src/router
- 登录状态放到 Pinia
- 页面保持简洁，优先保证可用
- 不追求花哨动画
- 表单要有基础校验
- 后端错误信息要显示给用户

## 11. 项目验收目标

第一版完成后，必须能演示：

1. 登录系统
2. 新增教师、班级、教室、课程
3. 创建教学任务
4. 手动排课
5. 制造教师冲突并被系统拦截
6. 制造班级冲突并被系统拦截
7. 制造教室冲突并被系统拦截
8. 制造容量不足并被系统拦截
9. 制造教室类型不匹配并被系统拦截
10. 查看班级课表
11. 查看教师课表
12. 查看教室课表

## 12. 开发时的行为准则

- 优先阅读 docs 目录中的文档
- 不要擅自扩大项目范围
- 不要把第一版写成复杂教务系统
- 不要引入无关依赖
- 不要跳阶段开发
- 每完成一个阶段，都要保证项目能启动
- 如果发现文档冲突，以 CLAUDE.md 为最高优先级

## 13. MCP 使用规范

### 可用 MCP 服务器
- **playwright**: 浏览器自动化（`mcp__playwright__*`）
- **chrome-devtools**: Chrome DevTools 协议（`mcp__chrome-devtools__*`）
- **context-mode**: 上下文管理（`mcp__plugin_context-mode_context-mode__*`）

### MCP 配置位置
用户级配置在 `C:\Users\zxl\.claude.json`，项目级 MCP 需在 `mcpServers` 中显式声明（项目级会覆盖全局配置）。

### Playwright MCP 使用要点
- 用 `browser_run_code_unsafe` 执行复杂操作（支持 async/await）
- 用 `browser_navigate` 导航页面
- 用 `browser_snapshot` 获取页面快照（需指定 `target` 为具体元素引用）
- 用 `browser_click` / `browser_fill_form` 操作表单
- 用 `browser_evaluate` 执行 JS 获取页面信息
- 用 `browser_take_screenshot` 截图调试
- `browser_run_code_unsafe` 内可用 `page.fill()`、`page.click()`、`page.waitForURL()` 等标准 Playwright API

### Chrome DevTools MCP 使用要点
- 用 `list_pages` 查看当前打开的页面
- 用 `navigate_page` 导航到指定 URL
- 用 `take_screenshot` / `take_snapshot` 截图和获取 DOM
- 用 `evaluate_script` 执行 JS

### 调试技巧
- 当 Playwright 测试失败时，优先用 `browser_run_code_unsafe` 直接操作浏览器验证页面行为
- 用 `browser_run_code_unsafe` 执行 `page.$$eval('button', els => els.map(e => e.textContent))` 等快速检查页面元素
- Element Plus 弹窗按钮文本可能是英文（"OK"/"Cancel"），需用多选择器兼容：`button:has-text("OK"), button:has-text("确定")`
- 严格模式下选择器匹配多个元素时，用 `.first()` 或更精确的作用域选择器

### 测试数据隔离
- 每个测试用例应使用独立的时间段/数据，避免测试间互相干扰
- 测试结束后应清理创建的 schedule 数据（注意 `deleteById` 是软删除）
- 创建教学任务时注意：实验课→实验室，机房课→机房，容量要匹配
- 用 `Date.now().toString().slice(-6)` 生成唯一标识（教师编号、班级名、教室名等）
- 每个冲突测试用独立时间段索引（SLOT map），防止测试间通过残留 schedule 互相干扰
- 注意容量/类型匹配：测试教室冲突时，班级人数必须 <= 教室容量，否则先触发容量冲突；测试某类冲突时，要确保不会先触发其他冲突

## 14. 调试与联调经验

### 服务启动
- `mvn spring-boot:run` 在后台启动可能因端口冲突立即退出，用前台启动排查错误
- Windows 端口可能被系统保留（TIME_WAIT、Hyper-V 动态端口范围），`netstat -ano | findstr "端口"` 排查
- PowerShell `Invoke-WebRequest` 可能因代理问题连不上 localhost，用 `curl` 验证服务是否正常
- 前端 Vite 代理配置（`vite.config.ts`）需与后端端口一致

### 数据库升级
- `schema.sql` 用 `CREATE TABLE IF NOT EXISTS` 不会重建已有表
- 添加新列用 `ALTER TABLE ... ADD COLUMN`（MySQL 不支持 `IF NOT EXISTS`）
- `DROP TABLE IF EXISTS` + `CREATE TABLE` 是最简单的升级方式（开发环境，会丢失数据）
- `spring.sql.init.mode: always` 每次启动都执行 schema.sql

### MyBatis Plus 注意事项
- `deleteById` 触发 `@TableLogic` 软删除（SET deleted=1），不是物理删除
- `selectById` 自动加 `WHERE deleted=0`，查不到软删除记录
- 手动 `setDeleted(1)` + `updateById` 不触发 `@TableLogic` 机制，必须用 `deleteById`
- 软删除记录不计入冲突检测（查询条件有 `deleted=0`）

### Element Plus 组件
- 确认弹窗按钮文本取决于 locale，需兼容中英文：`button:has-text("OK"), button:has-text("确定")`
- 下拉框（el-select）交互：先 click 打开，wait 500ms，再 click 选项
- filterable 下拉框可用 `fill()` 输入搜索文本过滤选项
- 严格模式下选择器匹配多个元素时报错，用 `.first()` 或更精确的作用域选择器
- 表格空状态用 `el-empty` 组件显示

### 测试调试技巧
- 测试失败时先截图（`browser_take_screenshot`）确认页面状态
- 用 `browser_run_code_unsafe` 执行 `page.$$eval()` 检查元素结构和文本
- 下拉列表有多个同名选项时，用搜索过滤或 `.first()` 选中
- 前端测试每个页面操作后需要足够 wait 时间（500ms-1000ms）

