# Daylog 接口文档

> 版本：v1.0 · 基于 RESTful 风格设计 · 后端由 springdoc 自动生成 Swagger 在线文档（`/swagger-ui.html`）

## 1. 通用约定

### 1.1 基础信息

| 项目 | 说明 |
|---|---|
| 开发环境地址 | `http://localhost:8080`（前端通过 Vite 代理 `/api` 访问，无跨域问题） |
| 数据格式 | `application/json`（文件上传为 `multipart/form-data`） |
| 时间格式 | 日期 `yyyy-MM-dd`，时间 `yyyy-MM-dd HH:mm:ss` |
| 认证方式 | 请求头 `Authorization: Bearer <token>`，JWT 有效期 7 天，支持主动失效 |

**无需认证的接口**：`POST /auth/register`、`POST /auth/login`，其余均需携带 token。

### 1.2 统一响应结构

所有接口返回统一 JSON 结构，前端 `request.js` 已按此结构解包：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

### 1.3 状态码约定

| code | 含义 | 典型场景 |
|---|---|---|
| 200 | 成功 | — |
| 400 | 参数错误 | 参数校验不通过（message 中含具体字段错误） |
| 401 | 未认证 | 未携带 token / token 过期 / token 已注销 |
| 403 | 无权限 | 访问了不属于自己的资源 |
| 404 | 资源不存在 | 日记/标签/周报不存在 |
| 1001 | 用户名已存在 | 注册时 |
| 1002 | 当天已有日记 | 一天一篇约束 |
| 1003 | 标签名已存在 | — |
| 1004 | 用户名或密码错误 | 登录时（不区分具体原因，防账号枚举） |
| 3005 | 该周没有日记 | 生成周报时该周 0 篇日记 |
| 3006 | 本周日记不足 3 篇 | 写了但少于 Agent 的 3 篇阈值（与 3005 区分：真没写 vs 写得不够） |
| 4001 | 操作过于频繁 | 登录/注册触发 IP 限流 |
| 500 | 系统错误 | 未预期异常（GlobalExceptionHandler 兜底） |

> 限流说明：`/auth/login`（60 秒 / 10 次）与 `/auth/register`（1 小时 / 5 次）按客户端 IP 计数，
> 超阈值时 HTTP 状态为 **429**，并带 `Retry-After` 头（窗口秒数），响应体仍是统一 `Result` 结构（`code=4001`）。
> 其余业务错误一律是 HTTP 200 + 非 200 的 `code`，只有这一条走真实 HTTP 状态码，因为它属于传输层流量控制、不是业务失败。

> 注：所有 Long 类型的 ID（雪花ID）在 JSON 中序列化为字符串，防止 JS Number 精度丢失。

### 1.4 分页响应结构

分页接口的 `data` 统一为：

```json
{
  "total": 100,
  "current": 1,
  "size": 10,
  "records": []
}
```

---

## 2. 认证模块 `/auth`

### 2.1 注册 `POST /auth/register`

请求：

```json
{
  "username": "zhangsan",
  "password": "123456",
  "nickname": "张三"
}
```

校验规则：username 4-30 位字母/数字/下划线；password 6-32 位；nickname 1-30 位，不传默认同 username。

响应 `data`：

```json
{ "userId": 1948237619283747841 }
```

### 2.2 登录 `POST /auth/login`

请求：

```json
{ "username": "zhangsan", "password": "123456" }
```

响应 `data`：

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userInfo": {
    "id": 1948237619283747841,
    "username": "zhangsan",
    "nickname": "张三",
    "avatar": "https://..."
  }
}
```

### 2.3 退出登录 `POST /auth/logout`

将当前 token 写入 Redis 黑名单（key: `jwt:blacklist:<token>`，TTL 与 token 剩余有效期一致），实现主动失效。

### 2.4 当前用户信息 `GET /auth/me`

响应 `data`：同 `userInfo` 结构。

---

## 3. 日记模块 `/diaries`

### 3.1 日记列表 `GET /diaries`（分页 + 多条件）

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| startDate | string | 否 | 记录日期起，如 `2026-09-01` |
| endDate | string | 否 | 记录日期止 |
| mood | int | 否 | 心情分筛选 1-5 |
| tagId | long | 否 | 标签筛选 |
| keyword | string | 否 | 标题/正文关键词（LIKE，走前缀索引优化） |
| page | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页条数，默认 10，最大 50 |

响应 `data.records` 元素：

```json
{
  "id": 1948237619283747842,
  "title": "第一个周末",
  "content": "今天完成了项目骨架搭建...",
  "moodScore": 4,
  "weather": "晴",
  "recordDate": "2026-09-06",
  "wordCount": 128,
  "tags": [
    { "id": 1, "name": "编程", "color": "#409EFF" }
  ],
  "imageCount": 2,
  "createdAt": "2026-09-06 23:00:00"
}
```

### 3.2 日记详情 `GET /diaries/{id}`

响应 `data`：列表元素结构 + `images: [{id, url, originalName}]`。

**权限**：仅日记所属用户可访问，他人访问返回 403。

### 3.3 创建日记 `POST /diaries`

```json
{
  "title": "第一个周末",
  "content": "今天完成了项目骨架搭建...",
  "moodScore": 4,
  "weather": "晴",
  "recordDate": "2026-09-06",
  "tagIds": [1, 2],
  "attachmentIds": [101, 102]
}
```

说明：当天已存在日记时返回 1002；`attachmentIds` 为先上传后回填的附件 ID 列表；`wordCount` 由后端统计，前端不传。

### 3.4 更新日记 `PUT /diaries/{id}`

请求体同创建（无特殊说明的字段均可修改），同样受一天一篇约束。

### 3.5 删除日记 `DELETE /diaries/{id}`

物理删除，同时清理 `diary_tag` 关联记录；附件记录与文件一并清理。

### 3.6 日历视图 `GET /diaries/calendar?year=2026&month=9`

响应 `data`（数组，仅返回有记录的日期）：

```json
[
  { "recordDate": "2026-09-01", "moodScore": 3 },
  { "recordDate": "2026-09-06", "moodScore": 4 }
]
```

### 3.7 查询指定日期日记 `GET /diaries/date/{recordDate}`

用于"编辑今天的日记"场景；当天无记录返回 `data: null`（非 404，前端据此显示"新建"或"编辑"）。

---

## 4. 标签模块 `/tags`

### 4.1 标签列表 `GET /tags`

响应 `data`：

```json
[
  { "id": 1, "name": "编程", "color": "#409EFF", "count": 23 }
]
```

`count` 为该标签下的日记数，用于前端标签管理与标签云展示。

### 4.2 创建标签 `POST /tags`

```json
{ "name": "编程", "color": "#409EFF" }
```

### 4.3 更新标签 `PUT /tags/{id}`

请求体同创建，仅允许修改 name / color。

### 4.4 删除标签 `DELETE /tags/{id}`

物理删除标签并解除所有 `diary_tag` 关联，不影响日记本身。

---

## 5. 统计模块 `/stats`

所有统计接口均缓存至 Redis（TTL 5 分钟），日记发生增删改时主动失效——**缓存一致性策略是本项目核心亮点之一**。

### 5.1 总览 `GET /stats/overview`

```json
{
  "totalDiaries": 128,
  "totalWords": 35620,
  "currentStreak": 6,
  "longestStreak": 23,
  "monthCount": 12
}
```

### 5.2 心情趋势 `GET /stats/mood-trend?startDate=2026-08-01&endDate=2026-09-06`

```json
[
  { "date": "2026-08-01", "avgMood": 3.5, "count": 1 }
]
```

范围最长 92 天（一个季度），超出自动截断。

### 5.3 心情分布 `GET /stats/mood-distribution?startDate=&endDate=`

```json
[
  { "moodScore": 1, "count": 3 },
  { "moodScore": 5, "count": 45 }
]
```

### 5.4 标签云 `GET /stats/tag-cloud?limit=20`

```json
[
  { "tagId": 1, "name": "编程", "color": "#409EFF", "count": 23 }
]
```

### 5.5 记录热力图 `GET /stats/heatmap?year=2026`

```json
[
  { "date": "2026-01-01", "moodScore": 4 }
]
```

GitHub 风格贡献图数据源，前端渲染为日历热力图。

---

## 6. AI 周报模块 `/ai/reports`

### 6.1 手动生成周报 `POST /ai/reports/generate`

```json
{ "weekStartDate": "2026-08-31" }
```

`weekStartDate` 必须为周一；服务端拉取该周日记，拼接 Prompt 调用 DeepSeek 生成总结。同周重复生成时**覆盖更新**（唯一约束 `uk_user_week` + upsert）。该周无日记时返回 400。

响应 `data`：

```json
{
  "id": 1,
  "weekStart": "2026-08-31",
  "weekEnd": "2026-09-06",
  "diaryCount": 5,
  "moodAvg": 3.8,
  "summary": "## 本周总结\n...",
  "model": "deepseek-chat"
}
```

### 6.2 周报列表 `GET /ai/reports?page=1&size=10`

分页结构，`records` 同上。

### 6.3 周报详情 `GET /ai/reports/{id}`

### 6.4 定时任务（后端行为，无接口）

每周一 08:00 由 `@Scheduled` 任务为"上周有日记"的用户自动生成周报（`scheduler/WeeklyReportScheduler`），失败重试 3 次，最终失败记录日志不阻塞其他用户。

---

## 7. 文件模块 `/files`

### 7.1 上传图片 `POST /files/upload`

`multipart/form-data`，字段名 `file`。

限制：仅 jpg/png/gif/webp；单文件 ≤ 5MB（`application-dev.yml` 可配）。

响应 `data`：

```json
{ "id": 101, "url": "/files/2026/09/uuid.png" }
```

说明：上传与日记保存解耦——先上传拿 `attachmentIds`，日记保存时回填 `diary_id`，24 小时内未关联的附件由定时任务清理（防孤儿文件）。

### 7.2 图片访问 `GET /files/**`

静态资源映射，开发环境映射本地磁盘 `uploads/` 目录，生产环境建议切换 OSS（配置切换，代码不动）。
