# AutoScript - AI 小说转剧本工具

> 演示视频：https://www.bilibili.com/video/BV1KnEt6MEye/?vd_source=c80ba0f6fecbebaada6d9e607c42e018
> 
> 作品方向：AI 小说转剧本工具  
> 
> Schema 文档：[SCRIPT_YAML_SCHEMA.md](SCRIPT_YAML_SCHEMA.md)

AutoScript 是一款面向小说作者的 AI 辅助剧本创作工具。它可以将 3 个章节以上的小说文本自动解析、分块、调用 AI 转换为结构化 scenes，并最终导出可编辑的剧本 YAML 初稿。

## 一句话介绍

作者上传 Markdown 小说后，AutoScript 会按章节和段落拆分文本，调用 DeepSeek 生成剧本场景、动作、对白、人物画像和来源追溯信息，并提供 YAML 导出与多版本测评对比。

## STAR 法介绍项目

### Situation 背景

很多小说作者希望把自己的作品改编成剧本，但小说和剧本的表达方式不同。小说更偏叙事，剧本更关注场景、人物行动、对白节奏和可拍摄结构。人工改编需要大量拆场景、提对白、整理人物和校对原文来源，门槛较高。

### Task 目标

本项目的目标是在 3 日限时实战内完成一款 AI 辅助剧本创作工具，让作者可以把 3 章以上小说快速转换成结构化 YAML 剧本初稿，并且保留可追溯、可编辑、可测评的结果。

### Action 实现

项目按 PR 持续拆分交付，完成了以下核心能力：

- 小说管理：新建、列表、详情、软删除、原文保存。
- Markdown 导入：支持 `.md` 上传创建、覆盖、追加，也支持文本框追加。
- 章节解析：按 `#` 和 `##` 识别小说标题与章节标题。
- chunk 分片：按段落拆分，每块控制在 1500-2000 字，并带上下文。
- AI 场景抽取：调用 DeepSeek API，把 chunk 转成 scenes JSON。
- rolling summary：跨 chunk 传递章节状态，改善长场景连续性。
- 去重与清洗：处理重复 scene、重复 dialogue、空 action、误转对白等问题。
- 角色画像：scene 生成后再额外汇总人物 aliases、role、description。
- YAML 导出：汇总 metadata、source、characters、scenes，生成可编辑 YAML。
- 测评对比：支持上传 1-5 份 YAML，比较 6 个指标和优先修改建议。

### Result 结果

最终效果是：用户可以从小说原文出发，经过“解析章节 → AI 分析 → YAML 导出 → 测评对比”的完整链路，得到结构化剧本初稿，并能根据测评建议继续打磨。

## 核心链路

```text
Markdown 小说
  -> 小说管理与原文保存
  -> 章节解析
  -> chunk 分片
  -> DeepSeek scenes 抽取
  -> 后端清洗 / 去重 / source_refs 修正
  -> 角色画像生成
  -> YAML 汇总导出
  -> YAML 多版本测评对比
```

### 1. Markdown 解析

系统约定：

- `#` 表示小说标题。
- `##` 表示章节标题。
- 章节正文按空行拆段落。

如果第一个 `##` 前有正文，会归入“正文前言”。如果全文没有 `##`，后端会提示用户补充章节标题。

### 2. chunk 分片

每章按段落拆成 chunk：

- chunk 目标长度为 1500-2000 字。
- 单段超过 2000 字时不强行切断。
- 后续 chunk 会携带前一 chunk 的上下文。
- context 使用更稳的混合策略，避免短段落导致上下文不足。

### 3. AI scenes 抽取

后端按 chunk 顺序调用 DeepSeek，要求输出 JSON：

```json
{
  "scenes": [],
  "chapter_state": {}
}
```

`scenes` 保存场景、地点、时间、summary、人物、beats 和 source_refs。  
`chapter_state` 维护章节级 rolling summary，包括当前地点、人物、冲突、已发生事件和未解决问题。

### 4. 质量兜底

AI 输出后，后端会做确定性清洗：

- 过滤空 action/transition。
- 将非直接引号对白降级为 action。
- scene_id 相同去重。
- dialogue.character + dialogue.text 去重。
- dialogue.text 完全相同去重。
- summary 高相似度合并。
- source_refs 过宽时按 beats 文本反推段落范围。
- chunk 输出质量不合格时自动 retry。

### 5. 异步任务与并发控制

AI 分析是长任务，系统使用：

- Java 21 虚拟线程执行后台任务。
- Redis 保存运行态和进度。
- Redisson 管理同小说任务锁。
- 全局 DeepSeek 请求并发控制，避免 API 消耗失控。

同一小说同一时间只允许一个 AI 分析任务运行，避免 rolling summary 和 scenes 落库顺序混乱。

### 6. YAML 导出

后端不让 AI 直接输出 YAML，而是采用：

```text
AI JSON -> 后端校验与汇总 -> YAML
```

这样可以减少 YAML 格式错误，并由后端统一生成稳定的 character_id、scene order、source_refs 和 metadata。

### 7. 测评对比

测评模块不调用 AI，使用确定性规则评估 YAML：

- 对白召回率
- 对白精确率
- 动作覆盖率
- 角色一致性
- 忠实度
- 结构完整性

前端支持上传 1-5 份 YAML 做横向比较，标记总分和小指标的最高/最低，并展示优先修改建议。

## 项目亮点

### AI 方向亮点

1. **两阶段生成**

   AI 先输出 JSON，后端再汇总成 YAML，降低格式漂移风险。

2. **rolling summary**

   每个 chunk 处理后保存章节状态，下一个 chunk 可以看到前文的地点、人物、冲突和未解决问题。

3. **跨 chunk continuation 合并**

   如果长场景被 chunk 截断，AI 可标记 continuation，后端再校验是否合并到上一场景。

4. **质量校验与 retry**

   后端发现空 action、空 scenes、source_refs 退化等问题时，会要求 AI 重试一次。

5. **source_refs 精细化**

   后端不完全相信 AI 的段落范围，会根据 dialogue/action 文本反推实际段落，避免多个 scene 全部指向同一个 chunk 范围。

6. **角色画像后处理**

   scenes 生成完成后，再从全局 scene 摘要生成人物画像，不影响 scene 抽取主链路。

### 工程方向亮点

1. **PR 持续交付**

   功能按 PR 拆分：小说管理、上传追加、章节分块、AI 抽取、YAML 导出、Redis 任务运行时、测评模块、角色画像等，避免最后一次性导入。

2. **长任务运行态设计**

   PostgreSQL 保存最终数据，Redis 保存高频变化的任务运行态和锁。

3. **并发控制**

   通过 Redisson 锁限制同小说任务，通过全局并发限制控制 DeepSeek 请求数量。

4. **测评系统**

   不只生成 YAML，还提供质量评估和修改建议，方便作者比较不同版本。

## 演示视频建议流程

录屏时建议按下面顺序讲解，覆盖评审关注的功能完整度、技术链路和演示效果。

### 0. 开场介绍

建议讲：

> 大家好，这是 AutoScript，一个 AI 小说转剧本工具。它面向小说作者，目标是把 3 章以上的小说文本自动转换成结构化 YAML 剧本初稿，并保留人物、场景、对白、动作和来源追溯信息。

### 1. 展示项目结构

打开仓库根目录，展示：

- `bankend/`：Spring Boot 后端。
- `frontend/`：Vue 前端。
- `SCRIPT_YAML_SCHEMA.md`：剧本 YAML Schema 文档。
- `.github/pull_request_template.md`：PR 模板。

建议讲：

> 项目采用前后端分离，后端负责小说解析、AI 调用、任务调度、YAML 导出和测评；前端负责小说管理、AI 进度展示、YAML 下载和多文件测评。

### 2. 展示小说管理和 Markdown 上传

操作：

1. 打开前端页面。
2. 上传或选择一篇 3 章以上小说。
3. 展示原始 Markdown 文本和右侧大纲。

建议讲：

> Markdown 中 `#` 是小说标题，`##` 是章节标题。系统会按章节和段落进行解析。

### 3. 展示章节解析与 chunk

操作：

1. 点击“解析章节”。
2. 展示章节数量和 chunk 数量。
3. 展开章节查看 chunk 字数、段落范围和 context。

建议讲：

> 每章会按段落拆 chunk，每块控制在 1500 到 2000 字左右。后续 chunk 会带上前文 context，避免 AI 缺上下文。

### 4. 展示 AI 分析

操作：

1. 点击“开始 AI 分析”。
2. 展示任务状态和进度条。
3. 展示生成后的 scene 列表。

建议讲：

> AI 分析是异步任务。后端使用 Redis 保存运行态，Redisson 做同小说任务锁，并通过并发控制限制 DeepSeek 请求数量。

### 5. 展示 YAML 导出

操作：

1. 点击“预览 YAML”。
2. 展示 `metadata`、`characters`、`scenes`。
3. 点击“下载 YAML”。

建议讲：

> AI 不直接输出最终 YAML，而是先输出 JSON，后端做清洗、去重、角色汇总和来源追溯，再生成 YAML。

### 6. 展示 YAML Schema 文档

操作：

1. 打开 `SCRIPT_YAML_SCHEMA.md`。
2. 讲解顶层结构和设计原因。

建议讲：

> Schema 以 scenes 为主结构，因为剧本的基本单位是场景；characters 独立放置，避免人物信息重复；beats 混合 action/dialogue/transition，保留剧本节奏；source_refs 用于回溯小说原文。

### 7. 展示测评对比

操作：

1. 切换到“测评对比”。
2. 选择小说。
3. 上传 1-5 份 YAML。
4. 点击测评。
5. 展示总分、小指标、最高/最低标记和优先修改建议。
6. 点击“评分规则”弹窗。

建议讲：

> 测评模块不调用 AI，而是使用确定性规则检查对白召回、对白精确率、动作覆盖、角色一致性、忠实度和结构完整性。

### 8. 收尾

建议讲：

> 这个工具目前已经完成从小说输入到 YAML 剧本导出，再到质量测评的完整闭环。后续可以继续增加 scene 在线编辑、角色画像编辑、分章节重跑和导出历史管理。

## 启动方式

### 环境要求

- JDK 21
- Node.js
- PostgreSQL
- Redis
- DeepSeek API Key

### 启动 Redis

```powershell
docker compose -f docker-compose.redis.yml up -d
```

### 启动后端

```powershell
cd bankend
.\mvnw.cmd spring-boot:run
```

### 启动前端

```powershell
cd frontend
npm install
npm run dev
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
DEEPSEEK_API_KEY=你的 DeepSeek API Key
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

前端：

```powershell
cd frontend
npm run build
```

## 第三方依赖

主要依赖：

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
- DeepSeek API

原创实现部分：

- Markdown 章节解析和 chunk 分片。
- AI scenes JSON 协议。
- rolling summary 和 continuation 合并。
- AI 输出清洗、去重和 source_refs 反推。
- YAML 汇总导出。
- YAML Schema 设计。
- YAML 测评 checker。
- 前端多文件测评对比。

## 后续优化方向

- 支持 scene 在线编辑。
- 支持角色画像手动编辑。
- 支持只重跑某一章或某个 chunk。
- 支持 YAML 导出历史。
- 支持更多模型提供方。
