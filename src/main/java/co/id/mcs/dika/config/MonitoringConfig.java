package co.id.mcs.dika.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import lombok.extern.slf4j.Slf4j;

/**
 * Monitoring Configuration
 * Actuator and Prometheus metrics setup
 */
@Configuration
@Slf4j
public class MonitoringConfig {

    /**
     * Register JVM metrics for monitoring
     */
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        log.info("Registering JVM Memory Metrics");
        return new JvmMemoryMetrics();
    }

    @Bean
    public JvmGcMetrics jvmGcMetrics() {
        log.info("Registering JVM GC Metrics");
        return new JvmGcMetrics();
    }

    @Bean
    public JvmThreadMetrics jvmThreadMetrics() {
        log.info("Registering JVM Thread Metrics");
        return new JvmThreadMetrics();
    }

    @Bean
    public ProcessorMetrics processorMetrics() {
        log.info("Registering Processor Metrics");
        return new ProcessorMetrics();
    }
}

