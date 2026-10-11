package com.platform.notification;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** A small, bounded pool for email delivery, so mail never occupies request threads. */
@Configuration
@EnableAsync
public class EmailExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailExecutorConfig.class);

    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("email-");
        // A full queue drops the message with an error in the log (the person can ask again, like any
        // failed delivery) instead of failing the request whose data was already committed.
        executor.setRejectedExecutionHandler((task, pool) ->
                log.error("Email queue full ({} waiting); a message was not sent", pool.getQueue().size()));
        executor.initialize();
        return executor;
    }

}
