# ============================================
# 前端多阶段构建
# ============================================

# 构建阶段
FROM node:20-slim AS builder
WORKDIR /app

# 接受 API 基础 URL 构建参数
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

COPY package.json package-lock.json ./
RUN npm ci --legacy-peer-deps

COPY . .
RUN node node_modules/vite/bin/vite.js build

# ============================================
# 运行阶段：Nginx 静态文件服务
# ============================================
FROM nginx:alpine AS runtime

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# Nginx 配置：SPA 路由支持 + API 代理
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
