# 本机环境与 Docker 方案

> 回答一个问题：容器里跑什么、本机跑什么、为什么这么分。

---

## 一、结论先行

**是的，就是你想的那样。**

| 组件 | 跑在哪 | 端口 |
|---|---|---|
| MySQL 8 | **Docker 容器** | 3306 |
| Redis 7 | **Docker 容器** | 6379 |
| Spring Boot 主项目 | **Windows 本机**（IDEA 里启动） | 8080 |
| Vue 前端 | Windows 本机（`npm run dev`） | 5173 |

本机项目通过 `localhost:3306` / `localhost:6379` 访问容器里的中间件。

---

## 二、为什么项目不一起进容器

| 对比项 | 中间件进容器 + 项目在本机（**本项目方案**） | 项目也进容器 |
|---|---|---|
| 改代码后的反馈速度 | 秒级（IDEA 编译 + 热重载） | 每次都要重新构建镜像，分钟级 |
| 断点调试 | IDEA 直接打断点 | 要配远程调试，很麻烦 |
| 端口访问 | 8080 直接访问 | 要额外做端口映射 |
| 文件挂载 | 不涉及 | Windows 目录挂进容器**非常慢** |
| 结论 | ✅ 开发阶段用这个 | ❌ 只在 W8 部署时用 |

**一句话**：开发期中间件容器化，项目留在本机；W8 部署时才把项目也打包成镜像。

---

## 三、Docker Desktop 在 Windows 上到底是什么

它**不是**"把 Linux 装进 Windows"，而是：

- Docker Desktop 会启动一台**轻量级 Linux 虚拟机**（默认 **WSL2** 后端）；
- 容器跑在这台轻量虚拟机里；
- Windows 里的 `localhost:3306` 由 Docker Desktop **端口转发**到容器。

### 和你以前的 VMware 方案对比

| 对比项 | 你以前的做法（VMware + Linux + Docker） | Docker Desktop |
|---|---|---|
| 原理 | VMware 起一台完整 Linux，在里面装 Docker | 轻量虚拟机，Docker 已经装好 |
| 启动耗时 | 开机几十秒到几分钟 | 秒级（常驻后台） |
| 资源占用 | 整台虚机（2-4 G 内存） | 按需，可在设置里限制上限 |
| 网络 | 虚机有独立 IP，本机得用虚机 IP 访问 | 端口直接映射到 `localhost` |
| 文件共享 | 要装 VMware Tools 配共享目录 | 用数据卷 / 挂载，注意性能 |
| 重启后 | 手动开虚机、再起容器 | 自启，容器可设重启策略 |

**你的 Docker 知识（镜像、容器、端口映射、数据卷）可以 100% 迁移过来。** 只有两点不同：**网络位置** 和 **文件系统**。

---

## 四、⚠️ 提前知道：VMware 与 Docker Desktop 可能互相干扰

- Docker Desktop 的 WSL2 / Hyper-V 后端需要启用 Windows 的虚拟化平台功能；
- 这会和 VMware 抢占同一层虚拟化资源；
- 较新的 VMware Workstation（15.5 以上）可以和 Hyper-V 共存（走 Windows Hypervisor Platform），但**性能可能下降**；
- 你还可能有课设需要在 VMware 里跑 Linux。

**建议**：先按 WSL2 后端把 Docker Desktop 装好，VMware 先留着别卸。两个都启动跑一次，看有没有异常。真冲突了再来找我——有几套绕法。

---

## 五、两个实操注意点（能省你半天）

### 1. MySQL 数据用 named volume，别挂 Windows 目录

❌ 不要这样：

```yaml
volumes:
  - ./data/mysql:/var/lib/mysql
```

✅ 要这样：

```yaml
volumes:
  - mysql-data:/var/lib/mysql

# 文件底部
volumes:
  mysql-data:
```

**为什么**：容器里是 Linux 文件系统，Windows 是另一套。跨文件系统挂载（尤其 MySQL 这种小文件随机 IO）性能会差一个量级，还容易撞上权限、大小写、换行符这类隐性坑。用 named volume 交给 Docker 自己管（数据实际落在 WSL2 的 Linux 文件系统里），性能正常，也不受项目路径影响。

> 📌 2026-09-22 更新：项目路径已从中文的 `其他项目` 改成全 ASCII 的 `other-projects`，Windows 侧少了一类麻烦；但"跨文件系统挂载"的性质没变，**结论不变**。

### 2. 起完容器先验证连得通，再动代码

按顺序验证：

1. `docker ps` → 两个容器状态是 `Up`
2. `docker exec -it <mysql容器名> mysql -uroot -p` → 能进 SQL 命令行
3. `docker exec -it <redis容器名> redis-cli ping` → 返回 `PONG`

三个都过了，再去写 `application.yml`。

**顺序反了，你会浪费大量时间纠结"到底是我配置错了，还是容器根本没起来"。**

---

## 六、顺手做个自启设置

在 `docker-compose.yml` 里给每个服务加 `restart: unless-stopped`。

这样重启电脑后容器会自动起来，不用每次手动 `docker compose up -d`。

> ⚠️ **但有个前提**：本机如果也有开机自启的 MySQL，它会抢在 Docker 前面占住 3306，容器照样起不来。**你这台机器上就有**（服务名 `MySQL80`，启动类型是「自动」）——必须先把它改成「手动」。处理步骤见 `07-Docker基础与操作流程.md` 第五节末尾。

---

## 七、命令速查

| 目的 | 命令 |
|---|---|
| 启动 | `docker compose up -d` |
| 看状态 | `docker ps` |
| 看日志 | `docker compose logs -f mysql` |
| 进容器 | `docker exec -it <容器名> bash` |
| 停止（保留数据） | `docker compose stop` |
| **彻底重置** | `docker compose down -v` |

`down -v` 是你 W1 建表建错时的救命命令——把容器和数据卷一起删干净，重来一遍。**注意 `-v` 会删掉数据卷，数据会没。**
