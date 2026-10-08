package com.itqiuan.shortlink;

import com.itqiuan.shortlink.common.constant.RedisKeyConstant;
import com.itqiuan.shortlink.entity.ShortLink;
import com.itqiuan.shortlink.mapper.ShortLinkMapper;
import com.itqiuan.shortlink.service.BloomFilterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
class RebuildBloomFilterTest {
    @Autowired
    private ShortLinkMapper shortLinkMapper;
    @Autowired
    private BloomFilterService bloomFilterService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Transactional
    @Test
    public void rebuildBloomFilterTest() {
        ShortLink link = new ShortLink();
        link.setShortCode("testCode");
        link.setSeqNo(10001L);
        link.setOriginUrl("https://github.com");
        link.setOriginUrlMd5("md5test123");
        link.setStatus(1);
        shortLinkMapper.insert(link);
        bloomFilterService.rebuildBloomFilter();
        boolean testCode = bloomFilterService.mightContain("testCode");
        assertThat(testCode).isTrue();
    }

    @AfterEach
    void cleanUp() {
        stringRedisTemplate.delete(RedisKeyConstant.BLOOM_READY);
        stringRedisTemplate.delete(RedisKeyConstant.BLOOM);
    }
}
