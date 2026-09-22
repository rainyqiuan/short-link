-- ============================================================================
-- 高并发短链平台 · 数据库设计
-- DB: MySQL 8.0    Charset: utf8mb4    Engine: InnoDB
--
-- ⚠️ 库名统一为 `shortlink`（与 docker-compose 的 MYSQL_DATABASE、application.yml 的
--    连接串保持一致）。容器里的 `shortlink` 账号只被授权访问这个库，写成别的名字会
--    报 Access denied。
--
-- 表清单
--   t_short_link        短链映射（核心表）
--   t_link_access_log   访问明细（写多读少，量大）
--   t_link_stats_daily  按天聚合统计（读多写少）
--   t_segment_alloc     号段发号器（可选，走号段模式时启用）
--
-- 设计要点（面试会问）
--   1. origin_url 不建索引 —— utf8mb4 下 2048*4 = 8192 字节，超过索引长度上限
--      （3072 字节），所以改用 origin_url_md5 CHAR(32) 做唯一索引实现幂等去重。
--   2. 明细表与聚合表分离 —— 明细只写不查，聚合表支撑查询，避免统计把明细表扫爆。
--   3. 短码用唯一索引兜底 —— 应用层发号可能重复，最终一致性由 DB 保证。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS shortlink
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE shortlink;

-- ---------------------------------------------------------------------------
-- 1. 短链映射表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS t_short_link;
CREATE TABLE t_short_link (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT         COMMENT '自增主键，仅内部使用',
  short_code      VARCHAR(16)     NOT NULL                        COMMENT '短码，Base62 编码结果',
  seq_no          BIGINT UNSIGNED NOT NULL                        COMMENT '发号器返回的序号，Base62 编码后即短码',
  origin_url      VARCHAR(2048)   NOT NULL                        COMMENT '原始长链接',
  origin_url_md5  CHAR(32)        NOT NULL                        COMMENT '长链接 MD5（小写），用于幂等去重',
  status          TINYINT         NOT NULL DEFAULT 1              COMMENT '状态 1=启用 0=禁用',
  expire_time     DATETIME        NULL     DEFAULT NULL           COMMENT '过期时间，NULL 表示永久有效',
  create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP                     COMMENT '创建时间',
  update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag        TINYINT         NOT NULL DEFAULT 0              COMMENT '逻辑删除 0=正常 1=已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_short_code     (short_code)                       COMMENT '短码唯一，发号重复时兜底',
  UNIQUE KEY uk_origin_url_md5 (origin_url_md5)                   COMMENT '同一长链只生成一个短码（幂等）',
  KEY        idx_create_time   (create_time)                      COMMENT '后台按创建时间倒序分页'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短链映射表';

-- ---------------------------------------------------------------------------
-- 2. 访问明细表
--    写入量最大的一张表。后续可做：按 stat_date 分区 / 定时归档 / 分表
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS t_link_access_log;
CREATE TABLE t_link_access_log (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT           COMMENT '自增主键',
  short_code   VARCHAR(16)     NOT NULL                          COMMENT '短码',
  link_id      BIGINT UNSIGNED NULL DEFAULT NULL                 COMMENT '短链表 id（冗余，便于聚合）',
  stat_date    DATE            NOT NULL                          COMMENT '访问日期（冗余，便于按天归档/分区）',
  access_time  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '访问时间（毫秒精度）',
  client_ip    VARCHAR(45)     NOT NULL DEFAULT ''               COMMENT '客户端 IP，兼容 IPv6',
  user_agent   VARCHAR(512)    NOT NULL DEFAULT ''               COMMENT 'UA',
  referer      VARCHAR(512)    NOT NULL DEFAULT ''               COMMENT '来源页面',
  PRIMARY KEY (id),
  KEY idx_code_date (short_code, stat_date)                      COMMENT '按短码 + 日期查明细',
  KEY idx_stat_date (stat_date)                                  COMMENT '按日期批量归档'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='访问明细日志表';

-- ---------------------------------------------------------------------------
-- 3. 按天聚合统计表
--    PV 由异步任务累加；UV 用近似值（HyperLogLog 或 去重 IP 估算）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS t_link_stats_daily;
CREATE TABLE t_link_stats_daily (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT           COMMENT '自增主键',
  short_code   VARCHAR(16)     NOT NULL                          COMMENT '短码',
  stat_date    DATE            NOT NULL                          COMMENT '统计日期',
  pv           BIGINT UNSIGNED NOT NULL DEFAULT 0                COMMENT '页面访问量',
  uv           BIGINT UNSIGNED NOT NULL DEFAULT 0                COMMENT '独立访客（近似值）',
  update_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_code_date (short_code, stat_date)                COMMENT '同一短码同一天只有一行，支撑 upsert 累加',
  KEY        idx_stat_date (stat_date)                           COMMENT '按日期范围查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按天聚合统计表';

-- ---------------------------------------------------------------------------
-- 4. 号段发号器（可选）
--    仅在 Day5 选择"号段模式"而非"Redis INCR"时启用
--    原理：一次从 DB 取走 step 个序号，在内存里逐个发，减少 DB 往返
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS t_segment_alloc;
CREATE TABLE t_segment_alloc (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT           COMMENT '自增主键',
  biz_tag      VARCHAR(64)     NOT NULL                          COMMENT '业务标识，如 short_link',
  max_id       BIGINT UNSIGNED NOT NULL DEFAULT 0                COMMENT '当前已分配到的最大序号',
  step         INT UNSIGNED    NOT NULL DEFAULT 1000             COMMENT '号段步长',
  update_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_tag (biz_tag)                                COMMENT '一个业务一条记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='号段发号器（可选）';

-- 号段模式初始化数据（走号段模式时才需要执行）
INSERT INTO t_segment_alloc (biz_tag, max_id, step)
VALUES ('short_link', 0, 1000)
ON DUPLICATE KEY UPDATE max_id = max_id;

-- ============================================================================
-- 常用验证 SQL（开发期随手用）
-- ============================================================================
-- 查看表结构
-- SHOW CREATE TABLE t_short_link;
-- 查看索引
-- SHOW INDEX FROM t_short_link;
-- 验证幂等：同一 md5 只能有一行
-- SELECT origin_url_md5, COUNT(*) c FROM t_short_link GROUP BY origin_url_md5 HAVING c > 1;
-- 统计当日 PV（聚合表 vs 明细表对账）
-- SELECT short_code, pv FROM t_link_stats_daily WHERE stat_date = CURDATE();
-- SELECT short_code, COUNT(*) FROM t_link_access_log WHERE stat_date = CURDATE() GROUP BY short_code;
