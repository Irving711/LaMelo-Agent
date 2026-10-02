# LaMelo Agent 微信小程序

本目录是独立的 UniApp Vue 3 微信小程序端，Web 端代码保持不变。

## 开发

```bash
npm install
npm run test:unit
npm run dev:mp-weixin
```

在微信开发者工具中导入 `dist/dev/mp-weixin`。发布前在 `src/manifest.json` 填写真实 AppID，并将 API 地址配置为 HTTPS 域名。

## 配置与发布

复制 `.env.example` 为 `.env.production`，设置 `VITE_LAMELO_AGENT_API_BASE_URL`。在 `src/manifest.json` 的 `mp-weixin.appid` 填写小程序 AppID。小程序后台需要配置 request 和 uploadFile 的 HTTPS 合法域名。后端通过 `LAMELO_AGENT_WECHAT_APP_ID` 和 `LAMELO_AGENT_WECHAT_APP_SECRET` 环境变量接收微信凭据，AppSecret 不得写入小程序工程。

首次部署执行 `sql/Mysql/create_database_mysql.sql` 和 `sql/Mysql/create_table_mysql.sql`，再使用测试环境的管理员账号登录。若需要普通用户测试账号，应在 `lamelo_agent_account` 与 `lamelo_agent_account_role` 中建立启用账号和角色，密码使用 BCrypt 哈希；不要把测试密码写入仓库。测试绑定流程需准备尚未绑定的微信测试号。

```bash
node scripts/check-config.mjs --production
npm run build:mp-weixin
```

`build:mp-weixin` 会先执行生产配置检查；本地仅验证编译时使用 `npm run build:mp-weixin:dev`。

构建产物位于 `dist/build/mp-weixin`。导入微信开发者工具后，再用真机检查微信登录、账号登录和绑定、流式对话、停止与重试、历史会话、文档上传、管理员导航、切后台、键盘遮挡与弱网错误。

真实微信凭据、MySQL 和真机联调不包含在本地单元测试中。
