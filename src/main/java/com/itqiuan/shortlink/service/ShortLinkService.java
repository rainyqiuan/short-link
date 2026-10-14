package com.itqiuan.shortlink.service;

import com.itqiuan.shortlink.dto.ShortLinkCreateDTO;
import com.itqiuan.shortlink.vo.ShortLinkVO;
import jakarta.validation.Valid;

/**
 * 短链创建服务。
 */
public interface ShortLinkService {

    /**
     * 创建短链（幂等：同一 URL 重复提交返回同一条）。
     * <p>
     * 实现里故意不加 {@code @Transactional}，事务边界在每一轮重试内部 —— 详见实现类。
     *
     * @return 创建结果（短码、完整短链 URL、时间等）
     */
    ShortLinkVO createShortLink(@Valid ShortLinkCreateDTO shortLinkCreateDTO);
}
