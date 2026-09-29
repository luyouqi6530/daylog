# ---------- 阶段一：Node 构建 ----------
FROM node:20-alpine AS build
WORKDIR /app

# 国内网络加速
RUN npm config set registry https://registry.npmmirror.com

# 先装依赖（利用 Docker 分层缓存）
# --legacy-peer-deps：lockfile 里 vue-router@5.3.1 的 peerOptional 要 vite@^7.3.0，
# 锁定的却是 vite@6.4.3（@vitejs/plugin-vue@5 只吃 ^5||^6，升不动）。
# npm ci 装什么完全由 lockfile 决定，这个开关只关掉 peer 区间校验。
COPY package.json package-lock.json ./
RUN npm ci --legacy-peer-deps

# 拷贝源码构建
COPY . .
RUN npm run build

# ---------- 阶段二：Nginx 托管静态资源 ----------
FROM nginx:1.27-alpine

# 站点配置：/api 反代后端 + SPA 路由回退
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 拷贝构建产物
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
