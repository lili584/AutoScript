# 剧本 YAML Schema

本文档定义“AI 小说转剧本工具”导出的 YAML 剧本结构。该 Schema 面向可编辑的剧本初稿，重点解决三个问题：

1. 保留小说到剧本的来源追溯，便于作者回看原文。
2. 用结构化字段表达场景、人物、动作和对白，便于继续编辑和导出。
3. 保持格式简单稳定，方便后端由 AI JSON 结果转换生成 YAML。

## 顶层结构

```yaml
schema_version: "1.0"

metadata:
  title: "长夜将明"
  source_type: "novel"
  language: "zh-CN"
  generated_at: "2026-06-05T10:00:00+08:00"
  generator: "AutoScript"

source:
  novel_id: "novel-001"
  title: "长夜将明"
  chapters:
    - index: 1
      title: "第一章 雨夜"
    - index: 2
      title: "第二章 旧信"

characters:
  - id: "character-lin-zhou"
    name: "林舟"
    aliases:
      - "林公子"
    role: "protagonist"
    description: "年轻书生，谨慎但富有同情心。"
    first_appearance:
      scene_id: "scene-001"

scenes:
  - id: "scene-001"
    order: 1
    chapter:
      index: 1
      title: "第一章 雨夜"
    title: "雨夜相遇"
    location: "城门外"
    time_of_day: "夜晚"
    summary: "林舟在雨夜遇到受伤的沈月，并决定帮她隐瞒行踪。"
    characters:
      - "character-lin-zhou"
      - "character-shen-yue"
    beats:
      - type: "action"
        text: "林舟撑伞站在城门下，雨水顺着伞沿落到青石路上。"
      - type: "dialogue"
        character_id: "character-shen-yue"
        character_name: "沈月"
        text: "别告诉任何人你见过我。"
      - type: "action"
        text: "林舟短暂迟疑后，将伞偏向沈月。"
    source_refs:
      - chapter_index: 1
        chapter_title: "第一章 雨夜"
        chunk_index: 1
        paragraph_start: 3
        paragraph_end: 8
```

## 字段说明

### `schema_version`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schema_version` | string | 是 | Schema 版本号。当前版本为 `"1.0"`。 |

设计原因：后续如果增加分镜、镜头、角色关系等字段，可以通过版本号兼容旧数据。

### `metadata`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `title` | string | 是 | 剧本标题，默认取小说标题。 |
| `source_type` | string | 是 | 来源类型，当前固定为 `"novel"`。 |
| `language` | string | 否 | 内容语言，例如 `"zh-CN"`。 |
| `generated_at` | string | 是 | 生成时间，使用 ISO 8601 格式。 |
| `generator` | string | 否 | 生成工具名称，例如 `"AutoScript"`。 |

设计原因：`metadata` 保存导出文件的基础信息，方便作者在本地文件中识别来源、时间和生成工具。

### `source`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `novel_id` | string | 否 | 后端小说 ID。 |
| `title` | string | 是 | 原小说标题。 |
| `chapters` | array | 是 | 原小说章节列表。 |
| `chapters[].index` | number | 是 | 章节序号，从 1 开始。 |
| `chapters[].title` | string | 是 | 章节标题。 |

设计原因：小说转剧本不是一次性结果，作者常需要回到原章节继续修改。`source` 提供全局来源信息，`source_refs` 提供场景级追溯。

### `characters`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 人物唯一 ID。 |
| `name` | string | 是 | 人物显示名。 |
| `aliases` | array | 否 | 人物别名、称呼或简称。 |
| `role` | string | 否 | 人物角色，例如 `protagonist`、`supporting`、`antagonist`。 |
| `description` | string | 否 | 人物简介。 |
| `first_appearance.scene_id` | string | 否 | 首次出现的场景 ID。 |

设计原因：人物信息独立放在 `characters` 中，可以避免每个场景重复描述人物，也方便后续增加人物表、角色关系和前端筛选。

### `scenes`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 场景唯一 ID。 |
| `order` | number | 是 | 场景顺序，从 1 开始。 |
| `chapter.index` | number | 是 | 来源章节序号。 |
| `chapter.title` | string | 是 | 来源章节标题。 |
| `title` | string | 是 | 场景标题。 |
| `location` | string | 否 | 场景地点。 |
| `time_of_day` | string | 否 | 场景时间，例如 `清晨`、`夜晚`。 |
| `summary` | string | 是 | 场景概要。 |
| `characters` | array | 否 | 场景涉及的人物 ID 列表。 |
| `beats` | array | 是 | 场景内按顺序排列的动作、对白和转场。 |
| `source_refs` | array | 否 | 场景对应的原文位置。 |

设计原因：剧本的主要组织单元是场景，`scenes` 作为主结构最适合预览、编辑和导出。每个场景携带章节信息，既能保持原小说顺序，也允许后续调整场景顺序。

### `beats`

`beats` 表示场景内部的连续内容。当前支持三种类型：

| `type` | 必填字段 | 说明 |
| --- | --- | --- |
| `action` | `text` | 动作、环境、人物行为或舞台说明。 |
| `dialogue` | `character_id`、`character_name`、`text` | 人物对白。 |
| `transition` | `text` | 转场说明，例如切至、闪回、时间跳转。 |

示例：

```yaml
beats:
  - type: "action"
    text: "屋内只剩一盏油灯。"
  - type: "dialogue"
    character_id: "character-lin-zhou"
    character_name: "林舟"
    text: "这封信是谁留下的？"
  - type: "transition"
    text: "切至三日前的驿站。"
```

设计原因：动作和对白如果拆成两个独立数组，会丢失原本的阅读顺序。`beats` 用混排结构保留剧本节奏，也更适合前端编辑器逐条修改。

### `source_refs`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `chapter_index` | number | 是 | 来源章节序号。 |
| `chapter_title` | string | 否 | 来源章节标题。 |
| `chunk_index` | number | 是 | 来源分块序号。 |
| `paragraph_start` | number | 否 | 来源段落起点。 |
| `paragraph_end` | number | 否 | 来源段落终点。 |

设计原因：AI 生成内容需要可追溯。`source_refs` 让作者知道每个场景来自哪一章、哪一个分块，便于人工校对和重新生成。

## 约束规则

1. `schema_version` 必须存在。
2. `scenes` 必须存在，且至少包含一个场景。
3. `scenes[].id` 在同一文件内必须唯一。
4. `scenes[].order` 应按剧本播放顺序递增。
5. `characters[].id` 在同一文件内必须唯一。
6. `scenes[].characters` 应引用 `characters[].id`。
7. `beats[].type` 只能是 `action`、`dialogue` 或 `transition`。
8. 当 `beats[].type` 为 `dialogue` 时，必须包含 `character_id`、`character_name` 和 `text`。

## 与 AI JSON 中间结果的关系

后端可以要求 AI 先输出 JSON，再转换为本 YAML Schema。建议 AI JSON 保持与 `scenes` 接近的结构，后端负责：

1. 校验 JSON 格式。
2. 生成稳定的 `scene.id` 和 `character.id`。
3. 根据 `scene_id`、`summary`、`dialogue.text` 去重。
4. 汇总人物到 `characters`。
5. 补充 `metadata`、`source` 和 `source_refs`。
6. 输出最终 YAML。

这样设计可以降低 AI 直接输出 YAML 时的格式错误风险，也让后端拥有更稳定的校验和修复空间。

## 设计取舍

### 为什么以场景为主结构

小说章节通常按照叙事推进组织，而剧本更关注场景切换、人物行动和对白节奏。将 `scenes` 作为主结构，可以让生成结果更接近剧本初稿，也方便前端按场景展示和编辑。

### 为什么人物独立建表

人物会在多个场景重复出现。如果每个场景都保存完整人物描述，会产生大量重复，也容易在 AI 多次生成时出现不一致。独立的 `characters` 可以统一人物名称、别名和简介。

### 为什么使用 `beats`

剧本内容需要保持动作和对白的先后顺序。`beats` 用一个数组混合表达 `action`、`dialogue` 和 `transition`，既简单，又能保留剧本阅读节奏。

### 为什么保留来源追溯

AI 改编结果需要作者继续打磨。`source_refs` 可以帮助作者快速定位原文依据，也方便后端支持“只重新生成某一章或某一块”的后续功能。

### 为什么不在第一版加入镜头级字段

镜头、景别、机位、音效等字段更适合分镜或导演稿。当前工具目标是降低小说作者改编门槛，因此第一版只输出可编辑剧本初稿，避免 Schema 过重。
