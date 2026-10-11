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
