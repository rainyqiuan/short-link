package com.itqiuan.shortlink.converter;

import com.itqiuan.shortlink.dto.ShortLinkCreateDTO;
import com.itqiuan.shortlink.entity.ShortLink;
import com.itqiuan.shortlink.vo.ShortLinkVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class ShortLinkConverter {

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${app.short-domain}")
    private String prefixUrl;

    /**
     * dto 转 entity
     * @param shortLinkCreateDTO
     * @return
     */
    public ShortLink toShortLink(ShortLinkCreateDTO shortLinkCreateDTO) {
        ShortLink shortLink=new ShortLink();
        shortLink.setOriginUrl(shortLinkCreateDTO.getOriginUrl());
        shortLink.setShortCode(shortLinkCreateDTO.getCustomCode());
        if(shortLinkCreateDTO.getExpireTime() != null){
            shortLink.setExpireTime(LocalDateTime.parse(shortLinkCreateDTO.getExpireTime(), formatter));
        }
        return shortLink;
    }


    /**
     * entity转vo
     * @param shortLink
     * @return
     */
    public ShortLinkVO toShortLinkVO(ShortLink shortLink) {
        ShortLinkVO shortLinkVO=new ShortLinkVO();

        shortLinkVO.setShortCode(shortLink.getShortCode());
        shortLinkVO.setShortUrl(prefixUrl + shortLink.getShortCode());
        shortLinkVO.setOriginUrl(shortLink.getOriginUrl());
        if(shortLink.getExpireTime() != null){
            shortLinkVO.setExpireTime(shortLink.getExpireTime().format(formatter));
        }
        if(shortLink.getCreateTime() != null){
            shortLinkVO.setCreateTime(shortLink.getCreateTime().format(formatter));
        }
        return shortLinkVO;
    }

}
