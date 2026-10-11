package com.platform.notification;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;

/** A flood of mail never turns into an error for the request that caused it. */
class EmailExecutorConfigTests {

    @Test
    void aFullQueueDropsTheMessageInsteadOfThrowingIntoTheRequest() throws Exception {
        Executor executor = new EmailExecutorConfig().emailExecutor();
        CountDownLatch release = new CountDownLatch(1);
        try {
            // 4 busy threads and 500 waiting fill it; the next one has nowhere to go.
            for (int i = 0; i < 504; i++) {
                executor.execute(() -> await(release));
            }
            assertThatCode(() -> executor.execute(() -> { })).doesNotThrowAnyException();
        } finally {
            release.countDown();
            ((ThreadPoolTaskExecutor) executor).shutdown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
