# Global PowerShell Rules (Windows + pwsh 7+)

## Shell Preference
- **禁止调用 Bash Tool。** 需要执行 shell 命令时，只能使用 PowerShell Tool（pwsh.exe）。这是工具层的硬约束，不是命令风格偏好。
- 不要把 Unix 惯用语法（`ls | head`、`grep`、`curl`、`find`、`cat`）作为选 Bash Tool 的理由——改用对应的 PowerShell cmdlet（`Get-ChildItem | Select-Object -First N`、`Select-String`、`Invoke-WebRequest`、`Get-ChildItem -Recurse`、`Get-Content`）。
- 列文件用 Glob，读文件用 Read，搜内容用 Grep——dedicated tool 优先级仍然高于 shell。
- 只有用户明确说"用 bash"或"sh"时，才可以考虑 Bash Tool；即便如此也优先 PowerShell 等价写法。
- 路径一律使用 Windows 原生风格（C:\ 或 $env:USERPROFILE），避免 / 路径。

## PowerShell 编码规范
- 函数/命令动词必须来自 Get-Verb（Get-、Set-、New-、Remove- 等）。
- 函数名/类名用 PascalCase，变量/参数用 camelCase。
- 优先使用 Write-Verbose / Write-Debug / Write-Information，**严禁随意使用 Write-Host**（除非必须格式化控制台输出）。
- 模块结构严格遵循：src/Public、src/Private、src/Classes + 静态 dot-source + Export-ModuleMember。
- 所有脚本必须加上 #Requires -Version 7.0。
- 错误处理统一使用 try/catch + ErrorAction Stop + Write-Error。
- 测试必须用 Pester，静态检查用 PSScriptAnalyzer。

## 常用指令
- 每次生成代码后自动运行 PSScriptAnalyzer 检查。
- 优先使用原生 cmdlet（Get-Command、Invoke-WebRequest、ConvertFrom-Json 等），不要自己造轮子。

---

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

## 1. MCP 使用规范

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

## 2. 调试与联调经验

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
