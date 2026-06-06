package com.duck.bankend.conf;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

}
