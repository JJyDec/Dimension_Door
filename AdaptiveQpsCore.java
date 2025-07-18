import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 自适应QPS控制核心实现
 * 基于TCP拥塞控制算法和令牌桶机制的智能流量调节
 */
public class AdaptiveQpsCore {
    
    // ==================== 核心配置参数 ====================
    private static class Config {
        // TCP拥塞控制参数
        int initialCwnd = 5;
        int maxCongestionWindow = 500;
        int vegasAlpha = 2;
        int vegasBeta = 6;
        
        // 令牌桶参数
        int minBucketCapacity = 20;
        int maxBucketCapacity = 500;
        double burstFactor = 2.5;
        
        // 系统阈值
        int lowLoadThreshold = 30;
        int highLoadThreshold = 75;
        int criticalLoadThreshold = 90;
        
        // 调整因子
        double loadAdjustFactor = 0.7;
        double recoveryAdjustFactor = 1.2;
        double emergencyAdjustFactor = 0.2;
        
        long maxQps = 1000;
    }
    
    // ==================== 数据结构定义 ====================
    
    /**
     * 流量统计信息
     */
    public static class TrafficStats {
        public int backlogCount;      // 积压请求数
        public double currentRate;    // 当前释放速率
        public int systemLoad;        // 系统负载百分比
        public List<Long> taskIds;    // 待释放的任务ID
        
        public TrafficStats(int backlogCount, double currentRate, int systemLoad, List<Long> taskIds) {
            this.backlogCount = backlogCount;
            this.currentRate = currentRate;
            this.systemLoad = systemLoad;
            this.taskIds = taskIds;
        }
    }
    
    /**
     * 释放结果
     */
    public static class ReleaseResult {
        public boolean isSuccess;
        public String failureReason;
        public long rtt;
        
        public ReleaseResult(boolean isSuccess, String failureReason, long rtt) {
            this.isSuccess = isSuccess;
            this.failureReason = failureReason;
            this.rtt = rtt;
        }
    }
    
    /**
     * 流量调整参数
     */
    public static class TrafficParameters {
        public long targetQps;           // 目标QPS
        public int congestionWindow;     // 拥塞窗口大小
        public int tokenBucketCapacity;  // 令牌桶容量
        public double tokenGenerationRate; // 令牌生成速率
        public String adjustReason;      // 调整原因
        
        public TrafficParameters(long targetQps, int congestionWindow, 
                               int tokenBucketCapacity, double tokenGenerationRate, String adjustReason) {
            this.targetQps = targetQps;
            this.congestionWindow = congestionWindow;
            this.tokenBucketCapacity = tokenBucketCapacity;
            this.tokenGenerationRate = tokenGenerationRate;
            this.adjustReason = adjustReason;
        }
        
        @Override
        public String toString() {
            return String.format("TrafficParameters{qps=%d, cwnd=%d, bucket=%d, rate=%.2f, reason='%s'}", 
                targetQps, congestionWindow, tokenBucketCapacity, tokenGenerationRate, adjustReason);
        }
    }
    
    /**
     * 批量释放结果
     */
    public static class BatchReleaseResult {
        public int totalRequests;        // 总请求数
        public int successCount;         // 成功数量
        public int failureCount;         // 失败数量
        public double avgRtt;            // 平均RTT
        public List<ReleaseResult> details; // 详细结果
        
        public BatchReleaseResult(int totalRequests, int successCount, int failureCount, 
                                double avgRtt, List<ReleaseResult> details) {
            this.totalRequests = totalRequests;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.avgRtt = avgRtt;
            this.details = details;
        }
        
        public double getSuccessRate() {
            return totalRequests > 0 ? (double) successCount / totalRequests : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("BatchReleaseResult{total=%d, success=%d, failure=%d, rate=%.2f%%, rtt=%.2fms}", 
                totalRequests, successCount, failureCount, getSuccessRate() * 100, avgRtt);
        }
    }
    
    // ==================== 核心状态变量 ====================
    
    private final Config config = new Config();
    
    // TCP拥塞控制状态
    private final AtomicInteger congestionWindow = new AtomicInteger(5);
    private final AtomicInteger slowStartThreshold = new AtomicInteger(100);
    private volatile CongestionState currentState = CongestionState.SLOW_START;
    private final AtomicLong baseRtt = new AtomicLong(100);
    private final AtomicLong currentRtt = new AtomicLong(100);
    private volatile int consecutiveSuccesses = 0;
    private volatile int consecutiveFailures = 0;
    
    // 令牌桶状态
    private final AtomicInteger bucketCapacity = new AtomicInteger(50);
    private final AtomicInteger currentTokens = new AtomicInteger(50);
    private final AtomicLong tokenGenerationRate = new AtomicLong(20);
    private volatile long lastRefillTime = System.currentTimeMillis();
    private final ReentrantLock refillLock = new ReentrantLock();
    
    // 系统指标
    private volatile double avgSystemLoad = 0.0;
    private volatile long updateCount = 0;
    private static final double SMOOTHING_FACTOR = 0.8;
    
    /**
     * 拥塞控制状态枚举
     */
    public enum CongestionState {
        SLOW_START,           // 慢启动
        CONGESTION_AVOIDANCE, // 拥塞避免
        FAST_RECOVERY        // 快速恢复
    }
    
    // ==================== 核心函数一：调整限流参数 ====================
    
    /**
     * 根据B系统状态动态调整流量控制参数
     * 融合TCP拥塞控制算法和令牌桶机制
     * 
     * @param stats B系统当前状态统计信息
     * @return 调整后的流量控制参数
     */
    public TrafficParameters adjustTrafficParameters(TrafficStats stats) {
        updateSystemMetrics(stats);
        
        // 1. TCP拥塞控制算法调整拥塞窗口
        adjustCongestionWindow(stats);
        
        // 2. 计算目标QPS
        long targetQps = calculateTargetQps(stats);
        
        // 3. 动态调整令牌桶参数
        adjustTokenBucketParameters(targetQps, stats);
        
        // 4. 生成调整原因说明
        String adjustReason = generateAdjustReason(stats);
        
        return new TrafficParameters(
            targetQps,
            congestionWindow.get(),
            bucketCapacity.get(),
            tokenGenerationRate.get(),
            adjustReason
        );
    }
    
    // ==================== 核心函数二：批量释放流量 ====================
    
    /**
     * 基于令牌桶机制批量释放流量
     * 确保不超过系统处理能力的同时最大化吞吐量
     * 
     * @param taskIds 待释放的任务ID列表
     * @param releaseFunction 实际执行释放的函数接口
     * @return 批量释放结果
     */
    public BatchReleaseResult releaseTrafficBatch(List<Long> taskIds, 
                                                 java.util.function.Function<Long, ReleaseResult> releaseFunction) {
        if (taskIds == null || taskIds.isEmpty()) {
            return new BatchReleaseResult(0, 0, 0, 0.0, new ArrayList<>());
        }
        
        // 1. 补充令牌桶
        refillTokens();
        
        // 2. 计算可释放的任务数量
        int availableTokens = Math.min(currentTokens.get(), taskIds.size());
        if (availableTokens <= 0) {
            return new BatchReleaseResult(0, 0, 0, 0.0, new ArrayList<>());
        }
        
        // 3. 扣减令牌
        if (!tryConsumeTokens(availableTokens)) {
            return new BatchReleaseResult(0, 0, 0, 0.0, new ArrayList<>());
        }
        
        // 4. 批量执行释放
        List<ReleaseResult> results = new ArrayList<>();
        long totalRtt = 0;
        int successCount = 0;
        int failureCount = 0;
        
        for (int i = 0; i < availableTokens; i++) {
            Long taskId = taskIds.get(i);
            long startTime = System.currentTimeMillis();
            
            try {
                ReleaseResult result = releaseFunction.apply(taskId);
                results.add(result);
                
                totalRtt += result.rtt;
                if (result.isSuccess) {
                    successCount++;
                    onSuccess();
                } else {
                    failureCount++;
                    onFailure();
                }
                
            } catch (Exception e) {
                ReleaseResult errorResult = new ReleaseResult(false, 
                    "Exception: " + e.getMessage(), System.currentTimeMillis() - startTime);
                results.add(errorResult);
                failureCount++;
                onFailure();
            }
        }
        
        double avgRtt = availableTokens > 0 ? (double) totalRtt / availableTokens : 0.0;
        
        return new BatchReleaseResult(availableTokens, successCount, failureCount, avgRtt, results);
    }
    
    // ==================== 内部辅助方法 ====================
    
    /**
     * 更新系统指标
     */
    private void updateSystemMetrics(TrafficStats stats) {
        if (updateCount == 0) {
            avgSystemLoad = stats.systemLoad;
        } else {
            avgSystemLoad = SMOOTHING_FACTOR * avgSystemLoad + (1 - SMOOTHING_FACTOR) * stats.systemLoad;
        }
        updateCount++;
    }
    
    /**
     * 调整拥塞窗口
     */
    private void adjustCongestionWindow(TrafficStats stats) {
        double healthScore = calculateHealthScore(stats);
        
        if (healthScore > 0.8) {
            // 系统健康，可以增加窗口
            switch (currentState) {
                case SLOW_START:
                    slowStart();
                    break;
                case CONGESTION_AVOIDANCE:
                    congestionAvoidance();
                    break;
                case FAST_RECOVERY:
                    fastRecovery();
                    break;
            }
        } else if (healthScore < 0.5) {
            // 系统拥塞，减小窗口
            onCongestion(stats);
        } else {
            // 使用Vegas算法精细调整
            vegasAlgorithm(stats);
        }
    }
    
    /**
     * 计算系统健康度
     */
    private double calculateHealthScore(TrafficStats stats) {
        double loadScore = Math.max(0, (100.0 - stats.systemLoad) / 100.0);
        double backlogScore = stats.backlogCount == 0 ? 1.0 : 
            Math.max(0, 1.0 - stats.backlogCount / 1000.0);
        double rateScore = 1.0; // 简化处理
        
        return 0.5 * loadScore + 0.3 * backlogScore + 0.2 * rateScore;
    }
    
    /**
     * 慢启动算法
     */
    private void slowStart() {
        int currentCwnd = congestionWindow.get();
        int ssthresh = slowStartThreshold.get();
        
        if (currentCwnd < ssthresh) {
            int newCwnd = Math.min(currentCwnd * 2, config.maxCongestionWindow);
            congestionWindow.set(newCwnd);
        } else {
            currentState = CongestionState.CONGESTION_AVOIDANCE;
        }
    }
    
    /**
     * 拥塞避免算法
     */
    private void congestionAvoidance() {
        int currentCwnd = congestionWindow.get();
        if (consecutiveSuccesses >= currentCwnd) {
            int newCwnd = Math.min(currentCwnd + 1, config.maxCongestionWindow);
            congestionWindow.set(newCwnd);
            consecutiveSuccesses = 0;
        }
    }
    
    /**
     * 快速恢复算法
     */
    private void fastRecovery() {
        if (consecutiveSuccesses > slowStartThreshold.get()) {
            currentState = CongestionState.CONGESTION_AVOIDANCE;
            congestionWindow.set(slowStartThreshold.get());
        }
    }
    
    /**
     * Vegas算法
     */
    private void vegasAlgorithm(TrafficStats stats) {
        long baseRttValue = baseRtt.get();
        long currentRttValue = currentRtt.get();
        int currentCwnd = congestionWindow.get();
        
        double expectedThroughput = (double) currentCwnd / baseRttValue;
        double actualThroughput = (double) currentCwnd / currentRttValue;
        double queueLength = (expectedThroughput - actualThroughput) * currentRttValue;
        
        if (queueLength < config.vegasAlpha) {
            congestionWindow.set(Math.min(currentCwnd + 1, config.maxCongestionWindow));
        } else if (queueLength > config.vegasBeta) {
            congestionWindow.set(Math.max(currentCwnd - 1, 1));
        }
    }
    
    /**
     * 处理拥塞
     */
    private void onCongestion(TrafficStats stats) {
        int currentCwnd = congestionWindow.get();
        slowStartThreshold.set(Math.max(currentCwnd / 2, 1));
        
        double severityFactor = calculateCongestionSeverity(stats);
        int newCwnd = Math.max((int)(currentCwnd * (1.0 - severityFactor)), 1);
        
        congestionWindow.set(newCwnd);
        currentState = CongestionState.CONGESTION_AVOIDANCE;
    }
    
    /**
     * 计算拥塞严重程度
     */
    private double calculateCongestionSeverity(TrafficStats stats) {
        double severity = 0.0;
        
        if (stats.systemLoad > 90) severity += 0.8;
        else if (stats.systemLoad > 80) severity += 0.5;
        else if (stats.systemLoad > 70) severity += 0.3;
        
        if (stats.backlogCount > 500) severity += 0.6;
        else if (stats.backlogCount > 100) severity += 0.3;
        
        return Math.min(severity, 1.0);
    }
    
    /**
     * 计算目标QPS
     */
    private long calculateTargetQps(TrafficStats stats) {
        int currentCwnd = congestionWindow.get();
        long rtt = Math.max(currentRtt.get(), 1);
        
        double baseQps = (double) currentCwnd * 1000.0 / rtt;
        double loadFactor = calculateLoadFactor(stats);
        
        long targetQps = Math.round(baseQps * loadFactor);
        return Math.min(targetQps, config.maxQps);
    }
    
    /**
     * 计算负载因子
     */
    private double calculateLoadFactor(TrafficStats stats) {
        if (stats.systemLoad >= config.criticalLoadThreshold) {
            return config.emergencyAdjustFactor;
        } else if (stats.systemLoad >= config.highLoadThreshold) {
            return config.loadAdjustFactor;
        } else if (stats.systemLoad <= config.lowLoadThreshold) {
            return config.recoveryAdjustFactor;
        } else {
            double range = config.highLoadThreshold - config.lowLoadThreshold;
            double position = (stats.systemLoad - config.lowLoadThreshold) / range;
            return 1.0 + (config.recoveryAdjustFactor - 1.0) * (1.0 - position);
        }
    }
    
    /**
     * 调整令牌桶参数
     */
    private void adjustTokenBucketParameters(long targetQps, TrafficStats stats) {
        // 更新令牌生成速率
        tokenGenerationRate.set(targetQps);
        
        // 调整桶容量
        int baseCapacity = (int)(congestionWindow.get() * config.burstFactor);
        double adjustmentFactor = 1.0;
        
        if (stats.systemLoad > config.criticalLoadThreshold) {
            adjustmentFactor = 0.5;
        } else if (stats.systemLoad > config.highLoadThreshold) {
            adjustmentFactor = 0.8;
        } else if (stats.systemLoad < config.lowLoadThreshold) {
            adjustmentFactor = 1.3;
        }
        
        int newCapacity = (int)(baseCapacity * adjustmentFactor);
        newCapacity = Math.max(config.minBucketCapacity, Math.min(config.maxBucketCapacity, newCapacity));
        
        bucketCapacity.set(newCapacity);
        
        // 如果容量减少，调整当前令牌数
        if (currentTokens.get() > newCapacity) {
            currentTokens.set(newCapacity);
        }
    }
    
    /**
     * 补充令牌
     */
    private void refillTokens() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRefill = currentTime - lastRefillTime;
        
        if (timeSinceLastRefill < 10) return; // 最小间隔10ms
        
        if (refillLock.tryLock()) {
            try {
                timeSinceLastRefill = currentTime - lastRefillTime;
                if (timeSinceLastRefill < 10) return;
                
                long rate = tokenGenerationRate.get();
                long tokensToAdd = (rate * timeSinceLastRefill) / 1000;
                
                if (tokensToAdd > 0) {
                    int capacity = bucketCapacity.get();
                    int currentCount = currentTokens.get();
                    int newTokenCount = Math.min(capacity, (int)(currentCount + tokensToAdd));
                    
                    currentTokens.set(newTokenCount);
                    lastRefillTime = currentTime;
                }
            } finally {
                refillLock.unlock();
            }
        }
    }
    
    /**
     * 尝试消费令牌
     */
    private boolean tryConsumeTokens(int requestTokens) {
        int currentCount = currentTokens.get();
        if (currentCount >= requestTokens) {
            int newCount = currentTokens.addAndGet(-requestTokens);
            if (newCount >= 0) {
                return true;
            } else {
                // 回滚
                currentTokens.addAndGet(requestTokens);
                return false;
            }
        }
        return false;
    }
    
    /**
     * 处理成功
     */
    private void onSuccess() {
        consecutiveSuccesses++;
        consecutiveFailures = 0;
    }
    
    /**
     * 处理失败
     */
    private void onFailure() {
        consecutiveFailures++;
        consecutiveSuccesses = 0;
    }
    
    /**
     * 生成调整原因
     */
    private String generateAdjustReason(TrafficStats stats) {
        if (stats.systemLoad >= config.criticalLoadThreshold) {
            return "Critical load detected, emergency protection";
        } else if (stats.systemLoad >= config.highLoadThreshold) {
            return "High load, reducing QPS";
        } else if (stats.systemLoad <= config.lowLoadThreshold) {
            return "Low load, increasing QPS";
        } else if (stats.backlogCount > 100) {
            return "High backlog, throttling";
        } else {
            return "Normal adjustment based on " + currentState;
        }
    }
    
    // ==================== 状态查询方法 ====================
    
    /**
     * 获取当前系统状态
     */
    public String getSystemStatus() {
        return String.format("CongestionWindow=%d, State=%s, Tokens=%d/%d, Rate=%d/s, AvgLoad=%.1f%%",
            congestionWindow.get(), currentState, currentTokens.get(), bucketCapacity.get(),
            tokenGenerationRate.get(), avgSystemLoad);
    }
}