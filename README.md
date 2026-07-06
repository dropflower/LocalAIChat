# PersonalAIApp

基于本地 Ollama 的智能对话应用，前后端分离架构，支持多模型切换、流式输出、深度思考展示和联网搜索。

## 功能特性

- **多模型对话** — 动态获取 Ollama 本地模型列表，一键切换对话模型
- **流式输出** — 基于 SSE (Server-Sent Events) 实时逐 Token 推送，响应延迟极低
- **深度思考** — 支持 DeepSeek-R1、Qwen3.5 等推理模型，实时展示思考过程（可折叠）
- **联网搜索** — 基于 Bing 搜索引擎，搜索结果注入 AI 上下文，提供来源链接反馈
- **会话管理** — 创建/重命名/置顶/删除会话，支持按标题模糊搜索
- **消息持久化** — MySQL 存储 + GZIP 压缩，支持上下文滑动窗口（最近 20 轮）
- **Redis 缓存** — 模型列表缓存（5 min TTL）、会话消息缓存（30 min TTL）
- **API Key 认证** — 请求头 `X-API-Key` 校验，单用户场景轻量级安全控制

## 技术栈

| 层级     | 技术                          | 版本         |
| ------ | --------------------------- | ---------- |
| 前端框架   | React + TypeScript          | React 18   |
| UI 组件库 | Ant Design                  | 5.x        |
| 状态管理   | Zustand                     | 5.x        |
| 构建工具   | Vite                        | 6.x        |
| 测试框架   | Vitest + Testing Library    | Vitest 2.x |
| 后端框架   | Spring Boot                 | 3.2.6      |
| 响应式    | Spring WebFlux (WebClient)  | —          |
| ORM    | Spring Data JPA + Hibernate | —          |
| 缓存     | Spring Data Redis (Lettuce) | —          |
| 数据库    | MySQL                       | 8.0+       |
| AI 服务  | Ollama (本地部署)               | —          |
| Java   | JDK                         | 21         |

## 项目结构

```
PersonalAIApp/
├── BackEnd/                          # 后端 Spring Boot 项目
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/aiapp/
│       │   │   ├── AiAppApplication.java       # 启动类
│       │   │   ├── config/
│       │   │   │   ├── RedisConfig.java         # Redis 序列化配置
│       │   │   │   └── WebConfig.java           # CORS + API Key 拦截器注册
│       │   │   ├── controller/
│       │   │   │   ├── AuthController.java      # 认证
│       │   │   │   ├── ChatController.java      # 对话（SSE 流式）
│       │   │   │   ├── HealthController.java    # 健康检查
│       │   │   │   ├── ModelController.java     # 模型管理
│       │   │   │   └── SessionController.java   # 会话 CRUD
│       │   │   ├── interceptor/
│       │   │   │   └── ApiKeyInterceptor.java   # API Key 认证拦截器
│       │   │   ├── model/
│       │   │   │   ├── ApiResponse.java         # 统一响应封装
│       │   │   │   ├── ChatRequest.java         # 对话请求 DTO
│       │   │   │   ├── Message.java             # 消息实体
│       │   │   │   ├── ModelConfig.java         # 模型配置实体
│       │   │   │   └── Session.java             # 会话实体
│       │   │   ├── repository/
│       │   │   │   ├── MessageRepository.java
│       │   │   │   ├── ModelConfigRepository.java
│       │   │   │   └── SessionRepository.java
│       │   │   └── service/
│       │   │       ├── ChatService.java         # 对话核心逻辑
│       │   │       ├── CleanupService.java      # 过期会话清理
│       │   │       ├── ModelService.java        # 模型管理
│       │   │       ├── OllamaClientService.java # Ollama API 客户端
│       │   │       ├── SessionService.java      # 会话管理
│       │   │       └── WebSearchService.java    # Bing 联网搜索
│       │   └── resources/
│       │       ├── application.yml              # 主配置
│       │       ├── application-dev.yml          # 开发环境配置（不入库）
│       │       └── schema.sql                   # 数据库建表脚本
│       └── test/
│           ├── java/com/aiapp/                  # 单元测试 + 集成测试
│           └── resources/application-test.yml   # 测试配置（H2 内存库）
├── FrontEnd/                         # 前端 React 项目
│   ├── package.json
│   ├── vite.config.ts
│   ├── vitest.config.ts
│   └── src/
│       ├── App.tsx                              # 根组件（路由切换）
│       ├── main.tsx                             # 入口
│       ├── components/
│       │   ├── ChatArea.tsx                     # 对话区域（消息列表 + 思考面板）
│       │   ├── ChatBubble/ChatBubble.tsx        # 消息气泡
│       │   ├── ChatInput/ChatInput.tsx          # 输入框
│       │   ├── Layout/ChatLayout.tsx            # 主布局
│       │   ├── LoginPage.tsx                    # 登录页
│       │   ├── ModelSelector/ModelSelector.tsx  # 模型选择器
│       │   └── SessionList/SessionList.tsx      # 会话列表
│       ├── services/api.ts                      # Axios API 封装
│       ├── stores/appStore.ts                   # Zustand 全局状态
│       ├── types/index.ts                       # TypeScript 类型定义
│       ├── utils/modelUtils.ts                  # 模型工具函数
│       └── test/                                # 前端测试
├── init_db.bat                       # Windows 数据库初始化脚本
├── start.bat                         # Windows 一键启动脚本
└── .gitignore
```

## 环境要求

| 依赖      | 最低版本 | 说明                |
| ------- | ---- | ----------------- |
| JDK     | 21   | 后端运行时             |
| Node.js | 18+  | 前端构建              |
| MySQL   | 8.0  | 数据库               |
| Redis   | 7.x  | 缓存                |
| Ollama  | —    | AI 模型服务，需提前拉取模型   |
| Maven   | 3.8+ | 后端构建（或使用项目内 mvnw） |

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/dropflower/LocalAIChat.git
cd PersonalAIApp
```

### 2. 初始化数据库

双击运行 `init_db.bat`，按提示输入 MySQL 用户名和密码，脚本将自动创建 `smart_chat` 数据库及数据表。

或手动执行：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS smart_chat DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p smart_chat < BackEnd/src/main/resources/schema.sql
```

### 3. 启动依赖服务

确保以下本地服务已运行：

- **MySQL** — `localhost:3306`
- **Redis** — `localhost:6379`
- **Ollama** — `localhost:11434`（至少拉取一个模型，如 `ollama pull qwen2.5:7b`）

### 4. 配置环境变量

后端配置通过环境变量注入敏感信息：

| 变量名              | 说明        | 示例              |
| ---------------- | --------- | --------------- |
| `MYSQL_USERNAME` | MySQL 用户名 | `root`          |
| `MYSQL_PASSWORD` | MySQL 密码  | `your_password` |
| `AI_APP_API_KEY` | API 认证密钥  | `my-secret-key` |

可在 `BackEnd/src/main/resources/application-dev.yml` 中覆盖默认值（该文件已在 `.gitignore` 中排除）。

### 5. 启动应用

**方式一：一键启动（Windows）**

双击 `start.bat`，将同时启动后端和前端。

**方式二：手动启动**

```bash
# 后端
cd BackEnd
mvnw.cmd spring-boot:run

# 前端（新终端）
cd FrontEnd
npm install
npm run dev
```

启动后访问：

- 前端：<http://localhost:5173>
- 后端：<http://localhost:8080>

## API 文档

所有接口前缀为 `/api`，除 `POST /api/auth/login` 和 `GET /api/health` 外均需在请求头中携带 `X-API-Key`。

### 认证

| 方法     | 路径                | 说明         |
| ------ | ----------------- | ---------- |
| `POST` | `/api/auth/login` | 验证 API Key |

请求体：

```json
{ "apiKey": "your-api-key" }
```

### 对话

| 方法     | 路径                                        | 说明        |
| ------ | ----------------------------------------- | --------- |
| `POST` | `/api/chat/completions`                   | 流式对话（SSE） |
| `GET`  | `/api/chat/sessions/{sessionId}/messages` | 获取消息历史    |

对话请求体：

```json
{
  "sessionId": 1,
  "modelName": "qwen2.5:7b",
  "message": "你好",
  "deepThink": false,
  "enableSearch": false
}
```

SSE 响应事件类型：

| 事件 data 中的 type 字段  | 说明                         |
| ------------------- | -------------------------- |
| `token`             | 对话内容 Token                 |
| `reasoning_content` | 深度思考内容（支持 `thinking` 字段兼容） |
| `search`            | 联网搜索状态（含结果数量和来源链接）         |
| `done`              | 对话完成                       |

### 会话管理

| 方法       | 路径                                 | 说明       |
| -------- | ---------------------------------- | -------- |
| `GET`    | `/api/sessions`                    | 分页查询会话列表 |
| `GET`    | `/api/sessions/search?keyword=xxx` | 按标题搜索会话  |
| `PUT`    | `/api/sessions/{id}/title`         | 重命名会话    |
| `PUT`    | `/api/sessions/{id}/pin`           | 切换置顶状态   |
| `DELETE` | `/api/sessions/{id}`               | 删除会话及消息  |

### 模型管理

| 方法    | 路径                   | 说明                |
| ----- | -------------------- | ----------------- |
| `GET` | `/api/models`        | 获取可用模型列表          |
| `GET` | `/api/models/status` | 检查 Ollama 状态及模型列表 |

### 健康检查

| 方法    | 路径            | 说明     |
| ----- | ------------- | ------ |
| `GET` | `/api/health` | 服务健康状态 |

## 测试

### 后端测试

```bash
cd BackEnd
mvnw.cmd test
```

测试使用 H2 内存数据库替代 MySQL，MockWebServer 模拟 Ollama HTTP 服务，无需启动外部依赖。

### 前端测试

```bash
cd FrontEnd
npm test              # 单次运行
npm run test:watch    # 监听模式
npm run test:coverage # 覆盖率报告
```
## 数据库说明

- 数据库名：`smart_chat`
- 表前缀：`sc_`
- 数据表：`sc_session`（会话）、`sc_message`（消息，GZIP 压缩存储）、`sc_model_config`（模型配置）
- 表结构由 `schema.sql` 管理，JPA `ddl-auto` 设为 `none`，不自动建表

## 许可证

本项目采用 [MIT License](https://opensource.org/licenses/MIT) 开源协议。

## 联系方式

如有问题或建议，请通过 [GitHub Issues](https://github.com/dropflower/LocalAIChat/issues) 提交。
