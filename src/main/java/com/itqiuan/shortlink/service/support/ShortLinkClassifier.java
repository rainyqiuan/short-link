package com.itqiuan.shortlink.service.support;

import com.itqiuan.shortlink.common.enums.LinkStatusEnum;
import com.itqiuan.shortlink.common.enums.LinkUsabilityEnum;
import com.itqiuan.shortlink.entity.ShortLink;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 短链可用性分类器：把"状态 + 过期时间"翻译成 {@link LinkUsabilityEnum}。
 * <p>
 * 单独抽出来的意义：同一套判定要被多处复用（查库后、读缓存后、判重后），
 * 散在各处极易出现"某个分支忘了判过期"这类不一致。
 */
@Component
public class ShortLinkClassifier {

    /**
     * 按实体分类（查库路径用）。
     *
     * @param shortLink 数据库记录，允许为 null（视为不存在）
     */
    public LinkUsabilityEnum classify(ShortLink shortLink){
        if (shortLink == null) {
            return LinkUsabilityEnum.NOT_FOUND;
        }
        if (shortLink.getStatus() == LinkStatusEnum.NORMAL.getCode() && (shortLink.getExpireTime() == null || shortLink.getExpireTime().isAfter(LocalDateTime.now()))) {
            return LinkUsabilityEnum.REUSABLE;
        }
        if (shortLink.getStatus() == LinkStatusEnum.DISABLE.getCode()) {
            return LinkUsabilityEnum.DISABLED;
        }
        if (shortLink.getExpireTime() != null && shortLink.getExpireTime().isBefore(LocalDateTime.now())) {
            return LinkUsabilityEnum.EXPIRED;
        }
        return LinkUsabilityEnum.UNKNOWN;
    }
    /**
     * 按字段分类（读缓存路径用：缓存里只有快照字段，没有完整实体）。
     *
     * @param status     状态码
     * @param expireTime 过期时间，null 表示永不过期
     */
    public LinkUsabilityEnum classify(int status, LocalDateTime expireTime) {
        if (status == LinkStatusEnum.NORMAL.getCode() && (expireTime == null || expireTime.isAfter(LocalDateTime.now()))) {
            return LinkUsabilityEnum.REUSABLE;
        }
        if (status == LinkStatusEnum.DISABLE.getCode()) {
            return LinkUsabilityEnum.DISABLED;
        }
        if (expireTime != null && expireTime.isBefore(LocalDateTime.now())) {
            return LinkUsabilityEnum.EXPIRED;
        }
        return LinkUsabilityEnum.UNKNOWN;
    }
}
