# Next roadmap 2026-06-18

## 1. 当前收口状态

| 项目 | 状态 | 证据 |
|---|---|---|
| V9 周次/单双周 | 已完成 | `8d9ca37`、`docs/v9/V9_FINAL_验收记录.md` |
| A1/A2/A3/D3 治理门面 | 已完成 | `74ffd8c`、`a881787` |
| C2 软约束扩展 | 已完成 | `46ee40a`、`e1494c7` |
| C3 导出 weekType 覆盖 | 已完成 | `ced7f4e` |
| Java 警告 P1/P2/P3 | 已完成 | `355b34c`、`210fb9c`、`53e3c29` |
| 后端全量测试 | 已通过 | `mvn test`，340 tests / 0 failures / 0 errors / 2 skipped |

全量测试环境变量：

```powershell
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/paike?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="123456"
$env:JWT_SECRET="dev_local_secret_please_change_32_chars_minimum"
mvn test
```

## 2. 推荐下一阶段

首选：**V10 连续周段支持**。

理由：

- V9 已解决 ODD / EVEN / ALL 三值周次，连续周段是自然延伸。
- 真实排课场景常见：第 1-8 周、第 9-16 周、实训周、阶段性课程。
- 技术叙事完整：数据模型、冲突检测、评分、导出、测试链路都需要系统性设计。

## 3. V10 立项前先做可行性研究

不要直接改模型。先产出 `docs/v10/V10_00_连续周段_可行性研究与决策.md`。

必须先定的设计点：

| 主题 | 决策问题 |
|---|---|
| 时间表达 | 用 `start_week` / `end_week`，还是独立 `week_pattern` |
| 与 V9 兼容 | ODD / EVEN / ALL 如何和连续周段叠加 |
| 唯一键 | `schedule` / `schedule_plan_item` 的冲突唯一性如何表达 |
| 冲突检测 | 周段重叠、单双周交集、同槽共存规则如何统一 |
| 评分 | 增量评分与全量评分必须保持一致 |
| 导出 | Excel cell 中多周段、多课程如何展示 |
| 数据迁移 | 旧数据默认周段如何填充，schema 脚本必须幂等 |

## 4. 暂缓项

| 项目 | 暂缓原因 |
|---|---|
| RBAC 权限体系升级 | 触及面大，收益不如 V10 直接服务核心排课能力 |
| 前端大改版 | 当前主风险不在 UI，先避免偏离核心排课链路 |
| 新软约束继续堆叠 | C2 已有一个新增规则，继续加前应先评估质量收益 |
| 大规模重构 | V8/V9/V10 连续演进期，优先保持回归稳定 |

## 5. 建议执行顺序

1. 建 `docs/v10/`。
2. 写 V10 可行性研究与决策。
3. 明确 schema / 冲突检测 / 导出方案。
4. 列阶段计划和验收清单。
5. 再开代码实现。
