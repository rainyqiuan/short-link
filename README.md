# short-link · 高并发短链平台

把长链接转换成短链，并在高并发访问下完成一跳跳转。目标是做成一个能讲清「缓存 / 限流 / 异步 / 压测 / 可观测」的完整后端项目。

> 当前状态：**W1 ~ W8 已完成**（骨架 → 建链 → 跳转 → Redis 缓存 → 布隆过滤器 → 访问日志异步落库 → 接口限流 → 访问统计 → 压测 → 全链路 traceId → Docker 容器化）

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 / 框架 | Java 17 · Spring Boot 3.5 |
| 持久层 | MyBatis-Plus · MySQL 8 |
| 缓存 | Redis 7（缓存 · 布隆过滤器 · 分布式发号 · Lua 限流） |
| 接口文档 | Knife4j（OpenAPI 3） |
| 可观测 | 全链路 `traceId`（MDC + 响应头 + 响应体） |
| 打包部署 | Docker（单阶段镜像）· Docker Compose（`profiles: [prod]`） |
| 压测 | JMeter / Java 并发探针 |

## 快速开始

### 方式一：开发模式（改代码用这个）

中间件跑容器，应用跑本机 —— 秒级热重载、IDEA 直接打断点。

```bash
# 1. 起中间件（只起 MySQL + Redis）
docker compose up -d

# 2. 准备环境变量（.env 不会被 Spring Boot 自动加载，需手动导出）
#    MYSQL_PASSWORD / REDIS_PASSWORD，见 .env.example

# 3. 本机启动应用
mvn spring-boot:run
```

数据库连接默认走 `localhost:3306` / `localhost:6379`，无需额外配置。

### 方式二：部署模式（换机器 / 演示 / 交付）

应用也进容器，一条命令起全套。

```bash
# 1. 先在本机打包（单阶段镜像不含 mvn，compose 不会替你编译）
#    -DskipTests 不能省，原因见 docs/05 §三之五十九 第 5 节
mvn -DskipTests package

# 2. 起整套
docker compose --profile prod up -d --build

# 3. 验证
curl localhost:8080/actuator/health     # {"status":"UP"}
curl localhost:8080/<短码>               # 302 → 目标地址
```

两种模式**共用同一个 `docker-compose.yml`** —— `app` 服务带 `profiles: [ "prod" ]`，不带 `--profile` 时它不参与，开发习惯零改变。

> 详细步骤、踩过的坑与实测数据见 [`docs/06`](docs/06-本机环境与Docker方案.md) §八、[`docs/07`](docs/07-Docker基础与操作流程.md) §八。

## 设计文档

设计先于代码。全部设计决策、取舍理由与踩坑记录都在 [`docs/`](docs/)：

| 文档 | 内容 |
|---|---|
| [01-开发计划](docs/01-开发计划.md) | 8 周节奏、里程碑与验收标准 |
| [02-数据库设计](docs/02-数据库设计.sql) | 建表 DDL 与索引设计 |
| [03-接口设计](docs/03-接口设计.md) | 接口清单与出入参（**接口路径的唯一权威**） |
| [04-概念速答卡](docs/04-概念速答卡.md) | 缓存三大问题、布隆过滤器等原理速查 |
| [05-工程结构与起步指南](docs/05-工程结构与起步指南.md) | 包结构、分层铁律、逐周实测记录与规律（R1~R138） |
| [06-本机环境与Docker方案](docs/06-本机环境与Docker方案.md) | 容器与本机各跑什么、两种运行模式 |
| [07-Docker基础与操作流程](docs/07-Docker基础与操作流程.md) | compose 怎么写、profile 怎么用 |
| [08-压测报告](docs/08-压测报告.md) | 六臂压测数据与结论（含一处被推翻的旧归因） |

## 核心设计

| 主题 | 做法 |
|---|---|
| 短码生成 | Redis `INCR` 发号 → 混淆 → 布隆 + 唯一索引双重占用校验 |
| 跳转读路径 | Cache-Aside：Redis 缓存 → miss 回源 MySQL → 回填（TTL 基准 86400s ± 抖动，`min(基准, 剩余寿命)` 封顶） |
| 缓存穿透 | 布隆过滤器（`m=9,580,000` / `k=7` / MD5 双 hash），未就绪一律放行 |
| 访问日志 | Redis List 缓冲（`RPUSH`）+ `@Scheduled` 批量搬运落库，事务提交后才 `trim` |
| 限流 | 令牌桶 + Redis Lua，键 = `IP + 路由模板`，超限返回 **429 + `Retry-After`** |
| 统计 | 写侧聚合（PV / UV 双算法 + 幂等 UPSERT）+ 读侧查询 |
| 可观测 | `TraceIdFilter` 写入 MDC + 响应头，`TraceIdAdvice` 注入响应体 |
| 降级原则 | Redis 全链路 **fail-open**（变慢不变错）；MySQL 是真相之源，**没有降级路径** |
