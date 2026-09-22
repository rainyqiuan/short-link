package com.itqiuan.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;

@TableName("t_short_link")
@Data
public class ShortLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String shortCode;

    private Long seqNo;

    private String originUrl;

    private String originUrlMd5;

    private Integer status;

    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer delFlag;

}
