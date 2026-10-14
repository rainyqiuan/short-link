package com.itqiuan.shortlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itqiuan.shortlink.entity.ShortLink;

/**
 * 短链表 Mapper。
 * <p>
 * 这里没有定义任何方法 —— 增删改查全部用 MyBatis-Plus 的 {@code BaseMapper} 内置方法，
 * 配合 {@code LambdaQueryWrapper} 构造条件。
 * <p>
 * 这么做的原因：手写字符串 SQL（XML 或 {@code @Select}）没有编译期检查，
 * 改字段名时不会报错、只在运行时炸；用 Lambda 写法，字段名写错编译就过不去。
 */
public interface ShortLinkMapper extends BaseMapper<ShortLink> {

}
