# LaMelo Agent

LaMelo Agent 是一个 Java 多模块 AI Agent 与 RAG 项目，包含对话工作台、文档知识库和管理控制台。

[GitHub 仓库](https://github.com/Irving711/LaMelo-Agent) · [Apache License 2.0](LICENSE)

## 功能概览

- ReAct Agent、工具调用、联网搜索与 SSE 流式对话
- 文档上传、异步解析、组合式切块和向量化
- 知识域/主题/文档三级路由，Qdrant 稠密与稀疏向量混合检索
- RRF 融合、可选 Rerank、证据预算控制和无证据短路
- 会话记忆、Checkpoint、执行轨迹和管理控制台
- Skills 与 MCP 扩展示例，以及多个 Spring AI 示例模块

## 项目结构

| 路径 | 内容 |
| --- | --- |
| `lamelo-agent-business/lamelo-agent-business-chat` | Spring Boot 对话和知识管理应用 |
| `lamelo-agent-common` | 公共组件与 Web 基础能力 |
| `lamelo-agent-id-generator-framework` | ID 生成能力 |
| `lamelo-agent-redisson-framework`、`lamelo-agent-redis-tool-framework` | Redis/Redisson 支持 |
| `ai-example` | Spring AI、RAG、MCP、记忆等示例 |
| `vue` | Vue 3 前端与管理控制台 |
| `sql` | MySQL 安装脚本与旧 PostgreSQL 数据迁移参考脚本 |
| `scripts/migrate_qdrant.py` | 旧索引数据迁移到 Qdrant 的独立工具 |

## 环境要求

- JDK 17、Maven 3.8+
- Node.js 与 npm（前端）
- MySQL、Redis、MinIO、Qdrant；Neo4j 可选，默认关闭
- 模型服务密钥；联网搜索和 Rerank 按需配置

应用默认监听 `9082`。后端主应用通过 `application.yaml` 与 `application-{dev,test,prod}.yaml` 区分公共、开发、测试和生产配置；当前默认 profile 为 `prod`，本地开发请显式设置 `SPRING_PROFILES_ACTIVE=dev`。测试环境所需的环境变量名称可查看仓库中的 `application-test.yaml`。

## 配置与启动

在 PowerShell 中设置必需的模型/搜索密钥并启动本地开发 profile：

```powershell
$env:LAMELO_AGENT_ALI_BAI_LIAN_API_KEY = "<DashScope API key>"
$env:LAMELO_AGENT_TAVILY_API_KEY = "<Tavily API key>"
$env:SPRING_PROFILES_ACTIVE = "dev"
```

按需设置 `LAMELO_AGENT_RERANK_API_KEY`。开发环境的本地服务默认连接信息仅保留在 `application-dev.yaml`；测试和生产 profile 要求通过环境变量提供基础设施地址及凭据。不要把真实密钥写入仓库。

开发环境默认 MySQL 库为 `lamelo_agent`。新环境按顺序执行：

1. 启动 MySQL，依次执行 `sql/Mysql/create_database_mysql.sql` 和 `sql/Mysql/create_table_mysql.sql`。建表脚本已经包含平台账号、角色、会话归属和微信身份表，可直接重复执行；不会写入默认管理员，若旧版本已写入公开默认哈希，重跑脚本会停用该账号。原先的拆分迁移脚本已合并并移除。
   本地开发配置默认管理员为 `admin/admin123`（配置在 `application.yaml`，首次登录时创建或恢复账号）。生产部署必须设置 `LAMELO_AGENT_ADMIN_USERNAME` 和强密码对应的 `LAMELO_AGENT_ADMIN_PASSWORD`，或设置 `LAMELO_AGENT_ADMIN_PASSWORD_HASH` 覆盖默认值。若使用旧版脚本产生的 `admin` 账号，首次按当前配置登录时会重置密码并启用账号。
2. 启动 Redis、MinIO 和 Qdrant；本地地址与账号默认值位于 `application-dev.yaml`。Qdrant 需支持稀疏向量的 IDF 修正和 Query API。
3. 如需保留旧 pgvector 与 Elasticsearch 索引，先使用 `scripts/migrate_qdrant.py` 重建并核对数据，再切换 Qdrant 集合别名；操作说明见 `scripts/README-qdrant-migration.md`。
4. 启动后端和前端：

```powershell
mvn -pl lamelo-agent-business/lamelo-agent-business-chat -am spring-boot:run
```

另开终端：

```powershell
cd vue
npm install
npm run dev
```

生产构建：

```powershell
mvn -DskipTests compile
cd vue
npm run build
```

前端 API 基地址可通过 `VITE_LAMELO_AGENT_API_BASE_URL` 配置；留空时使用当前站点地址。

