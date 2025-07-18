package com.adaptive.qps;

import com.adaptive.qps.algorithm.TcpCongestionController;
import com.adaptive.qps.algorithm.AdaptiveTokenBucket;
import com.adaptive.qps.metrics.SystemMetrics;
import com.adaptive.qps.config.AdaptiveQpsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 自适应QPS控制器
 * 基于TCP拥塞控制算法和令牌桶机制实现动态流量调节
 * 
 * @author AI Assistant
 */
public class AdaptiveQpsController {
    
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveQpsController.class);
    
    private final TrafficReleaseSDK trafficReleaseSDK;
    private final AdaptiveQpsConfig config;
    
    // 核心算法组件
    private final TcpCongestionController congestionController;
    private final AdaptiveTokenBucket tokenBucket;
    private final SystemMetrics systemMetrics;
    
    // 控制参数
    private final AtomicLong currentQps = new AtomicLong(0);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // 线程池和调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private final ExecutorService workExecutor = Executors.newFixedThreadPool(10);
    
    // 统计信息
    private volatile long totalRequests = 0;
    private volatile long successRequests = 0;
    private volatile long lastUpdateTime = System.currentTimeMillis();
    
    public AdaptiveQpsController(TrafficReleaseSDK trafficReleaseSDK, AdaptiveQpsConfig config) {
        this.trafficReleaseSDK = trafficReleaseSDK;
        this.config = config;
        
        this.congestionController = new TcpCongestionController(config);
        this.tokenBucket = new AdaptiveTokenBucket(config);
        this.systemMetrics = new SystemMetrics();
        
        logger.info("AdaptiveQpsController initialized with config: {}", config);
    }
    
    /**
     * 启动自适应QPS控制器
     */
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Starting AdaptiveQpsController...");
            
            // 启动系统监控任务
            scheduler.scheduleAtFixedRate(this::monitorSystemStatus, 0, 
                config.getFeedbackIntervalMs(), TimeUnit.MILLISECONDS);
            
            // 启动QPS调整任务
            scheduler.scheduleAtFixedRate(this::adjustQps, 1000, 
                config.getAdjustIntervalMs(), TimeUnit.MILLISECONDS);
            
            // 启动流量释放任务
            scheduler.scheduleAtFixedRate(this::processTrafficRelease, 0, 
                config.getReleaseIntervalMs(), TimeUnit.MILLISECONDS);
            
            logger.info("AdaptiveQpsController started successfully");
        }
    }
    
    /**
     * 停止自适应QPS控制器
     */
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Stopping AdaptiveQpsController...");
            
            scheduler.shutdown();
            workExecutor.shutdown();
            
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                if (!workExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    workExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                workExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            logger.info("AdaptiveQpsController stopped");
        }
    }
    
    /**
     * 监控B系统状态
     */
    private void monitorSystemStatus() {
        try {
            TrafficStats stats = trafficReleaseSDK.queryBacklog(config.getPageSize());
            if (stats != null) {
                systemMetrics.updateStats(stats);
                
                // 更新拥塞控制算法
                long currentTime = System.currentTimeMillis();
                long rtt = currentTime - lastUpdateTime;
                congestionController.onFeedbackReceived(stats, rtt);
                
                // 更新令牌桶参数
                tokenBucket.updateBucketParameters(stats, congestionController.getCurrentWindow());
                
                lastUpdateTime = currentTime;
            }
        } catch (Exception e) {
            logger.error("Error monitoring system status", e);
            // 出现异常时进入保护模式
            congestionController.onTimeout();
        }
    }
    
    /**
     * 调整QPS
     */
    private void adjustQps() {
        try {
            double targetQps = calculateTargetQps();
            long newQps = Math.max(1, Math.round(targetQps));
            
            currentQps.set(newQps);
            tokenBucket.setTokenGenerationRate(newQps);
            
            logger.debug("QPS adjusted to: {}, congestion window: {}, system load: {}%", 
                newQps, congestionController.getCurrentWindow(), systemMetrics.getSystemLoad());
                
        } catch (Exception e) {
            logger.error("Error adjusting QPS", e);
        }
    }
    
    /**
     * 计算目标QPS
     */
    private double calculateTargetQps() {
        // 获取当前拥塞窗口大小
        int congestionWindow = congestionController.getCurrentWindow();
        
        // 获取当前RTT
        long currentRtt = Math.max(congestionController.getCurrentRtt(), 1);
        
        // 基础QPS = 拥塞窗口 / RTT
        double baseQps = (double) congestionWindow * 1000.0 / currentRtt;
        
        // 根据系统负载调整
        double loadFactor = calculateLoadFactor();
        
        // 根据成功率调整
        double successFactor = calculateSuccessFactor();
        
        // 最终QPS
        double targetQps = baseQps * loadFactor * successFactor;
        
        // 应用限制
        return Math.min(targetQps, config.getMaxQps());
    }
    
    /**
     * 计算负载因子
     */
    private double calculateLoadFactor() {
        int systemLoad = systemMetrics.getSystemLoad();
        
        if (systemLoad >= config.getCriticalLoadThreshold()) {
            return config.getEmergencyAdjustFactor();
        } else if (systemLoad >= config.getHighLoadThreshold()) {
            return config.getLoadAdjustFactor();
        } else if (systemLoad <= config.getLowLoadThreshold()) {
            return config.getRecoveryAdjustFactor();
        } else {
            // 线性插值
            double range = config.getHighLoadThreshold() - config.getLowLoadThreshold();
            double position = (systemLoad - config.getLowLoadThreshold()) / range;
            return 1.0 + (config.getRecoveryAdjustFactor() - 1.0) * (1.0 - position);
        }
    }
    
    /**
     * 计算成功率因子
     */
    private double calculateSuccessFactor() {
        if (totalRequests == 0) {
            return 1.0;
        }
        
        double successRate = (double) successRequests / totalRequests;
        
        if (successRate >= 0.99) {
            return 1.1; // 成功率很高，可以提升
        } else if (successRate >= 0.95) {
            return 1.0; // 成功率良好，保持
        } else if (successRate >= 0.90) {
            return 0.9; // 成功率一般，降低
        } else {
            return 0.7; // 成功率较低，大幅降低
        }
    }
    
    /**
     * 处理流量释放
     */
    private void processTrafficRelease() {
        if (!isRunning.get()) {
            return;
        }
        
        try {
            // 获取可释放的任务列表
            TrafficStats stats = trafficReleaseSDK.queryBacklog(config.getPageSize());
            if (stats == null || stats.taskIds == null || stats.taskIds.isEmpty()) {
                return;
            }
            
            // 计算这次可以释放的任务数量
            int availableTokens = tokenBucket.tryAcquire(stats.taskIds.size());
            if (availableTokens <= 0) {
                logger.debug("No tokens available, skipping traffic release");
                return;
            }
            
            // 释放任务
            int releasedCount = 0;
            for (int i = 0; i < Math.min(availableTokens, stats.taskIds.size()); i++) {
                Long taskId = stats.taskIds.get(i);
                
                // 异步执行任务释放
                workExecutor.submit(() -> {
                    try {
                        ReleaseResult result = trafficReleaseSDK.releaseTraffic(taskId);
                        updateStatistics(result);
                        
                        // 根据结果更新拥塞控制状态
                        if (result.isSuccess) {
                            congestionController.onSuccess();
                        } else {
                            congestionController.onFailure();
                        }
                        
                    } catch (Exception e) {
                        logger.error("Error releasing traffic for task: " + taskId, e);
                        congestionController.onFailure();
                    }
                });
                
                releasedCount++;
            }
            
            logger.debug("Released {} tasks, available tokens: {}", releasedCount, availableTokens);
            
        } catch (Exception e) {
            logger.error("Error in traffic release process", e);
        }
    }
    
    /**
     * 更新统计信息
     */
    private void updateStatistics(ReleaseResult result) {
        totalRequests++;
        if (result.isSuccess) {
            successRequests++;
        }
        
        // 定期重置统计信息，避免历史数据影响
        if (totalRequests % 10000 == 0) {
            totalRequests = Math.min(totalRequests, 1000);
            successRequests = Math.min(successRequests, (long)(successRequests * 0.1));
        }
    }
    
    /**
     * 获取当前QPS
     */
    public long getCurrentQps() {
        return currentQps.get();
    }
    
    /**
     * 获取系统健康状态
     */
    public SystemHealthStatus getHealthStatus() {
        return SystemHealthStatus.builder()
            .currentQps(getCurrentQps())
            .congestionWindow(congestionController.getCurrentWindow())
            .systemLoad(systemMetrics.getSystemLoad())
            .successRate(totalRequests > 0 ? (double) successRequests / totalRequests : 1.0)
            .backlogCount(systemMetrics.getBacklogCount())
            .currentRtt(congestionController.getCurrentRtt())
            .tokenBucketLevel(tokenBucket.getCurrentLevel())
            .build();
    }
    
    /**
     * 系统健康状态
     */
    public static class SystemHealthStatus {
        private long currentQps;
        private int congestionWindow;
        private int systemLoad;
        private double successRate;
        private int backlogCount;
        private long currentRtt;
        private int tokenBucketLevel;
        
        // Builder pattern implementation
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private SystemHealthStatus status = new SystemHealthStatus();
            
            public Builder currentQps(long currentQps) {
                status.currentQps = currentQps;
                return this;
            }
            
            public Builder congestionWindow(int congestionWindow) {
                status.congestionWindow = congestionWindow;
                return this;
            }
            
            public Builder systemLoad(int systemLoad) {
                status.systemLoad = systemLoad;
                return this;
            }
            
            public Builder successRate(double successRate) {
                status.successRate = successRate;
                return this;
            }
            
            public Builder backlogCount(int backlogCount) {
                status.backlogCount = backlogCount;
                return this;
            }
            
            public Builder currentRtt(long currentRtt) {
                status.currentRtt = currentRtt;
                return this;
            }
            
            public Builder tokenBucketLevel(int tokenBucketLevel) {
                status.tokenBucketLevel = tokenBucketLevel;
                return this;
            }
            
            public SystemHealthStatus build() {
                return status;
            }
        }
        
        // Getters
        public long getCurrentQps() { return currentQps; }
        public int getCongestionWindow() { return congestionWindow; }
        public int getSystemLoad() { return systemLoad; }
        public double getSuccessRate() { return successRate; }
        public int getBacklogCount() { return backlogCount; }
        public long getCurrentRtt() { return currentRtt; }
        public int getTokenBucketLevel() { return tokenBucketLevel; }
        
        @Override
        public String toString() {
            return String.format("SystemHealthStatus{qps=%d, cwnd=%d, load=%d%%, success=%.2f%%, backlog=%d, rtt=%dms, tokens=%d}",
                currentQps, congestionWindow, systemLoad, successRate * 100, backlogCount, currentRtt, tokenBucketLevel);
        }
    }
}