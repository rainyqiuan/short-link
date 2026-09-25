package com.itqiuan.shortlink.service;

import com.itqiuan.shortlink.dto.ShortLinkCreateDTO;
import com.itqiuan.shortlink.vo.ShortLinkVO;

public interface ShortLinkService {
    ShortLinkVO createShortLink(ShortLinkCreateDTO shortLinkCreateDTO);
}
