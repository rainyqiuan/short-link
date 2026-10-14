package com.itqiuan.shortlink.service;

import com.itqiuan.shortlink.bo.ShortLinkCheckResult;

/**
 * 短链跳转服务（读路径）。
 */
public interface RedirectService {

    /**
     * 解析短码，返回"能不能跳、跳到哪"。
     * <p>
     * 只做判定、不直接产生响应 —— 具体跳 302 还是返 404/410 由 Controller 决定。
     *
     * @return 体检结果；分类为 REUSABLE 时携带目标地址
     */
    ShortLinkCheckResult redirect(String shortCode);
}
