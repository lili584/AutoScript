package com.duck.bankend.conf;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 剧本生成后台任务运行时组件。
 */
@Configuration
@EnableConfigurationProperties(ScriptGenerationRuntimeProperties.class)
public class ScriptGenerationRuntimeConfig {

    @Bean(destroyMethod = "close")
    public ExecutorService scriptGenerationExecutorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.username:}") String username,
            @Value("${spring.data.redis.password:}") String password) {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        config.useSingleServer()
                .setAddress(address)
                .setUsername(StringUtils.hasText(username) ? username : null)
                .setPassword(StringUtils.hasText(password) ? password : null);
        return Redisson.create(config);
    }

}
