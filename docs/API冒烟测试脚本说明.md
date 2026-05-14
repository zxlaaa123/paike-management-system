# API 冒烟测试脚本说明

## 文件位置

- 脚本：`scripts/api-smoke-test.js`
- 命令入口：`npm run smoke:api`

## 目的

这个脚本用于做一轮轻量级后端 API 冒烟测试。

适合场景：

- 后端接口刚改完，先快速确认主流程没挂
- AI 或开发者改了 controller/service/mapper 后做一次自检
- 手动测试前先确认关键接口可用

它不是正式单元测试，也不是完整回归测试。
它的目标是快速验证“系统主链路是否还活着”。

## 覆盖范围

当前脚本会验证：

1. 健康检查
2. 登录
3. 时间段列表
4. 排课列表分页
5. 教学任务列表分页
6. 未排任务列表分页
7. 教师创建
8. 班级创建
9. 教室创建
10. 课程创建
11. 教学任务创建
12. 手动排课创建
13. 班级课表查询
14. 教师冲突检测
15. 测试数据清理

## 默认行为

- 默认连接：`http://127.0.0.1:8090`
- 默认账号：`admin / 123456`
- 默认会自动创建测试数据
- 默认在结束后自动清理测试数据
- 有失败时进程退出码为 `1`

## 使用方式

### 直接运行

```bash
npm run smoke:api
```

### 输出 JSON 结果

```bash
npm run smoke:api -- --json
```

### 把结果写到文件

```bash
npm run smoke:api -- --output output/api-smoke-result.json
```

### 保留测试数据不清理

```bash
npm run smoke:api -- --keep-data
```

这个参数只建议在你明确需要人工继续检查测试数据时使用。

## 可配置参数

### CLI 参数

- `--json`
- `--output <path>`
- `--keep-data`
- `--base-url <url>`
- `--username <username>`
- `--password <password>`
- `--prefix <tag>`

### 环境变量

- `PAIKE_BASE_URL`
- `PAIKE_USERNAME`
- `PAIKE_PASSWORD`
- `PAIKE_SMOKE_PREFIX`

示例：

```bash
$env:PAIKE_BASE_URL="http://127.0.0.1:8090"
$env:PAIKE_USERNAME="admin"
$env:PAIKE_PASSWORD="123456"
npm run smoke:api
```

## 测试数据命名

脚本会用前缀创建测试数据。

默认前缀格式：

- `SMOKE-123456`

你也可以手动指定：

```bash
npm run smoke:api -- --prefix DEV-CHECK-01
```

## 给后续 AI / 开发者的建议

如果后续要继续扩展这份脚本，建议遵守：

1. 保持“冒烟”定位，不要把它写成超长的全量回归脚本
2. 新增测试时优先覆盖关键主链路，不要先堆边角流程
3. 默认必须自动清理测试数据
4. 所有新增数据都要带统一前缀
5. 输出尽量保持简洁，失败时能快速定位接口即可

## 不建议做的事

- 不要把真实线上地址写死
- 不要把真实账号密码写死进仓库
- 不要让脚本默认保留测试数据
- 不要把一次性排障逻辑直接塞进正式脚本
