package com.itqiuan.shortlink.service.impl;

import com.itqiuan.shortlink.common.constant.RedisKeyConstant;
import com.itqiuan.shortlink.entity.LinkStatsDaily;
import com.itqiuan.shortlink.mapper.LinkAccessLogMapper;
import com.itqiuan.shortlink.mapper.LinkStatsDailyMapper;
import com.itqiuan.shortlink.service.StatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
            Long uv = null;

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
}
