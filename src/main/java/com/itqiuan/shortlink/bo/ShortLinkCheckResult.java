package com.itqiuan.shortlink.bo;

import com.itqiuan.shortlink.common.enums.LinkUsabilityEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShortLinkCheckResult {

    private LinkUsabilityEnum linkUsabilityEnum;
    private String originUrl;

    public static ShortLinkCheckResult success(String originUrl) {
        return new ShortLinkCheckResult(LinkUsabilityEnum.REUSABLE, originUrl);
    }

    public static ShortLinkCheckResult fail(LinkUsabilityEnum status) {
        return new ShortLinkCheckResult(status, null);
    }
}
