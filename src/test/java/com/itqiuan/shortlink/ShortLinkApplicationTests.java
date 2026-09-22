package com.itqiuan.shortlink;

import com.itqiuan.shortlink.entity.ShortLink;
import com.itqiuan.shortlink.mapper.ShortLinkMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;


import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
class ShortLinkApplicationTests {

    @Autowired
    private ShortLinkMapper shortLinkMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional
    @Test
    void dateInAndOutTest() {
        log.info("===================dateInAndOutTest==================");

        // 1. 插入
        ShortLink link = new ShortLink();
        link.setShortCode("testCode");
        link.setSeqNo(10001L);
        link.setOriginUrl("https://github.com");
        link.setOriginUrlMd5("md5test123");
        link.setStatus(1);

        int rows = shortLinkMapper.insert(link);
        log.info("插入成功，影响行数: {}", rows);
        log.info("自动回填的主键 id: {}", link.getId()); // MP 会把自增主键写回实体
        assertThat(rows).isEqualTo(1);
        // 2. 按 id 查询
        ShortLink dbLink = shortLinkMapper.selectById(link.getId());
        assertThat(dbLink).isNotNull();
        assertThat(dbLink.getShortCode()).isEqualTo(link.getShortCode());
        assertThat(dbLink.getDelFlag()).isEqualTo(0);
        assertThat(dbLink.getCreateTime()).isNotNull();
        log.info("查询结果: {}", dbLink);

        shortLinkMapper.deleteById(link.getId());
        assertThat(shortLinkMapper.selectById(link.getId())).isNull();

        assertThat(jdbcTemplate.queryForObject("select del_flag from t_short_link where id = ?", Integer.class, link.getId())).isEqualTo(1);

        log.info("===================dateInAndOutTest==================");
    }
}
