package com.itqiuan.shortlink.converter;

import com.itqiuan.shortlink.dto.ShortLinkCreateDTO;
import com.itqiuan.shortlink.entity.ShortLink;
import com.itqiuan.shortlink.vo.ShortLinkVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * 转换层：在 DTO / VO / Entity 三者之间搬运字段。
 * <p>
 * 单独成类的原因：分层要求 Controller 只碰 DTO/VO、Mapper 只碰 Entity，
 * 两者之间的字段搬运若散在 Service 里，会到处出现 new + set 的噪音。
 * <p>
 * 注意 {@code shortUrl} 是在这里<b>拼出来</b>的（配置前缀 + 短码），而不是库里存的字段。
 */
@Component
public class ShortLinkConverter {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${app.short-domain}")
    private String prefixUrl;

    /**
     * DTO → Entity（只搬用户填的那三个字段，其余字段由后续步骤补全）。
     */
    public ShortLink toShortLink(ShortLinkCreateDTO shortLinkCreateDTO) {
        ShortLink shortLink = new ShortLink();
        shortLink.setOriginUrl(shortLinkCreateDTO.getOriginUrl());
        shortLink.setShortCode(shortLinkCreateDTO.getCustomCode());
        shortLink.setExpireTime(shortLinkCreateDTO.getExpireTime());
        return shortLink;
    }


    /**
     * Entity → VO。
     * <p>
     * 两点要留意：
     * <ul>
     *   <li>{@code shortUrl} 不是库里存的字段，而是用配置前缀（app.short-domain）+ 短码拼出来的；</li>
     *   <li>时间字段被格式化成字符串（yyyy-MM-dd HH:mm:ss）再返回 —— 对外统一用字符串，避免前端各自处理时区。</li>
     * </ul>
     */
    public ShortLinkVO toShortLinkVO(ShortLink shortLink) {
        ShortLinkVO shortLinkVO = new ShortLinkVO();

        shortLinkVO.setShortCode(shortLink.getShortCode());
        shortLinkVO.setShortUrl(prefixUrl + shortLink.getShortCode());
        shortLinkVO.setOriginUrl(shortLink.getOriginUrl());
        if (shortLink.getExpireTime() != null) {
            shortLinkVO.setExpireTime(shortLink.getExpireTime().format(formatter));
        }
        if (shortLink.getCreateTime() != null) {
            shortLinkVO.setCreateTime(shortLink.getCreateTime().format(formatter));
        }
        return shortLinkVO;
    }

}
