# 周览 Worklog · 前端

Vite + Vue 3 + Element Plus + Vue Router + Axios。

## 启动

```bash
cd D:\IDEA_experience\Worklog\frontend
npm install          # 首次执行
npm run dev          # 开发服务器 http://127.0.0.1:5173
```

后端需要先启动（`运行Worklog.bat` 或 IDEA 里跑 `WorklogApplication`），
前端通过 Vite 代理把 `/api` 转发到 `http://localhost:8080`，无需处理跨域。

## 目录

```
frontend/
├── index.html
├── vite.config.js          # /api -> localhost:8080 代理
└── src/
    ├── main.js             # 挂载 Element Plus（中文语言包）与图标
    ├── App.vue             # 路由出口 + 页面切换动效
    ├── api/auth.js         # 登录相关接口
    ├── router/index.js     # 路由表 + 登录守卫
    ├── styles/main.css     # 设计令牌（#F7F6F3 / #3F4A5A / #7A6A93）与 Element 主题覆盖
    ├── utils/
    │   ├── auth.js         # Token / 用户信息的 localStorage 读写
    │   └── request.js      # Axios 实例：自动带 Token、统一处理 code != 200
    └── views/
        ├── LoginView.vue   # 登录页（演示账号快捷填充）
        └── HomeView.vue    # 临时主页：登录成功，欢迎 XXX
```

## 演示账号

密码统一 `123456`：

| 角色 | 账号 |
|---|---|
| 管理员 | `admin` |
| 部门负责人 | `liyue` |
| 员工 | `wangfan` |

## 构建

```bash
npm run build     # 产物在 dist/
npm run preview   # 本地预览构建产物
```

> 生产环境若前后端分开部署，需要把 `utils/request.js` 里的 `baseURL` 改为完整后端地址，
> 或由 Nginx 把 `/api` 反代到后端，避免依赖 Vite 代理。
