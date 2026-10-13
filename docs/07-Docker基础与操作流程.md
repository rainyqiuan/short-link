# Docker 基础与操作流程

> 你以前是**直接在 Linux 里装 Redis**；现在改成「用 Docker 跑中间件」。
> 这份文档把概念、`docker-compose.yml` 的作用、操作顺序一次讲清。

---

## 一、先建立四个概念

| 概念 | 类比 | 说明 |
|---|---|---|
| **镜像 image** | 安装包 / 类 | 只读模板，例如 `mysql:8.0`、`redis:7` |
| **容器 container** | 装好并跑起来的程序 / 对象 | 镜像的运行实例，有独立进程和文件系统 |
| **数据卷 volume** | 外接硬盘 | 容器删了数据还在。**MySQL 的数据必须放这里** |
| **端口映射** | 门牌号转发 | `-p 本机端口:容器端口`，**左本机、右容器**，写反了就连不上 |

**一句话记住**：镜像 → 容器 就是「**类 → 对象**」。容器随时可以删掉重建，但数据卷里的数据会留着。

---

## 二、docker-compose.yml 是什么

它是一份**声明式的服务清单**：你只写「**我要什么**」，不写「每一步怎么做」。

不用 compose 的话，你得手敲三条 `docker run`，每条都带一堆参数（端口、密码、数据卷），错一个就连不上；用 compose，一条 `docker compose up -d` 全部起来。

| | `docker run`（命令式） | `docker-compose.yml`（声明式） |
|---|---|---|
| 写法 | 一行一个容器，参数全在命令行里 | 一个文件写清所有服务 |
| 复现 | 靠记忆或另写文档 | **文件本身就是文档**，谁跑都一样 |
| 多服务 | 三条命令，启动顺序和依赖自己管 | 一个文件，一条命令 |
| 版本管理 | 无 | 跟代码一起进 Git |
| 简历价值 | — | 「使用 Docker Compose 编排 MySQL 与 Redis」 |

**记住一句**：文件里 `services:` 下面的**每一个条目 = 一个容器**。

---

## 三、完整操作流程（重点，这里你理解有两处要修正）

你的理解「先拉镜像 → 启动容器 → 进容器验证」**方向是对的**，但有两个地方要改。

### 修正 1：不需要手动拉镜像

`docker compose up -d` 会自动完成**三件事**：

1. **拉取镜像**（本地没有才拉，有就直接用本地的）
2. **创建容器**
3. **启动容器**

所以不用单独执行 `docker pull`。只有你想「提前下载好」或「钉死某个版本」时，手动拉才有意义。

### 修正 2：验证不是一步，而是三层

| 层 | 命令 | 它在回答什么问题 |
|---|---|---|
| ① 状态 | `docker ps` | 容器**进程**在跑吗 |
| ② 日志 | `docker compose logs -f mysql` | 服务**启动过程**有报错吗 |
| ③ 连接 | `docker exec -it <容器名> mysql -uroot -p` | 服务**真的能用**吗 |

**为什么必须三层**：MySQL 第一次启动要先初始化数据目录，需要 **10-30 秒**。这段时间 `docker ps` 显示 `Up`，但你根本连不上——这是新手最容易踩的坑（"明明是 Up，为什么连不上？"）。

### 所以完整顺序是

```
⓪ 本机 MySQL80 停掉 + 启动类型改「手动」  ← 只做一次，见第五节末尾
① 写 docker-compose.yml
② docker compose up -d          ← 自动：拉镜像 + 建容器 + 启动
③ docker ps                     ← 看状态是不是 Up
④ docker compose logs -f        ← 看日志，读到 ready 才算好
⑤ docker exec 进去连一下         ← 最终验证
⑥ 全通过，才去写 application.yml
```

**第 ⑥ 步的顺序千万别提前。** 容器没验证好就去写配置，你会分不清是配置错了还是容器的问题。

---

## 四、这些操作能在 Docker Desktop 里做吗？

**能，而且很方便。但建议「命令行起服务，GUI 查问题」。**

| 操作 | Docker Desktop 图形界面 | 命令行 |
|---|---|---|
| 看镜像 / 容器列表 | ✅ 直观 | ✅ |
| 启动 / 停止 / 重启 | ✅ 点按钮 | ✅ |
| 看日志 | ✅ 有日志面板，可搜索 | ✅ |
| 进容器敲命令 | ✅ Containers → Exec 里有终端 | ✅ |
| 看端口 / 环境变量 / 挂载 | ✅ 详情页 | `docker inspect` |
| **编排整组服务（compose）** | ⚠️ 新版本有 Compose 视图，但不够顺 | ✅ **推荐** |
| 删数据卷 | ⚠️ 能点，但容易误删 | `docker compose down -v` |

### 为什么推荐用命令行起服务

1. **可复现**。GUI 点出来的环境，换台电脑、换个人就说不清了；命令写下来谁都能跑；
2. **面试更好说**。"我用 `docker compose up -d` 起中间件" 比 "我在 Docker Desktop 里点了启动" 专业得多；
3. GUI 真正好用的场景是**排错**——看日志、看端口映射、看容器为什么退出。

**结论**：两个都用。**compose 文件用命令行跑，日常查看和排错用 GUI。**

---

## 五、几个大概率会踩的坑

| 坑 | 现象 | 原因 / 解法 |
|---|---|---|
| 端口写反 | 连不上 | `-p 3306:3306` 是 `本机:容器`，前一个才是你在 Windows 上访问用的 |
| 容器 Up 但连不上 | 连接超时 | MySQL 还在初始化，等 20 秒再看日志 |
| 数据挂到 Windows 目录 | 极慢 / 权限报错 | 用 **named volume**，别 bind mount（理由见 `06-本机环境与Docker方案.md` 第五节） |
| 重启电脑后连不上 | 容器没启动 | 加 `restart: unless-stopped` |
| **端口被占用** | 容器启动失败 | 见下方说明 |
| 建表建错了想重来 | — | `docker compose down -v`（⚠️ 会删掉数据卷） |

### 「端口被占用」这条要特别提醒

**如果你以前在本机（或 VMware 里）装过 MySQL / Redis，而且服务还在跑，Docker 起容器时会因为 3306 / 6379 被占用而启动失败。**

先确认本机没有在跑的 MySQL / Redis 服务：

- Windows：`netstat -ano | findstr 3306`
- 或者直接在「服务」里把本机装的 MySQL 服务停掉

实在要共存，就把 compose 里改成 `3307:3306`，然后项目连 `3307`。

### ✅ 你这台机器的实测结论（2026-09-22 检查）

| 检查项 | 实测结果 |
|---|---|
| 3306 | **被占用** —— `mysqld.exe`（PID 7632），服务名 `MySQL80`，**启动类型是「自动」** |
| 6379 | 空闲，本机没有 Redis 服务 → 容器直接可用 |

**已决定：3306 让给容器。** 好处是 compose 不用改端口，`localhost:3306` 永远连到容器里的 MySQL，Navicat 之类的工具也不用换端口。

需要你做两件事：

1. **停掉它，并把启动类型改成「手动」**（⚠️ 只停服务不改启动类型没用——开机时它会先抢走 3306，你会遇到"昨天还好好的，今天容器起不来"）：
   - 图形界面：`Win + R` → `services.msc` → 找到 `MySQL80` → 右键「属性」→ 启动类型选**手动** → 确定，再右键「停止」
   - 或管理员 PowerShell：`Stop-Service MySQL80` 然后 `Set-Service MySQL80 -StartupType Manual`
2. **以后要再跑本机 MySQL**（比如课设商城要演示）：`net start MySQL80` 临时启回来即可，数据不会丢。

---

## 六、docker-compose.yml 写在哪、用什么写

### 它不是 Docker Desktop 里写的东西

Docker Desktop 是「**图形界面 + 引擎**」，它能**查看和启停**已有的 compose 项目，但**不是编辑器**。这个文件就是一个**普通文本文件**，你自己用编辑器创建。

### 位置：Spring Boot 项目的根目录

和 `pom.xml` **同级**：

```text
D:\03-Projects\other-projects\FindWork\short-link\      ← 这一层就是 Git 仓库根目录
├── docs\                       项目文档（7 篇，跟代码一起进仓库）
├── docker-compose.yml          ★ 写在这里
├── pom.xml
└── src\
```

### 文件名必须精确

必须是 `docker-compose.yml` 或 `compose.yaml`。

> ⚠️ **Windows 的坑**：资源管理器默认隐藏已知扩展名。用记事本保存时很容易变成 `docker-compose.yml.txt`——你看着是 `docker-compose.yml`，其实不是。**在资源管理器里把「文件扩展名」勾选显示出来，确认一下。**

### 用什么写

| 工具 | 说明 |
|---|---|
| **IDEA**（在项目里直接新建文件） | 推荐。顺便用它的内置终端跑命令 |
| VS Code | 轻量，YAML 有高亮和缩进提示 |
| Notepad++ | 够用 |
| ❌ Word / WPS | **绝对不要**，会加隐藏格式，YAML 直接解析失败 |

### YAML 三条硬规则

1. **缩进只能用空格，不能用 Tab** —— 这是最常见的失败原因
2. **冒号后面必须有空格**：`image: mysql:8.0` ✅ / `image:mysql:8.0` ❌
3. 大小写敏感

### 在哪执行命令

`docker compose up` **只读「当前目录」里的 compose 文件**。所以终端必须先切到那个目录：

```bash
cd D:\03-Projects\other-projects\FindWork\short-link
docker compose up -d
```

**不需要额外开终端** —— IDEA 底部有 `Terminal` 面板，直接用那个就行。

### 顺带说一个可能遇到的坑

Docker Compose 默认拿**目录名**当项目名，而 Docker 要求项目名只能包含小写字母、数字、`-` 和 `_`。

`short-link` 是合法的，正常不会有问题。如果你后面遇到类似 `project name must not be empty` 的报错，就在 compose 文件**第一行**加一句显式指定：

```yaml
name: shortlink
```

---

## 七、你现在的操作顺序（照做就行）

**第 0 步不能跳**：先让本机 MySQL 让位（第五节末尾：`MySQL80` 停掉 + 启动类型改「手动」），否则第 3 步一定失败。

1. 本机 `MySQL80` → 停掉，启动类型改「手动」
2. 启动 **Docker Desktop**，等引擎状态变成绿色
3. 在项目根目录写 `docker-compose.yml`：两个服务 `mysql` + `redis`，都用 named volume，都加 `restart: unless-stopped`
4. 命令行执行 `docker compose up -d`
5. `docker ps` → 确认两个容器都是 `Up`
6. `docker compose logs -f` → MySQL 输出 `ready for connections`、Redis 输出 `Ready to accept connections`
7. `docker exec` 分别连一次 MySQL 和 Redis，验证真的能用
8. 全通过 → 去写 `application.yml`

**走完第 8 步，你的环境就搭好了，可以正式进 Day 2。**

> 🔁 **2026-10-13 更新**：上面这套顺序是**开发模式**（只起中间件）。W8 之后 `docker-compose.yml` 里多了第三个服务 `app`，多一种"整套部署"的用法 —— 见第八节。

---

## 八、🔁 2026-10-13 更新：compose 多了第三个服务 `app`

到 W7 为止，`docker-compose.yml` 里只有 `mysql` + `redis`，应用永远跑在本机。W8 把应用也容器化了，但**不是把它变成常住的第三个容器**，而是挂在 `profiles` 后面：

```yaml
  app:
    build: .                       # 用项目根目录的 Dockerfile 构建
    profiles: [ "prod" ]           # ← 关键：不带 --profile prod 就不存在
    ports:
      - "8080:8080"
    environment:
      MYSQL_HOST: mysql            # 服务名就是主机名（同一网络内）
      REDIS_HOST: redis
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      REDIS_PASSWORD: ${REDIS_PASSWORD}
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
    restart: unless-stopped
```

### 为什么用 profiles 而不是直接加？

| 方案 | 平时 `docker compose up -d` 的结果 | 问题 |
|---|---|---|
| 直接加 `app` | 会把应用也拉起来，**抢 8080** | 本机 IDEA 就跑不了了 |
| `profiles: [ "prod" ]` | 只有 mysql/redis | 无 —— 想跑整套时加 `--profile prod` |

**一句话**：同一个文件，**默认视图**给开发用、**prod 视图**给部署用，不用维护两份配置（也就不会有两份悄悄漂移的问题）。

### 中间件多了 `healthcheck`

`condition: service_healthy` 要有东西可等 —— 而 `mysql:8.0` / `redis:7.4` 这两个镜像**都没有自带健康检查**（`docker inspect ... .Config.Healthcheck` 是 `null`），所以要自己写：

```yaml
    healthcheck:
      test: [ "CMD-SHELL", "mysqladmin ping -h127.0.0.1 -uroot -p$$MYSQL_ROOT_PASSWORD --silent" ]
      interval: 5s
      timeout: 5s
      retries: 10
      start_period: 30s
```

⚠️ **这里有三个必踩的坑**（W8 实测踩过）：

1. **必须写 `$$` 而不是 `$`** —— `${X}` 是 compose 在宿主机上替换，`$$X` 才是"容器内的环境变量"。两者机制完全不同。
2. **必须用 `CMD-SHELL` 而不是 `CMD`** —— `CMD` 数组形式**不经过 shell**，变量永远不会展开。
3. **`$$X` 还有第三个前提**：容器里得**真的定义了这个变量**。redis 的 healthcheck 一开始一直 `unhealthy`，就是因为 redis 的 `environment:` 里只有 `TZ`、没有 `REDIS_PASSWORD`（`mysql` 那边恰好有 `MYSQL_ROOT_PASSWORD`，所以它没事）。

完整推导与取证见 `05-工程结构与起步指南.md` §三之五十九 第 2、3 节。

### 部署模式的完整顺序

```bash
# 1. 先在本机打出 jar（单阶段镜像不含 mvn，compose 不会替你编译）
mvn -DskipTests package

# 2. 起整套（--build 会重建 app 镜像）
docker compose --profile prod up -d --build

# 3. 看状态：app 显示 Up，mysql/redis 显示 (healthy)
docker compose --profile prod ps
```

`up` 的输出会把依赖顺序打出来，**卡在哪一段就说明问题在哪一层**：

```
Container shortlink-mysql-1 Waiting
Container shortlink-redis-1 Waiting
Container shortlink-redis-1 Healthy
Container shortlink-mysql-1 Healthy
Container shortlink-app-1 Starting
Container shortlink-app-1 Started
```

卡在 `Waiting` → 问题在中间件的 healthcheck，不在应用。

### 停的时候注意

| 命令 | 效果 |
|---|---|
| `docker compose --profile prod down` | 删容器 + 网络，**数据卷保留**（表数据还在） |
| `docker compose --profile prod down -v` | **连数据卷一起删**，真正从零重建 |

### 一句提醒

`mvn package` 会**跑测试**，而项目里的 `RebuildBloomFilterTest` 有个 `@AfterEach` 会删掉 Redis 里的布隆位图（`@Transactional` 只能回滚 MySQL，回滚不了 Redis）。所以打包一律用 **`-DskipTests`**；镜像构建阶段更要如此 —— 那时容器里连不上 MySQL/Redis，测试必然失败。
