package com.itqiuan.shortlink.bo;

import com.itqiuan.shortlink.common.enums.LinkUsabilityEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 跳转前的"体检结果"：这条短链属于哪种可用性分类，以及（可用时的）目标地址。
 * <p>
 * 用 BO 承载 service 的查询结果，而不是直接返回 entity —— entity 是库表映射，
 * 不该长着"可用性分类"这种业务概念。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShortLinkCheckResult {

    /** 可用性分类（决定跳转怎么响应）。 */
    private LinkUsabilityEnum linkUsabilityEnum;

    /** 目标原始地址，仅当分类为 REUSABLE 时非 null。 */
    private String originUrl;

    /** 可用：带上要跳转的目标地址。 */
    public static ShortLinkCheckResult success(String originUrl) {
        return new ShortLinkCheckResult(LinkUsabilityEnum.REUSABLE, originUrl);
    }

    /** 不可用：只带分类，没有目标地址。 */
    public static ShortLinkCheckResult fail(LinkUsabilityEnum status) {
        return new ShortLinkCheckResult(status, null);
    }
}
