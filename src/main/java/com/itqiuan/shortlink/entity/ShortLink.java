package com.itqiuan.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短链表实体，对应表 {@code t_short_link}。
 * <p>
 * 表上有两个唯一索引，创建流程就是围绕它们做冲突处理：
 * <ul>
 *   <li>{@code short_code} —— 短码唯一（自定义短码撞了报 40006，自动发号撞了就换号重试）；</li>
 *   <li>{@code origin_url_md5} —— 原始 URL 指纹唯一（保证同一 URL 不重复建）。</li>
 * </ul>
 * <p>
 * 两个约定：① 主键 {@code id} 交给数据库自增（{@code IdType.AUTO}），不用雪花 ID；
 * ② {@code createTime}/{@code updateTime} 由数据库列默认值负责，插入后实体里仍是 null ——
 * 需要它们时得 {@code selectById} 重查一次。
 */
@TableName("t_short_link")
@Data
public class ShortLink {

    /** 主键，数据库自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 短码（4~16 位 Base62），对外唯一标识。 */
    private String shortCode;

    /** 发号得到的原始序号（短码是它在固定偏移上编码的结果，保留便于排查）。 */
    private Long seqNo;

    /** 原始长链接。 */
    private String originUrl;

    /** originUrl 的 MD5，配合唯一索引做创建幂等。 */
    private String originUrlMd5;

    /** 状态：1 正常 / 0 禁用（见 LinkStatusEnum）。 */
    private Integer status;

    /** 过期时间，null 表示永不过期。 */
    private LocalDateTime expireTime;

    /** 创建时间，由数据库默认值填充（插入后实体里为 null）。 */
    private LocalDateTime createTime;

    /** 更新时间，由数据库自动维护。 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 未删 / 1 已删（全局配置 logic-delete-field，查询自动过滤）。 */
    private Integer delFlag;

}
