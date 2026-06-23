# AutoScript - AI 小说转剧本工具

> 演示视频：https://www.bilibili.com/video/BV1KnEt6MEye/?vd_source=c80ba0f6fecbebaada6d9e607c42e018
> 仓库地址：https://github.com/lili584/AutoScript
> Schema 文档：[SCRIPT_YAML_SCHEMA.md](SCRIPT_YAML_SCHEMA.md)

AutoScript 是一款面向小说作者的 AI 辅助剧本创作工具。用户上传 3 个章节以上的 Markdown 小说后，系统会解析章节、拆分 chunk、调用 LLM 抽取结构化剧本场景，并导出可编辑、可追溯、可测评的剧本 YAML 初稿。

## 项目目标

小说改编剧本需要拆场景、提对白、整理人物、补动作和校对原文来源。直接让大模型一次性输出完整剧本，容易出现长文本上下文丢失、格式漂移、人物重复、对白编造和来源不可追溯等问题。

AutoScript 的目标不是生成最终定稿，而是提供一个可继续打磨的结构化初稿：

- 对作者：降低从小说到剧本初稿的整理成本。
- 对系统：将大模型输出放进可校验、可回溯、可导出的工程链路。
- 对后续编辑：通过 YAML Schema 和测评报告支持人工二次修改。

## 用户流程

```text
新建小说 / 上传 Markdown
  -> 解析章节和 chunk
  -> 启动 AI 分析任务
  -> 查看 scenes 草稿
  -> 预览并下载 YAML
  -> 上传 1-5 份 YAML 做测评对比
```

前端包含两个主要工作区：

- 小说管理：小说 CRUD、Markdown 导入、章节解析、AI 分析、YAML 预览与下载。
- 测评对比：选择小说并上传多份 YAML，横向比较总分、六项指标和修改建议。

## 核心链路

```text
Markdown 小说
  -> 小说管理与原文保存
  -> Markdown 章节解析
  -> 按段落 chunk 分片
  -> LLM scenes JSON 抽取
  -> 后端质量校验 / 去重 / 合并 / source_refs 修正
  -> 角色画像生成
  -> YAML 汇总导出
  -> YAML 多版本测评对比
```

### Markdown 解析与 chunk 分片

系统约定 `#` 表示小说标题，`##` 表示章节标题。每章正文按空行拆段落，再按段落组装 chunk：

- chunk 目标长度为 1500-2000 字。
- 单段超过 2000 字时不强行截断。
- 后续 chunk 携带前一 chunk 的局部 context。
- context 使用混合策略，从前一 chunk 末尾向前取段落，满足最小字数或最大段落数后截断。

这套规则优先保证实现稳定和段落可追溯。后续可升级为 AI semantic chunking：先由模型根据段落编号输出语义连续段落组，再由后端校验连续、不重复、不越界。

### AI JSON 中间协议

AutoScript 不要求 LLM 直接输出最终 YAML，而是要求先输出 JSON：

```json
{
  "scenes": [
    {
      "scene_id": "chapter-1-chunk-1-scene-1",
      "title": "咖啡厅偶遇",
      "location": "咖啡厅",
      "time_of_day": "白天",
      "summary": "场景概要",
      "characters": ["林屿安", "客户"],
      "beats": [
        {
          "type": "action",
          "text": "林屿安把咖啡杯放回瓷盘。"
        },
        {
          "type": "dialogue",
          "character_name": "客户",
          "text": "林总？"
        }
      ],
      "source_refs": [
        {
          "chapter_index": 1,
          "chapter_title": "第一章 重逢",
          "chunk_index": 1,
          "paragraph_start": 1,
          "paragraph_end": 5
        }
      ]
    }
  ],
  "chapter_state": {
    "current_location": "",
    "active_characters": [],
    "current_conflict": "",
    "completed_events": [],
    "unresolved_questions": [],
    "open_scene": {
      "scene_id": "",
      "title": "",
      "location": "",
      "time_of_day": "",
      "characters": [],
      "summary": "",
      "is_resolved": true
    }
  }
}
```

这样设计的原因：

- YAML 对缩进和换行敏感，直接让 AI 输出更容易格式损坏。
- JSON 更适合作为后端校验、清洗、去重和合并的中间结构。
- 后端可以统一生成稳定的 `scene_id`、`character_id`、`source_refs` 和最终 YAML。

### rolling summary 与跨 chunk 合并

每个 chunk 处理完成后，LLM 会返回 `chapter_state`。下一个 chunk 调用时，后端会把上一 chunk 的 `chapter_state` 和局部 `context` 一起传入模型。

`chapter_state` 用于保存章节级滚动状态：

- 当前地点
- 活跃人物
- 当前冲突
- 已发生关键事件
- 未解决问题
- 当前 chunk 结束时可能尚未完结的 `open_scene`

如果长场景被 chunk 截断，下一 chunk 的第一个 scene 可以由 AI 标记：

```json
{
  "is_continuation": true,
  "continuation_of": "chapter-1-chunk-1-scene-3",
  "continuation_reason": "同一地点、同一人物、同一冲突继续推进"
}
```

后端不会直接相信该标记，必须同时满足以下规则才合并：

- 上一 chunk 存在未完结 `open_scene`。
- 当前 scene 是当前 chunk 的第一个 scene。
- 当前 chunk 与上一 open scene 属于同一章节。
- chunk 序号相邻。
- `continuation_of` 指向上一 open scene。
- 地点和时间不能明显冲突。
- 人物集合兼容，至少有交集，或其中一侧为空。

校验通过后，后端合并 beats、characters、source_refs 和 summary；否则忽略 continuation 标记，将当前 scene 作为新场景保存。

### source_refs 修正

LLM 会为每个 scene 标记段落来源，但模型常出现退化范围，例如多个 scene 都指向同一个完整 chunk。后端会对 `source_refs` 做二次处理：

1. 如果 AI 返回的段落范围是 chunk 子范围且不异常，直接保留。
2. 如果范围覆盖整个 chunk、覆盖 80% 以上，或接近整个 chunk，判定为退化。
3. 退化时，后端使用 beat 文本回当前 chunk 原文段落反查。
4. 优先匹配 dialogue 文本，action/transition 作为辅助。
5. 文本匹配不到时，按 scene 在 chunk 内的顺序做连续段落兜底切分。
6. 最后对同一 chunk 内多个 scene 的范围做顺序平滑，避免范围倒序或全部指向整块。

最终 YAML 中的 `source_refs` 来自 `AI 标记 + 后端反推 + 顺序兜底 + 平滑`，用于帮助作者回到原文校对。

### 质量校验与 retry

LLM 响应会经过两层校验：

- JSON 解析校验：响应必须能解析为 JSON，且顶层包含 `scenes` 数组。
- 质量校验：过滤空 action/transition、空 dialogue、无有效 beat 的 scene，并检测空 action 比例、空 scenes、只有对白无动作等问题。

如果 JSON 可解析但质量不合格，后端会把失败原因写入 retry prompt，针对同一个 chunk 重新生成一次。重试仍不合格时，任务失败并返回具体章节和 chunk 位置。

### 角色画像

角色是否存在由 scenes 决定，不由角色画像模型决定。后端从 `scene.characters` 和 `dialogue.character_name` 汇总基础人物表，并生成稳定 `character_id` 和首次出场 scene。

所有 scenes 生成完成后，系统再构造精简 scenes 摘要数组，额外调用一次 LLM 生成角色画像：

```json
[
  {
    "scene_id": "chapter-1-chunk-1-scene-1",
    "title": "咖啡厅偶遇",
    "location": "咖啡厅",
    "time_of_day": "白天",
    "summary": "场景概要",
    "characters": ["林屿安", "客户"],
    "dialogue_speakers": ["客户", "林屿安"]
  }
]
```

模型只补充 `aliases`、`role`、`description`。后端保存前会用已知角色集合过滤，避免 AI 新增幽灵角色。角色画像失败不会阻塞主生成流程，YAML 会回退为空画像字段或保留上一次成功结果。

## YAML 导出

后端采用确定性导出流程：

```text
script_scenes / script_dialogues / script_character_profiles
  -> 汇总 characters
  -> 映射 character_id
  -> 排序 scenes
  -> 写入 metadata / source / characters / scenes
  -> 生成 .yaml
```

YAML Schema 详见 [SCRIPT_YAML_SCHEMA.md](SCRIPT_YAML_SCHEMA.md)。该 Schema 以 `scenes` 为核心，使用 `beats` 保留 action、dialogue、transition 的顺序，并通过 `source_refs` 支持原文追溯。

## 测评系统

测评模块不调用 AI，定位为 deterministic quality gate，用于筛查 AI 输出中高频、可规则化识别的问题。它不是文学质量评分，也不判断剧情是否精彩。

六项指标如下：

| 指标 | 目的 |
| --- | --- |
| 对白召回率 | 检查原文直接对白是否被 YAML dialogue 保留。 |
| 对白精确率 | 检查 YAML dialogue 是否疑似编造，或是否把叙事误转为对白。 |
| 动作覆盖率 | 检查 action/transition beat 是否存在且有有效文本。 |
| 角色一致性 | 检查重复角色、未定义角色引用和对白角色引用问题。 |
| 忠实度 | 检查地点、时间、对白角色和叙事转对白是否有原文依据。 |
| 结构完整性 | 检查 scene_id、beat 数量、dialogue 存在性和章节覆盖。 |

测评结果包含：

- `overallScore`
- 六项 `scorecard.metrics`
- 结构化 `issues`
- 可展示的 `issuesMarkdown`

硬规则可能误判纯动作场景、合理概括和文学化改写。后续可引入 embedding 相似度、LLM-as-judge 或 RAGAS-like reference-based evaluation，提升对语义等价和合理改写的判断能力。

## 技术架构

```text
frontend/
  Vue 3 + Vite

bankend/
  Spring Boot 4 + Java 21
  MyBatis Plus
  PostgreSQL
  Redis / Redisson
  RestClient 调用 LLM OpenAI-compatible API
  SnakeYAML / Jackson
```

AI 分析是长任务：

- Java 21 virtual thread per task executor 执行后台任务。
- Java Semaphore 控制同一 JVM 内运行任务数和 LLM 请求并发数。
- Redis 保存任务运行态、最新任务索引和同小说任务锁。
- PostgreSQL 保存任务历史、chapters、chunks、scenes、dialogues、chapter_state 和角色画像。
- 服务重启时将遗留的 pending/running 任务标记为 failed，避免前端一直显示运行中。

当前版本使用 Redisson `RLock` 做单 Redis 同小说互斥。由于任务启动线程和后台执行线程不同，释放时使用 `forceUnlock`。后续更严谨的长任务锁可改为基于 taskId token 的 Redis `SET NX EX` 锁，避免锁 ownership 绑定线程。

## 启动方式

### 环境要求

- JDK 21
- Node.js
- PostgreSQL
- Redis
- LLM API Key

### 后端

Windows:

```powershell
cd bankend
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
cd bankend
./mvnw.sh spring-boot:run
```

### 前端

```powershell
cd frontend
npm install
npm run dev
```

### Redis

```powershell
docker compose -f docker-compose.redis.yml up -d
```

## 配置说明

后端主要环境变量：

```text
DB_URL=jdbc:postgresql://localhost:5432/autoscript
DB_USERNAME=agent
DB_PASSWORD=你的数据库密码

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_USERNAME=autoscript
REDIS_PASSWORD=你的 Redis 密码

DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_API_KEY=你的 LLM API Key
DEEPSEEK_MODEL=deepseek-v4-flash
DEEPSEEK_MAX_TOKENS=8000

SCRIPT_GENERATION_MAX_RUNNING_TASKS=2
SCRIPT_GENERATION_MAX_DEEPSEEK_REQUESTS=3
SCRIPT_GENERATION_RUNTIME_TTL_MINUTES=30
```

## 测试命令

后端：

```powershell
cd bankend
.\mvnw.cmd test
```

Linux/macOS:

```bash
cd bankend
./mvnw.sh test
```

前端：

```powershell
cd frontend
npm run build
```

## 第三方依赖

- Spring Boot 4
- MyBatis Plus
- PostgreSQL
- Redis
- Redisson
- Jackson
- SnakeYAML
- Lombok
- Vue 3
- Vite
- lucide-vue-next
- LLM OpenAI-compatible API

## 后续优化方向

- AI semantic chunking：由模型先输出语义连续段落组，再进行 scenes 抽取。
- Character Memory：将角色画像升级为带 source_refs 的人物事实库。
- LLM-as-judge 测评：引入语义等价判断，减少硬规则误判。
- scene 在线编辑：支持作者在导出前调整场景、对白和动作。
- 分章节重跑：只重跑某一章或某个 chunk，降低生成成本。
