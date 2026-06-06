# Backend Agent 指南

## 技术栈

- Spring Boot 4.0.6，Java 17。
- MyBatis Plus 3.5.16。
- PostgreSQL，建表脚本维护在 `src/main/resources/db/schema.sql`。
- DeepSeek 使用 OpenAI 兼容 HTTP 接口，不把 API Key 写入配置文件默认值。

## 目录约定

- `controller/`：HTTP 接口层，只做请求响应编排和基础错误转换。
- `service/`：业务接口。
- `service/impl/`：业务实现。
- `client/`：外部服务客户端，例如 DeepSeek API 调用。
- `constant/`：常量定义，按功能边界拆分并写清楚中文注释。
- `mapper/`：MyBatis Plus mapper。
- `model/entity/`：数据库实体。
- `model/dto/`：请求、响应和视图对象。

## 接口与数据规范

- 接口统一返回 `Result`。
- 删除小说使用软删除。
- 新增表结构时更新 `schema.sql`，字段使用 snake_case，Java 实体使用 camelCase。
- AI 生成结果中，`script_scenes.chunk_id` 表示 scene 首次创建来源；合并后的完整来源以 `source_refs_json` 为准。

## 本地配置

- 数据库连接默认读取 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，可使用 `application.yaml` 的默认本地值。
- DeepSeek API Key 使用环境变量：`DEEPSEEK_API_KEY`。
- DeepSeek 模型可通过 `DEEPSEEK_MODEL` 覆盖。

## 验证

- 后端改动至少运行：`.\mvnw.cmd test`。
- 移动类或改包名后，如遇旧 class 扫描冲突，运行：`.\mvnw.cmd clean test`。
- 涉及 AI 调用时，如无真实 API Key，可验证任务创建前置条件和编译测试；有 Key 时再做抽样实调。
