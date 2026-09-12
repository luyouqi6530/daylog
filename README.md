<div align="center">

# 📔 Daylog · 每日记录与心情追踪

> 一个前后端分离的个人日记与情绪追踪平台：每天一篇 Markdown 日记、心情打分、标签管理，
> 可视化统计心情趋势，并由 DeepSeek AI 每周自动生成总结周报。

**Vue 3 · Spring Boot 3 · MySQL 8 · Redis · Docker Compose**

</div>

---

## ✨ 功能特性

| 模块 | 功能 |
|---|---|
| 认证 | 注册 / 登录 / 登出，Spring Security + JWT 无状态认证，Redis 黑名单实现可控登出 |
| 日记 | 一天一篇（数据库唯一索引兜底），Markdown 编辑 + 心情 1-5 打分 + 天气，可配图 |
| 标签 | 标签创建 / 管理 / 使用次数统计，日记多标签关联 |
| 日历 | 日历视图直观回看每天的心情记录 |
| 统计 | 心情趋势折线、GitHub 风格年度热力图、标签分布、记录总览（Redis 缓存加速） |
| AI 周报 | 每周一自动调用 DeepSeek 生成上周总结，支持手动触发与历史回看 |
| 部署 | Docker Compose 一条命令拉起 mysql / redis / backend / nginx 四容器 |

## 🛠 技术栈

**后端**：Spring Boot 3.3（Java 17）· Spring Security · JWT(jjwt 0.12) · MyBatis-Plus · MySQL 8 · Redis · springdoc-openapi

**前端**：Vue 3 · Vite · Pinia · Vue Router · Element Plus · ECharts · md-editor-v3 · axios

## 🏗 架构

```
浏览器 (Vue3 SPA)
   │  请求统一带 /api 前缀
   ▼
Nginx ── 托管静态资源 + history 路由回退
   │  /api/* 去前缀反代    /files/* 反代图片
   ▼
Spring Boot 3 ── Controller → Service → Mapper
   │
   ├─ MySQL 8      业务数据（6 张表）
   ├─ Redis        统计缓存(5min TTL) + JWT 登出黑名单
   ├─ 本地磁盘     日记配图（/files/** 静态映射）
   └─ DeepSeek API AI 周报生成
```

## 📁 项目结构

```
daylog/
├── docker-compose.yml      # 一键部署编排（mysql/redis/backend/nginx）
├── .env.example            # 部署环境变量模板
├── backend/                # Spring Boot 后端（按业务模块分包）
│   ├── sql/schema.sql      # 建表脚本
│   └── src/main/java/com/daylog/
│       ├── common/         # 统一响应 Result / 全局异常
│       ├── config/         # Security、Jackson(Long→String)、MyBatis-Plus 配置
│       ├── security/       # JWT 工具、过滤器、Redis 黑名单
│       ├── scheduler/      # AI 周报(周一08:00)、孤儿文件清理 定时任务
│       └── modules/        # 业务模块：auth / diary / tag / stats / file / report
├── frontend/               # Vue3 前端（Vite + Element Plus）
│   └── src/
│       ├── api/            # 接口封装（按后端模块分文件）
│       ├── views/          # login/dashboard/diary/calendar/stats/tags/report
│       └── utils/request.js# axios 拦截器（token 注入 / 401 处理）
├── learn/                  # 学习文档：总览 / 路线图 / 面试考点 / 踩坑记录
└── docs/API.md             # 接口文档（30+ 接口）
```

## 🚀 快速开始

### 方式一：Docker Compose 一键部署（推荐）

```bash
# 1. 准备环境变量（修改数据库密码 / JWT 密钥 / DeepSeek Key）
cp .env.example .env

# 2. 构建并启动（首次需拉取基础镜像与依赖，耗时较长）
docker compose up -d --build

# 3. 访问
#    前端入口   http://localhost
#    后端直连   http://localhost:18080
```

> 端口已避开本机默认值（MySQL 13306 / Redis 16379 / 后端 18080），与本地开发环境可同时运行互不冲突。

### 方式二：本地开发

**后端**（需 JDK 17+、MySQL、Redis；用 IDEA 打开 `backend/pom.xml`）
1. 执行 `backend/sql/schema.sql` 初始化数据库
2. 修改 `application-dev.yml` 中数据库账号密码与 DeepSeek Key
3. 运行 `DaylogApplication`

**前端**
```bash
cd frontend
npm install
npm run dev        # http://localhost:5173，/api 由 Vite 代理到 8080
```

## 💡 关键设计

<details>
<summary><b>1. 一天一篇如何保证？三层防御</b></summary>

- 前端：编辑页先查当天日记，存在则进编辑模式
- 服务层：保存前 count 校验，给出友好提示
- 数据库：`uk(user_id, record_date)` 唯一索引兜底并发穿透

任何约束遵循"前端体验 + 服务层校验 + 数据库约束"的三层防御。
</details>

<details>
<summary><b>2. JWT 无状态与"登出即失效"如何兼得？</b></summary>

JWT 天然无法主动失效。方案：登出时将 token 写入 Redis 黑名单，TTL = token 剩余有效期；JWT 过滤器验签后查一次 Redis 黑名单，**不查数据库**，兼顾无状态验签的性能与可控失效的能力。
</details>

<details>
<summary><b>3. 逻辑删除与唯一索引的冲突</b></summary>

diary/tag 若逻辑删除，已删记录仍占据唯一索引位置（如"一天一篇"的当天记录），导致无法再写。因此 diary/tag/diary_tag/attachment 采用**物理删除**，仅 user 表用逻辑删除。
</details>

<details>
<summary><b>4. 雪花 ID 与前端精度</b></summary>

19 位 Long 超出 JS Number 安全范围。Jackson 全局配置 Long → String 序列化，前端以字符串接收 ID，杜绝精度丢失。
</details>

<details>
<summary><b>5. 附件"先上传后关联"</b></summary>

编辑器插图时日记尚未保存，无 diary_id 可用 → 图片先上传返回 URL，保存日记时回填 attachmentIds 建立关联；定时任务清理 24 小时未关联的孤儿文件。
</details>

<details>
<summary><b>6. 统计缓存策略</b></summary>

Cache-Aside：读接口先查 Redis（TTL 5min），写操作后**删除**相关缓存 key（删而非更新，避免并发旧值回写），TTL 兜底最终一致。
</details>

## 📚 更多文档

- 接口文档：[docs/API.md](docs/API.md)
- 学习笔记：[learn/00-项目总览.md](learn/00-项目总览.md)（总览 / 路线图 / 面试考点 / 踩坑记录）
