# AutoScript Agent 指南

## 项目结构

- `bankend/`：Spring Boot 后端，提供小说管理、Markdown 解析、chunk 分片、DeepSeek AI 场景抽取接口。
- `frontend/`：Vue 3 + Vite 前端，提供小说管理、上传追加、章节解析、AI 分析结果展示。
- `docs/`：项目设计文档，包括剧本 YAML Schema 和后续优化记录。
- `.github/pull_request_template.md`：PR 模板，所有 PR 都应填写中文标题和描述。

## 通用约束

- 每个 PR 只做一件事，功能、重构、文档尽量拆开。
- 不提交 API Key、数据库密码、token 等密钥；DeepSeek Key 使用 `DEEPSEEK_API_KEY` 环境变量。
- 不随意回滚他人改动；提交前确认 `git status`。
- 提交信息使用中文，保留 conventional commit 前缀，例如 `feat:`、`fix:`、`refactor:`、`docs:`。
- 代码变更后优先跑对应模块测试或构建，并在 PR 描述写清楚验证方式。

## 启动与验证命令

- 后端测试：`cd bankend && .\mvnw.cmd test`
- 后端启动：`cd bankend && .\mvnw.cmd spring-boot:run`
- 后端 Linux/macOS 测试：`cd bankend && ./mvnw.sh test`
- Redis 启动：`docker compose -f docker-compose.redis.yml up -d`
- 前端安装：`cd frontend && npm install`
- 前端启动：`cd frontend && npm run dev`
- 前端构建：`cd frontend && npm run build`

## Git 与 PR 规范

- 默认从当前功能基线新建 `codex/` 前缀分支。
- PR 标题和描述使用中文。
- PR 描述包含：功能描述、实现思路、测试方式、影响范围。
- 代码推送前确认工作区只包含本 PR 范围内的文件。
