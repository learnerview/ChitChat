package com.learnerview.chitchat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("AsyncTask-");
        executor.setRejectedExecutionHandler((r, executor1) -> {
            System.err.println("Task rejected: " + r.toString());
            // Log the rejection but don't throw exception to avoid breaking the flow
        });
        executor.initialize();
        return executor;
    }

    @Override
    public org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, java.lang.reflect.Method method, java.lang.Object[] params) {
                System.err.println("Async method " + method.getName() + " threw exception: " + ex.getMessage());
                ex.printStackTrace();
            }
        };
    }
}
