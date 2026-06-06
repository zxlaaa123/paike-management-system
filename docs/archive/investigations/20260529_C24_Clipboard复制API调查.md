# C-24 Clipboard 复制 API 调查

## 结论

已修复。

原问题部分成立：前端仅检出 2 处 `document.execCommand('copy')`，且都位于 `navigator.clipboard.writeText()` 失败后的 fallback。它不是 Critical 级运行故障，但确实是废弃 API。

## 本轮处理

新增 `frontend/src/utils/clipboard.ts`：

- 统一封装 `copyText()`。
- 优先使用标准 Clipboard API：`navigator.clipboard.writeText()`。
- Clipboard API 不可用或执行失败时返回 `false`，由页面提示用户手动复制。
- 移除两个页面中的 `document.execCommand('copy')` fallback。

本轮不继续保留 textarea + `execCommand` fallback。原因是该 fallback 本身就是本条目的问题来源；在当前本地开发和现代浏览器场景下，标准 Clipboard API 更明确。

## 本轮验证

前端构建通过：

- `npm --prefix D:\paike\frontend run build`

并确认前端源码不再检出 `document.execCommand`。

