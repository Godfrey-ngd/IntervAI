# IntervAI 运行速查（Windows）

> 给“总是忘记怎么启动”的自己。按这个文件走就行。

---

## 0. 一次性前置检查

- 已安装：JDK 21、Node.js 20+、pnpm、Docker Desktop
- 已启动：Docker Desktop
- 在项目根目录：`D:\Github\IntervAI`

---

## 1. 启动依赖服务（数据库/缓存/对象存储）

在项目根目录执行：

```powershell
docker compose -f docker-compose.dev.yml up -d
```

确认容器运行：

```powershell
docker ps
```

至少应看到：
- `interview-postgres`
- `interview-redis`
- `interview-rustfs`（或你当前使用的对象存储容器）

---

## 2. 启动后端（最新代码）

> 关键：一定用当前源码启动，不要用旧进程。

在项目根目录执行：

```powershell
.\mvnw.cmd -f .\app\pom.xml -DskipTests spring-boot:run
```

后端默认端口：`8080`

快速健康检查：

```powershell
Invoke-WebRequest http://localhost:8080/api/resumes/health
```

如果你在做登录功能，也可以测：

```powershell
Invoke-WebRequest http://localhost:8080/api/auth/me
```

---

## 3. 启动前端

新开一个终端，进入前端目录：

```powershell
Set-Location D:\Github\IntervAI\frontend
pnpm.cmd install
pnpm.cmd dev
```

前端默认地址：
- http://localhost:5173

---

## 4. 我自己的标准启动顺序（推荐照抄）

1. `docker compose -f docker-compose.dev.yml up -d`
2. 启动后端：`mvnw.cmd ... spring-boot:run`
3. 启动前端：`pnpm.cmd dev`
4. 打开 http://localhost:5173

---

## 5. 常见问题（你最容易踩的坑）

### Q1：前端提示“API 接口不存在”

大概率是后端不是最新进程，或者压根没启动。

先查 8080 占用：

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

如果是旧 Java 进程占用，先结束再重启后端。

---

### Q2：PowerShell 里 `pnpm` 不能执行

请用：

```powershell
pnpm.cmd dev
```

不要直接用 `pnpm dev`。

---

### Q3：后端启动成功但前端仍报网络错误

检查：
- 前端是否是 `http://localhost:5173`
- 后端是否是 `http://localhost:8080`
- [frontend/src/api/request.ts](frontend/src/api/request.ts) 的 baseURL 是否是 `http://localhost:8080`（开发环境）

---

### Q4：我只想一把梭全容器

可用根目录 `docker-compose.yml` 一键起整套（含前后端）。

```powershell
docker compose up -d --build
```

然后访问：
- 前端：http://localhost
- 后端：http://localhost:8080

---

## 6. 结束运行

- 结束前端：当前终端 `Ctrl + C`
- 结束后端：当前终端 `Ctrl + C`
- 停止依赖容器：

```powershell
docker compose -f docker-compose.dev.yml down
```

如需连数据一起清理：

```powershell
docker compose -f docker-compose.dev.yml down -v
```

---

## 7. 30 秒自检清单（每天开工前）

- [ ] Docker Desktop 已启动
- [ ] `docker ps` 有 postgres/redis/rustfs
- [ ] 8080 后端已启动
- [ ] 5173 前端已启动
- [ ] 页面可打开且 API 不报 404

---

## 8. 最短命令备忘

```powershell
# 终端1（根目录）
docker compose -f docker-compose.dev.yml up -d
.\mvnw.cmd -f .\app\pom.xml -DskipTests spring-boot:run

# 终端2（frontend目录）
pnpm.cmd install
pnpm.cmd dev
```
