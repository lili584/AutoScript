# AutoScript 剧本 YAML Schema

本文档定义 AutoScript 导出的剧本 YAML 格式，并说明每个 key 的含义和 Schema 设计原因。该格式面向小说作者的“可编辑剧本初稿”，重点保留场景、人物、动作、对白和原文来源追溯。

## 设计目标

1. **可编辑**：YAML 文本结构清晰，作者可以直接阅读和修改。
2. **可追溯**：每个 scene 都能回到小说章节、chunk 和段落范围。
3. **可扩展**：通过 `schema_version` 支持后续加入分镜、镜头、角色关系等字段。
4. **可校验**：字段结构稳定，便于后端导出、前端展示和测评模块检查。

## 顶层结构

```yaml
schema_version: "1.0"

metadata:
  title: ""
  source_type: "novel"
  language: "zh-CN"
  generated_at: ""
  generator: "AutoScript"

source:
  novel_id: ""
  title: ""
  chapters: []

characters: []

scenes: []
```

顶层包含 5 个 key：

| key | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schema_version` | string | 是 | YAML Schema 版本号，当前为 `"1.0"`。 |
| `metadata` | object | 是 | 导出文件本身的信息。 |
| `source` | object | 是 | 原小说来源信息。 |
| `characters` | array | 是 | 全局人物表。 |
| `scenes` | array | 是 | 剧本场景列表。 |

设计原因：顶层结构把“文件信息、原文信息、人物信息、场景正文”分开，方便作者查看，也方便后端和测评模块分别处理。

## `schema_version`

```yaml
schema_version: "1.0"
```

| key | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schema_version` | string | 是 | Schema 版本。 |

设计原因：如果后续新增镜头、分镜、音效、角色关系等字段，可以通过版本号区分旧格式和新格式。

## `metadata`

```yaml
metadata:
  title: "倒影"
  source_type: "novel"
  language: "zh-CN"
  generated_at: "2026-06-07T17:49:44.6455175+08:00"
  generator: "AutoScript"
```

| key | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `metadata.title` | string | 是 | 剧本标题，默认取小说标题。 |
| `metadata.source_type` | string | 是 | 来源类型，当前固定为 `"novel"`。 |
| `metadata.language` | string | 否 | 内容语言，例如 `"zh-CN"`。 |
| `metadata.generated_at` | string | 是 | YAML 生成时间，使用 ISO 8601 格式。 |
| `metadata.generator` | string | 否 | 生成工具名称，当前为 `"AutoScript"`。 |

设计原因：metadata 帮助作者识别这个 YAML 文件是什么时候、由哪个工具、基于什么内容生成的。

## `source`

```yaml
source:
  novel_id: "3"
  title: "倒影"
  chapters:
    - index: 1
      title: "第一章 重逢"
```

| key | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source.novel_id` | string | 否 | 后端小说 ID。 |
| `source.title` | string | 是 | 原小说标题。 |
| `source.chapters` | array | 是 | 原小说章节列表。 |
| `source.chapters[].index` | number | 是 | 章节序号，从 1 开始。 |
| `source.chapters[].title` | string | 是 | 章节标题。 |

设计原因：source 保存全局来源信息。剧本场景可能会被作者调整顺序，但 source 可以让作者始终知道原小说章节结构。

## `characters`

```yaml
characters:
  - id: "character-林屿安"
    name: "林屿安"
    aliases: []
    role: "主角"
    description: "林屿安是一名建筑师，性格内敛敏感，对过去的情感难以释怀。他曾在六年前与顾衍川有过约定，如今重逢后内心波动，但努力保持冷静。"
    first_appearance:
      scene_id: "chapter-1-chunk-1-scene-1"
```

| key | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `characters[].id` | string | 是 | 人物唯一 ID，供 scene 和 dialogue 引用。 |
| `characters[].name` | string | 是 | 人物显示名。 |
| `characters[].aliases` | array | 否 | 人物别名、简称、称呼。 |
| `characters[].role` | string | 否 | 人物定位，例如“主角”“配角-客户”“功能性角色-前台”。 |
| `characters[].description` | string | 否 | 人物简介，通常包含身份、性格、背景或关系。 |
| `characters[].first_appearance` | object | 否 | 人物首次出场信息。 |
| `characters[].first_appearance.scene_id` | string | 否 | 人物首次出现的 scene ID。 |

设计原因：人物在多个场景中会重复出现。如果每个 scene 都重复写完整人物描述，会造成冗余，也容易不一致。因此 characters 独立成全局人物表，scene 中只引用人物 ID。

## `scenes`

```yaml
scenes:
  - id: "chapter-1-chunk-1-scene-1"
    order: 1
    chapter:
      index: 1
      title: "第一章 重逢"
    title: "咖啡厅偶遇"
    location: "咖啡厅"
    time_of_day: "白天"
    summary: "林屿安在咖啡厅与客户开会时，透过落地窗瞥见一个酷似顾衍川的背影，心神不宁。"
    characters:
      - "character-林屿安"
      - "character-客户"
    beats: []
    source_refs: []
```

| key | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `scenes[].id` | string | 是 | 场景唯一 ID。 |
| `scenes[].order` | number | 是 | 场景顺序，从 1 开始。 |
| `scenes[].chapter` | object | 是 | 场景来源章节。 |
| `scenes[].chapter.index` | number | 是 | 来源章节序号。 |
| `scenes[].chapter.title` | string | 是 | 来源章节标题。 |
| `scenes[].title` | string | 是 | 场景标题。 |
| `scenes[].location` | string | 否 | 场景地点。 |
| `scenes[].time_of_day` | string | 否 | 场景时间，例如白天、夜晚、清晨。 |
| `scenes[].summary` | string | 是 | 场景概要。 |
| `scenes[].characters` | array | 否 | 当前场景涉及的人物 ID 列表。 |
| `scenes[].beats` | array | 是 | 场景内部动作、对白、转场的顺序列表。 |
| `scenes[].source_refs` | array | 否 | 当前场景对应的原文范围。 |

设计原因：剧本的核心单位是场景，不是小说章节。以 scenes 为主结构，更符合剧本改编和后续编辑流程。

## `beats`

`beats` 表示一个 scene 内部的连续内容，按出现顺序排列。

```yaml
beats:
  - type: "action"
    text: "林屿安把咖啡杯放回瓷盘，杯底磕出一声脆响。"
  - type: "dialogue"
    character_id: "character-客户"
    character_name: "客户"
    text: "林总？"
```

| key | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `beats[].type` | string | 是 | beat 类型，只能是 `action`、`dialogue`、`transition`。 |
| `beats[].text` | string | 是 | beat 文本。 |
| `beats[].character_id` | string | dialogue 必填 | 对白人物 ID。 |
| `beats[].character_name` | string | dialogue 必填 | 对白人物显示名。 |

beat 类型说明：

| type | 含义 | 示例 |
| --- | --- | --- |
| `action` | 动作、环境、人物反应、舞台说明 | “林屿安把咖啡杯放回瓷盘。” |
| `dialogue` | 角色直接对白 | “您继续，我在听。” |
| `transition` | 转场或时间跳转 | “切至六年前的傍晚。” |

设计原因：剧本的动作和对白必须保留顺序。如果把动作和对白拆成两个数组，会丢失节奏。beats 用混排数组保留原始阅读和表演顺序。

## `source_refs`

```yaml
source_refs:
  - chapter_index: 1
    chapter_title: "第一章 重逢"
    chunk_index: 1
    paragraph_start: 1
    paragraph_end: 5
```

| key | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source_refs[].chapter_index` | number | 是 | 来源章节序号。 |
| `source_refs[].chapter_title` | string | 否 | 来源章节标题。 |
| `source_refs[].chunk_index` | number | 是 | 来源 chunk 序号。 |
| `source_refs[].paragraph_start` | number | 否 | 来源段落起点。 |
| `source_refs[].paragraph_end` | number | 否 | 来源段落终点。 |

设计原因：AI 生成结果需要可校对。source_refs 可以让作者快速回到原文，检查某个场景是否遗漏、误解或过度改写。

## 完整示例片段

以下示例提取自本地导出文件：

```text
D:\download\倒影-剧本-202606071749.yaml
```

```yaml
schema_version: "1.0"

metadata:
  title: "倒影"
  source_type: "novel"
  language: "zh-CN"
  generated_at: "2026-06-07T17:49:44.6455175+08:00"
  generator: "AutoScript"

source:
  novel_id: "3"
  title: "倒影"
  chapters:
    - index: 1
      title: "第一章 重逢"
    - index: 2
      title: "第二章 裂痕"
    - index: 3
      title: "第三章 旧地"
    - index: 4
      title: "第四章 暗涌"
    - index: 5
      title: "第五章 破晓"

characters:
  - id: "character-林屿安"
    name: "林屿安"
    aliases: []
    role: "主角"
    description: "林屿安是一名建筑师，性格内敛敏感，对过去的情感难以释怀。他曾在六年前与顾衍川有过约定，如今重逢后内心波动，但努力保持冷静。"
    first_appearance:
      scene_id: "chapter-1-chunk-1-scene-1"
  - id: "character-客户"
    name: "客户"
    aliases: []
    role: "配角-客户"
    description: "林屿安在咖啡厅会面的客户，身份未详，仅出现在第一场对话中。"
    first_appearance:
      scene_id: "chapter-1-chunk-1-scene-1"

scenes:
  - id: "chapter-1-chunk-1-scene-1"
    order: 1
    chapter:
      index: 1
      title: "第一章 重逢"
    title: "咖啡厅偶遇"
    location: "咖啡厅"
    time_of_day: "白天"
    summary: "林屿安在咖啡厅与客户开会时，透过落地窗瞥见一个酷似顾衍川的背影，心神不宁。"
    characters:
      - "character-林屿安"
      - "character-客户"
    beats:
      - type: "action"
        text: "林屿安把咖啡杯放回瓷盘，杯底磕出一声脆响。"
      - type: "action"
        text: "客户滔滔不绝地讲着第三季度的投放策略，林屿安的目光却飘向窗外——一个穿灰蓝色风衣的身影正穿过斑马线。"
      - type: "action"
        text: "那个身影走到街角报刊亭旁，微微侧过头，阳光打在那张侧脸上。"
      - type: "dialogue"
        character_id: "character-客户"
        character_name: "客户"
        text: "林总？"
      - type: "dialogue"
        character_id: "character-林屿安"
        character_name: "林屿安"
        text: "您继续，我在听。"
    source_refs:
      - chapter_index: 1
        chapter_title: "第一章 重逢"
        chunk_index: 1
        paragraph_start: 1
        paragraph_end: 5
```

## 约束规则

1. `schema_version` 必须存在。
2. `metadata.title`、`metadata.generated_at` 必须存在。
3. `source.title`、`source.chapters` 必须存在。
4. `characters[].id` 在同一 YAML 文件内必须唯一。
5. `scenes[].id` 在同一 YAML 文件内必须唯一。
6. `scenes[].order` 应按剧本顺序递增。
7. `scenes[].characters` 应引用 `characters[].id`。
8. `beats[].type` 只能是 `action`、`dialogue`、`transition`。
9. 当 `beats[].type` 为 `dialogue` 时，必须包含 `character_id`、`character_name`、`text`。
10. `source_refs` 应尽量指向具体段落范围，不应全部退化为整章或整块。

## 与 AI JSON 中间结果的关系

AutoScript 不要求 AI 直接输出最终 YAML，而是采用：

```text
AI scenes JSON -> 后端校验清洗 -> 汇总 characters -> 导出 YAML
```

这样设计的原因：

- AI 直接输出 YAML 容易出现缩进、引号、数组结构错误。
- 后端可以统一生成稳定的 `character_id` 和 `scene order`。
- 后端可以做 dialogue 去重、source_refs 修正、空 action 过滤。
- 后端可以把角色画像独立汇总到 `characters`。
- 测评模块可以基于稳定 YAML Schema 做质量检查。

## 设计取舍

### 为什么不输出镜头级字段

本工具面向小说作者，目标是先生成可编辑剧本初稿。镜头、景别、机位、音效更适合导演稿或分镜稿，如果第一版加入会让 Schema 过重。

### 为什么保留 `character_name`

即使有 `character_id`，dialogue 中仍保留 `character_name`，方便作者直接阅读 YAML，不需要频繁跳到 characters 表查 ID。

### 为什么保留 `source_refs`

AI 改编结果不是最终稿，作者需要校对。source_refs 可以快速定位原文段落，也支持后续“只重跑某个章节或 chunk”的扩展。

### 为什么 `characters` 独立于 `scenes`

人物信息是跨场景复用的。独立 characters 表可以减少重复，并降低 AI 多次生成导致的人物信息不一致。
