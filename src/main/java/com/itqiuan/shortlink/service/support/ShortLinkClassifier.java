package com.itqiuan.shortlink.service.support;

import com.itqiuan.shortlink.common.enums.LinkStatusEnum;
import com.itqiuan.shortlink.common.enums.LinkUsabilityEnum;
import com.itqiuan.shortlink.entity.ShortLink;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ShortLinkClassifier {
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
