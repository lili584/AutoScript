# 后续优化记录

## 章节级 rolling summary

在当前 chunk 分片中，第二个及之后的 chunk 如果只携带前一个 chunk 的局部 context，长场景、短段落密集对白、跨 chunk 连续剧情会存在上下文不足风险。基础版 rolling summary 已纳入 AI 场景抽取链路，用于在同一章节内传递稳定上下文。

已实现的基础方向：

- 每处理完一个 chunk 后，维护当前章节的滚动摘要。
- 摘要包含当前地点、人物、冲突目标、已发生关键事件、未解决问题。
- 后续 AI 输入改为 `章节 rolling summary + 前文局部 context + 当前 chunk`。

后续仍可优化：

- 控制 rolling summary 长度，避免上下文成本无限增长。
- 增加人工可查看的章节状态面板，帮助调试 AI 合并判断。
- 针对跨章节伏笔增加全小说级 summary，但不应替代章节级 summary。
