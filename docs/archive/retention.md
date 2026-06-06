# 文档保留策略

本文只标记保留优先级，不代表已经删除文件。

## keep

长期保留。项目交付、后续开发、问题追溯会持续用到。

- `README.md`
- `AGENTS.md`
- `CLAUDE.md`
- `docs/v1/` 到 `docs/v6/`
- `docs/API冒烟测试脚本说明.md`
- `docs/手动测试流程.md`
- `docs/测试报告.md`
- `docs/修复记录.md`
- `docs/代码设计问题审查报告.md`
- `docs/代码设计问题审查报告核实记录.md`
- `docs/V1-V4_总体验收实施计划_精简版_2026-05-19.md`
- `docs/V1-V4_总体验收_AI执行说明_2026-05-19.md`
- `docs/V1-V5_后续开发建议_2026-05-21.md`
- `docs/V5_代码深度审查_2026-05-21.md`
- `docs/archive/README.md`

## archive

归档保留。平时不需要看，但能解释关键技术决策、修复背景、质量问题来源。

- `docs/archive/plans/`
- `docs/archive/reviews/`
- `docs/archive/investigations/`

其中 `investigations` 里和下面主题有关的文档优先保留：

- 自动排课规则
- 事务边界
- 软删除
- 数据库约束和字符集
- API 契约
- 评分权重
- 前端类型收口
- AI 客户端
- 请求体大小限制
- 缓存失效

## delete-candidate

确认不再需要后可删。当前先保留，避免丢失上下文。

- `docs/archive/handoff-prompts/`
- `docs/archive/logs/GIT_LOG.md`
- `docs/archive/misc/minimaxm320260602.md`
- `docs/archive/misc/代码修改建议.md`

## review-before-delete

删除前需要人工快速看一眼，确认没有唯一信息。

- `docs/archive/logs/20260603_M16_第4批_ScheduleAdjustLog收口.md`
- `docs/archive/misc/20260529_M41显式Deleted条件整理.md`
- `docs/archive/misc/20260602_M16_EntityViewField风险评估.md`

## 建议清理节奏

1. 先保持当前归档结构一段时间。
2. 后续如果确认提示词和临时日志没有用，再删除 `delete-candidate`。
3. `archive` 类文档只在明显重复、内容已被主线文档吸收时删除。
4. 删除前单独提交，提交信息使用 `docs: prune archived notes`。
