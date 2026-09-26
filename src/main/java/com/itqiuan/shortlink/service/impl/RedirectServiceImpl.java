package com.itqiuan.shortlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itqiuan.shortlink.bo.ShortLinkCheckResult;
import com.itqiuan.shortlink.common.enums.LinkUsabilityEnum;
import com.itqiuan.shortlink.entity.ShortLink;
import com.itqiuan.shortlink.mapper.ShortLinkMapper;
import com.itqiuan.shortlink.service.RedirectService;
import com.itqiuan.shortlink.service.support.ShortLinkClassifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RedirectServiceImpl implements RedirectService {

    @Autowired
    private ShortLinkMapper shortLinkMapper;
    @Autowired
    private ShortLinkClassifier shortLinkClassifier;

    @Override
    public ShortLinkCheckResult redirect(String shortCode) {
        ShortLink shortLink= shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, shortCode));
        return setShortLinkCheckResult(shortLink);
    }

    private ShortLinkCheckResult setShortLinkCheckResult(ShortLink shortLink) {
        switch (shortLinkClassifier.classify(shortLink)) {
            case REUSABLE:
                return ShortLinkCheckResult.success(shortLink.getOriginUrl());
            case DISABLED:
                return ShortLinkCheckResult.fail(LinkUsabilityEnum.DISABLED);
            case EXPIRED:
                return ShortLinkCheckResult.fail(LinkUsabilityEnum.EXPIRED);
            case NOT_FOUND:
                return ShortLinkCheckResult.fail(LinkUsabilityEnum.NOT_FOUND);
            case UNKNOWN:
            default:
                return ShortLinkCheckResult.fail(LinkUsabilityEnum.UNKNOWN);
        }
    }

}
