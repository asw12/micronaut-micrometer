package io.micronaut.configuration.metrics.binder.retry

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.search.RequiredSearch
import io.micronaut.context.ApplicationContext
import io.micronaut.retry.annotation.Retryable
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import static io.micronaut.configuration.metrics.binder.retry.RetryMetricsBinder.RETRY_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static org.junit.Assert.assertThrows

class RetryMetricsBinderSpec extends Specification {

    void "test retry metrics"() {
        when:
        ApplicationContext context = ApplicationContext.run([(RETRY_METRICS_ENABLED): true])

        def bean = context.getBean(RetryTester)
        assertThrows(RuntimeException.class) {
            bean.doWork()
        }

        MeterRegistry registry = context.getBean(MeterRegistry)

        RequiredSearch search = registry.get("micronaut.retry.attempt.total")
        def typeName = 'io.micronaut.configuration.metrics.binder.retry.RetryMetricsBinderSpec$RetryTester'
        search.tags("declaring_type", typeName, "method_description", "void doWork()")
        Counter attempts = search.counter()

        then: "We should record 4 attempts"
        attempts.count() == 4

        cleanup:
        context.close()
    }

    void "test retry parallelism"() {
        when:
        ApplicationContext context = ApplicationContext.run([(RETRY_METRICS_ENABLED): true])

        def bean = context.getBean(RetryTester)
        ExecutorService executor = Executors.newFixedThreadPool(10)
        10.times {
            executor.submit {
                try {
                    bean.doWork()
                } catch (RuntimeException ignored) {
                }
            }
        }

        MeterRegistry registry = context.getBean(MeterRegistry)

        RequiredSearch search = registry.get("micronaut.retry.attempt.total")
        def typeName = 'io.micronaut.configuration.metrics.binder.retry.RetryMetricsBinderSpec$RetryTester'
        search.tags("declaring_type", typeName, "method_description", "void doWork()")
        Counter attempts = search.counter()

        then: "We should record 40 attempts"
        attempts.count() == 40

        cleanup:
        context.close()
    }

    @Unroll
    void "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(RetryMetricsBinder).isPresent() == inContext

        cleanup:
        context.close()

        where:
        cfg                       | setting | inContext
        MICRONAUT_METRICS_ENABLED | true    | false
        MICRONAUT_METRICS_ENABLED | false   | false
        RETRY_METRICS_ENABLED     | true    | true
        RETRY_METRICS_ENABLED     | false   | false
    }

    @Singleton
    static class RetryTester {
        @Retryable(attempts = "4", delay = "0s")
        void doWork() {
            throw new RuntimeException("fail")
        }
    }

}

