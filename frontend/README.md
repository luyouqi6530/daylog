# Daylog Frontend

Daylog 每日记录与心情追踪平台 —— 前端工程（Vue 3 + Vite + Element Plus）。

## 开发

```bash
npm install
npm run dev        # http://localhost:5173（/api 由 Vite 代理到 localhost:8080）
```

## 构建

```bash
npm run build      # 产物输出到 dist/，由 Docker 阶段拷贝进 Nginx 镜像
```

## 环境变量

| 文件 | 用途 |
|---|---|
| `.env.development` | 开发环境：`VITE_API_BASE_URL=/api`（Vite 代理去前缀） |
| `.env.production` | 生产环境：`VITE_API_BASE_URL=/api`（Nginx 反代去前缀） |

## 目录

```
src/
├── api/        # 接口封装（auth / diary / tag / stats / report）
├── assets/     # 全局样式
├── layout/     # MainLayout 骨架（侧边栏 + 顶栏）
├── router/     # 路由表 + 登录守卫
├── stores/     # Pinia（user：token 持久化）
├── utils/      # axios 封装、心情映射
└── views/      # login / dashboard / diary / calendar / stats / tags / report
```

> 完整说明见根目录 [README](../README.md)。
