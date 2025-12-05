package com.github.pwittchen.varun.config;

import com.github.pwittchen.varun.service.AggregatorService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class MetricsConfig {

    private final ConcurrentMap<String, AtomicLong> lastSuccessTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> taskSuccessCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> taskFailureCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> taskTimers = new ConcurrentHashMap<>();

    public MetricsConfig(MeterRegistry meterRegistry, AggregatorService aggregatorService) {
        // Application metrics
        Gauge.builder("varun_spots_total", aggregatorService::countSpots)
                .description("Total number of kite spots")
                .register(meterRegistry);

        Gauge.builder("varun_countries_total", aggregatorService::countCountries)
                .description("Total number of countries with kite spots")
                .register(meterRegistry);

        Gauge.builder("varun_live_stations_total", aggregatorService::countLiveStations)
                .description("Number of live weather stations currently reporting")
                .register(meterRegistry);

        // Cache metrics
        Gauge.builder("varun_cache_forecasts_size", aggregatorService::getForecastCacheSize)
                .description("Number of forecasts in cache")
                .register(meterRegistry);

        Gauge.builder("varun_cache_conditions_size", aggregatorService::getCurrentConditionsCacheSize)
                .description("Number of current conditions in cache")
                .register(meterRegistry);

        Gauge.builder("varun_cache_ai_analysis_size", aggregatorService::getAiAnalysisCacheSize)
                .description("Number of AI analyses in cache")
                .register(meterRegistry);

        Gauge.builder("varun_cache_coordinates_size", aggregatorService::getCoordinatesCacheSize)
                .description("Number of coordinates in cache")
                .register(meterRegistry);

        // Scheduler task metrics
        String[] tasks = {"forecasts", "conditions", "ai_analysis_en", "ai_analysis_pl"};
        for (String task : tasks) {
            lastSuccessTimestamps.put(task, new AtomicLong(0));

            Gauge.builder("varun_scheduler_last_success_timestamp", lastSuccessTimestamps.get(task)::get)
                    .description("Timestamp of last successful scheduled task execution")
                    .tag("task", task)
                    .register(meterRegistry);

            taskSuccessCounters.put(task, Counter.builder("varun_scheduler_executions_total")
                    .description("Total scheduled task executions")
                    .tag("task", task)
                    .tag("status", "success")
                    .register(meterRegistry));

            taskFailureCounters.put(task, Counter.builder("varun_scheduler_executions_total")
                    .description("Total scheduled task executions")
                    .tag("task", task)
                    .tag("status", "failure")
                    .register(meterRegistry));

            taskTimers.put(task, Timer.builder("varun_scheduler_duration_seconds")
                    .description("Duration of scheduled task execution")
                    .tag("task", task)
                    .register(meterRegistry));
        }
    }

    public void recordTaskSuccess(String task) {
        lastSuccessTimestamps.get(task).set(System.currentTimeMillis());
        taskSuccessCounters.get(task).increment();
    }

    public void recordTaskFailure(String task) {
        taskFailureCounters.get(task).increment();
    }

    public Timer getTaskTimer(String task) {
        return taskTimers.get(task);
    }

    @Aspect
    @Component
    public static class SchedulerMetricsAspect {

        private final MetricsConfig metricsConfig;

        public SchedulerMetricsAspect(MetricsConfig metricsConfig) {
            this.metricsConfig = metricsConfig;
        }

        @Around("execution(* com.github.pwittchen.varun.service.AggregatorService.fetchForecastsEveryThreeHours(..))")
        public Object measureForecastsTask(ProceedingJoinPoint joinPoint) throws Throwable {
            return measureTask(joinPoint, "forecasts");
        }

        @Around("execution(* com.github.pwittchen.varun.service.AggregatorService.fetchCurrentConditionsEveryOneMinute(..))")
        public Object measureConditionsTask(ProceedingJoinPoint joinPoint) throws Throwable {
            return measureTask(joinPoint, "conditions");
        }

        @Around("execution(* com.github.pwittchen.varun.service.AggregatorService.fetchAiAnalysisEveryEightHoursEn(..))")
        public Object measureAiAnalysisEnTask(ProceedingJoinPoint joinPoint) throws Throwable {
            return measureTask(joinPoint, "ai_analysis_en");
        }

        @Around("execution(* com.github.pwittchen.varun.service.AggregatorService.fetchAiAnalysisEveryEightHoursPl(..))")
        public Object measureAiAnalysisPlTask(ProceedingJoinPoint joinPoint) throws Throwable {
            return measureTask(joinPoint, "ai_analysis_pl");
        }

        private Object measureTask(ProceedingJoinPoint joinPoint, String taskName) throws Throwable {
            Timer.Sample sample = Timer.start();
            try {
                Object result = joinPoint.proceed();
                metricsConfig.recordTaskSuccess(taskName);
                return result;
            } catch (Throwable t) {
                metricsConfig.recordTaskFailure(taskName);
                throw t;
            } finally {
                sample.stop(metricsConfig.getTaskTimer(taskName));
            }
        }
    }
}