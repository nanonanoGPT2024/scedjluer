package co.id.mcs.dika.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async Executor Configuration
 * Thread pool configuration for async operations
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Value("${async.executor.core-pool-size:10}")
    private int corePoolSize;

    @Value("${async.executor.max-pool-size:50}")
    private int maxPoolSize;

    @Value("${async.executor.queue-capacity:500}")
    private int queueCapacity;

    /**
     * Default async executor for general purpose async operations
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        log.info("Configuring Async Task Executor");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("Async executor configured: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }

    /**
     * CPU-intensive tasks executor
     * Thread pool size = CPU cores
     */
    @Bean(name = "cpuExecutor")
    public Executor cpuExecutor() {
        log.info("Configuring CPU-intensive Task Executor");

        int cores = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cores);
        executor.setMaxPoolSize(cores);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cpu-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();

        log.info("CPU executor configured with {} cores", cores);

        return executor;
    }

    /**
     * I/O-intensive tasks executor
     * Higher thread count for I/O operations
     */
    @Bean(name = "ioExecutor")
    public Executor ioExecutor() {
        log.info("Configuring I/O-intensive Task Executor");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(50);
        executor.setMaxPoolSize(200);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("io-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();

        log.info("I/O executor configured: core=50, max=200, queue=1000");

        return executor;
    }
}
