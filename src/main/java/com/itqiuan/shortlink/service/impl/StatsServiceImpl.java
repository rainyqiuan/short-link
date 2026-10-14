package com.itqiuan.shortlink.service.impl;

import com.itqiuan.shortlink.common.constant.RedisKeyConstant;
import com.itqiuan.shortlink.common.exception.BusinessException;
import com.itqiuan.shortlink.common.result.ResultCode;
import com.itqiuan.shortlink.entity.LinkStatsDaily;
import com.itqiuan.shortlink.mapper.LinkAccessLogMapper;
import com.itqiuan.shortlink.mapper.LinkStatsDailyMapper;
import com.itqiuan.shortlink.service.StatsService;
import com.itqiuan.shortlink.vo.LinkStatsVO;
import com.itqiuan.shortlink.vo.StatsItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 统计实现：定时聚合（PV 来自明细表、UV 来自 HyperLogLog）与查询。
 * <p>
 * 分工：PV 精确、来自访问明细表；UV 是估算、来自 Redis 的 HyperLogLog ——
 * 用 HLL 而不是 Set 存访客，是为了把"去重计数"的内存从 O(人数) 降到常数级，
 * 代价是结果有约 0.81% 的误差（短链统计场景可接受）。
 */
@Service
@Slf4j
public class StatsServiceImpl implements StatsService {

    private final LinkAccessLogMapper linkAccessLogMapper;
    private final LinkStatsDailyMapper linkStatsDailyMapper;
    private final StringRedisTemplate stringRedisTemplate;


    public StatsServiceImpl(LinkAccessLogMapper linkAccessLogMapper, LinkStatsDailyMapper linkStatsDailyMapper, StringRedisTemplate stringRedisTemplate) {
        this.linkAccessLogMapper = linkAccessLogMapper;
        this.linkStatsDailyMapper = linkStatsDailyMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 聚合最近 lookbackDays 天：先按区间查出每条短码每天的 PV，再逐个补上 UV，最后批量 upsert。
     * <p>
     * UV 取不到（Redis 异常）时兜底为 0，而不让整个聚合失败 —— 统计是旁路，不该拖垮主流程。
     */
    @Override
    public void aggregate(int lookbackDays) {
        long startTime = System.currentTimeMillis();
        //统计页面访问量
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(lookbackDays - 1);
        List<LinkStatsDaily> linkStatsDailies = linkAccessLogMapper.selectPvByDateRange(start, end);
        if (linkStatsDailies.isEmpty()) {
            log.info("窗口内无明细，跳过");
            return;
        }
        for (LinkStatsDaily item : linkStatsDailies) {
            // 1. 用当前这行数据的 短链码 和 具体日期 拼出 Redis key
            String uvKey = RedisKeyConstant.uvKey(item.getShortCode(), item.getStatDate());
            Long uv;

            try {
                // 2. 从 HyperLogLog 获取 UV 估算值
                uv = stringRedisTemplate.opsForHyperLogLog().size(uvKey);
            } catch (DataAccessException e) {
                log.warn("从 HyperLogLog 获取 UV 估算值时出错", e);
                uv = 0L;
            }

            // 3. 判空兜底 (null -> 0L) 并塞回对象
            item.setUv(uv == null ? 0L : uv);
        }
        linkStatsDailyMapper.upsertBatch(linkStatsDailies);
        log.info("统计完成,数量：{}，耗时：{}",linkStatsDailies.size(),System.currentTimeMillis() - startTime);
    }

    /**
     * 查询统计：先校验入参（起止顺序、跨度上限 90 天），再查明细并汇总。
     * <p>
     * totalUv 是把每天的 UV 直接相加（人日合计），不是区间去重人数 —— 见 LinkStatsVO 注释。
     */
    @Override
    public LinkStatsVO queryStats(String code, LocalDate start, LocalDate end) {
        if(start.isAfter(end)){
            throw new BusinessException(ResultCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        }
        if(ChronoUnit.DAYS.between(start, end) >= 90){
            throw new BusinessException(ResultCode.PARAM_ERROR, "日期范围不能超过90天");
        }

        // 1. 查每天的明细列表
        List<StatsItemVO> items = linkStatsDailyMapper.selectByCodeAndRange(code, start, end);

        // 2. 通过 Stream 流把每天的 pv 和 uv 分别加起来，算出 total
        // 算的是人日，不是人数
        long totalPv = items.stream().mapToLong(StatsItemVO::getPv).sum();
        long totalUv = items.stream().mapToLong(StatsItemVO::getUv).sum();

        // 3. 组装最终的 VO
        LinkStatsVO vo = new LinkStatsVO();
        vo.setShortCode(code);
        vo.setTotalPv(totalPv);
        vo.setTotalUv(totalUv);
        vo.setItems(items);

        return vo;
    }
}
