package com.itqiuan.shortlink.service;

import com.itqiuan.shortlink.bo.ShortLinkCheckResult;

public interface RedirectService {
    ShortLinkCheckResult redirect(String shortCode);
}
