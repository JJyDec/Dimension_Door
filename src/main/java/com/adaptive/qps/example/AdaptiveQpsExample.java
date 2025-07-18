package com.adaptive.qps.example;

import com.adaptive.qps.*;
import com.adaptive.qps.config.AdaptiveQpsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 自适应QPS控制系统使用示例
 * 
 * @author AI Assistant
 */
public class AdaptiveQpsExample {
    
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveQpsExample.class);
    
    public static void main(String[] args) {
        logger.info("Starting Adaptive QPS Control System Example...");
        
        // 1. 创建配置
        AdaptiveQpsConfig config = createConfig();
        
        // 2. 创建模拟的TrafficReleaseSDK
        TrafficReleaseSDK mockSDK = new MockTrafficReleaseSDK();
        
        // 3. 创建自适应QPS控制器
        AdaptiveQpsController controller = new AdaptiveQpsController(mockSDK, config);
        
        // 4. 启动控制器
        controller.start();
        
        // 5. 启动监控任务
        startMonitoring(controller);
        
        // 6. 运行一段时间后停止
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Adaptive QPS Controller...");
            controller.stop();
        }));
        
        try {
            // 运行30秒
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        controller.stop();
        logger.info("Adaptive QPS Control System Example completed.");
    }
    
    /**
     * 创建配置
     */
    private static AdaptiveQpsConfig createConfig() {
        return AdaptiveQpsConfig.builder()
            // TCP拥塞控制参数
            .initialCwnd(5)
            .initialSsthresh(100)
            .maxCongestionWindow(500)
            .vegasParams(2, 6)
            
            // 令牌桶参数
            .bucketCapacity(20, 500)
            .initialTokenRate(20)
            .burstFactor(2.5)
            
            // 反馈控制参数
            .feedbackInterval(200)
            .adjustInterval(1000)
            .releaseInterval(100)
            .pageSize(50)
            
            // 系统保护参数
            .maxQps(1000)
            .maxRetryTimes(3)
            .timeoutThreshold(5000)
            .emergencyFallbackRate(0.1)
            
            // 负载阈值
            .loadThresholds(30, 75, 90)
            
            // 调整策略
            .adjustFactors(0.7, 1.2, 0.2)
            
            // 预测参数
            .predictionAlpha(0.8)
            .healthWeight(0.5, 0.3, 0.2)
            
            .build();
    }
    
    /**
     * 启动监控任务
     */
    private static void startMonitoring(AdaptiveQpsController controller) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                AdaptiveQpsController.SystemHealthStatus status = controller.getHealthStatus();
                logger.info("System Status: {}", status);
                
                // 模拟负载变化
                if (System.currentTimeMillis() % 10000 < 2000) {
                    logger.info("Simulating high load period...");
                }
                
            } catch (Exception e) {
                logger.error("Error in monitoring task", e);
            }
        }, 2, 2, TimeUnit.SECONDS);
        
        // 关闭时停止调度器
        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));
    }
    
    /**
     * 模拟的TrafficReleaseSDK实现
     */
    private static class MockTrafficReleaseSDK implements TrafficReleaseSDK {
        
        private final Random random = new Random();
        private volatile int currentLoad = 50;
        private volatile int backlogCount = 0;
        private volatile double currentRate = 10.0;
        private long taskIdCounter = 1000;
        
        @Override
        public TrafficStats queryBacklog(int pageSize) {
            // 模拟负载变化
            updateSimulatedLoad();
            
            // 生成任务ID列表
            List<Long> taskIds = new ArrayList<>();
            int taskCount = Math.min(pageSize, backlogCount);
            for (int i = 0; i < taskCount; i++) {
                taskIds.add(taskIdCounter++);
            }
            
            TrafficStats stats = new TrafficStats(
                backlogCount,
                currentRate,
                currentLoad,
                (int) (taskIdCounter % 100),
                (int) ((taskIdCounter - 1) % 100),
                (int) ((taskIdCounter + 1) % 100),
                backlogCount / 10,
                taskIds
            );
            
            logger.debug("Mock queryBacklog: {}", stats);
            return stats;
        }
        
        @Override
        public ReleaseResult releaseTraffic(Long taskId) {
            // 模拟处理时间
            try {
                Thread.sleep(random.nextInt(50) + 10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 模拟成功率（根据当前负载调整）
            boolean success = random.nextInt(100) < (100 - currentLoad / 2);
            
            if (success) {
                backlogCount = Math.max(0, backlogCount - 1);
                currentRate += 0.1;
            } else {
                backlogCount += random.nextInt(3);
            }
            
            ReleaseResult result = new ReleaseResult(
                success,
                success ? null : "Mock failure: high load",
                backlogCount,
                currentRate,
                100.0, // 限制速率
                random.nextInt(100) + 50 // RTT
            );
            
            logger.debug("Mock releaseTraffic({}): {}", taskId, result);
            return result;
        }
        
        /**
         * 更新模拟负载
         */
        private void updateSimulatedLoad() {
            long time = System.currentTimeMillis();
            
            // 模拟周期性负载变化
            if (time % 20000 < 5000) {
                // 高负载期
                currentLoad = Math.min(95, currentLoad + random.nextInt(10));
                backlogCount += random.nextInt(20);
            } else if (time % 20000 < 10000) {
                // 中等负载期
                currentLoad = 50 + random.nextInt(20);
                backlogCount += random.nextInt(5) - 2;
            } else {
                // 低负载期
                currentLoad = Math.max(10, currentLoad - random.nextInt(10));
                backlogCount = Math.max(0, backlogCount - random.nextInt(10));
            }
            
            // 限制范围
            currentLoad = Math.max(0, Math.min(100, currentLoad));
            backlogCount = Math.max(0, Math.min(1000, backlogCount));
            currentRate = Math.max(0, Math.min(200, currentRate));
        }
    }
}