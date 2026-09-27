package com.itqiuan.shortlink.service;

import com.itqiuan.shortlink.dto.ShortLinkCreateDTO;
import com.itqiuan.shortlink.vo.ShortLinkVO;
import jakarta.validation.Valid;

public interface ShortLinkService {
    ShortLinkVO createShortLink(@Valid ShortLinkCreateDTO shortLinkCreateDTO);
}
