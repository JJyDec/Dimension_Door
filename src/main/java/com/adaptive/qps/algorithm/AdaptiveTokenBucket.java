package com.adaptive.qps.algorithm;

import com.adaptive.qps.config.AdaptiveQpsConfig;
import com.adaptive.qps.TrafficStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 自适应令牌桶算法实现
 * 支持动态调整令牌生成速率和桶容量，平滑突发流量
 * 
 * @author AI Assistant
 */
public class AdaptiveTokenBucket {
    
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveTokenBucket.class);
    
    private final AdaptiveQpsConfig config;
    
    // 令牌桶参数
    private final AtomicInteger bucketCapacity = new AtomicInteger(100);
    private final AtomicInteger currentTokens = new AtomicInteger(0);
    private final AtomicLong tokenGenerationRate = new AtomicLong(10); // tokens per second
    
    // 时间控制
    private volatile long lastRefillTime = System.currentTimeMillis();
    private final ReentrantLock refillLock = new ReentrantLock();
    
    // 自适应参数
    private final AtomicLong lastUpdateTime = new AtomicLong(System.currentTimeMillis());
    private volatile double burstFactor = 2.0;
    private volatile double adaptiveFactor = 1.0;
    
    // 统计信息
    private volatile long totalTokensGenerated = 0;
    private volatile long totalTokensConsumed = 0;
    private volatile long rejectedRequests = 0;
    
    public AdaptiveTokenBucket(AdaptiveQpsConfig config) {
        this.config = config;
        this.bucketCapacity.set(config.getMinBucketCapacity());
        this.currentTokens.set(config.getMinBucketCapacity());
        this.tokenGenerationRate.set(config.getInitialTokenRate());
        this.burstFactor = config.getBurstFactor();
        
        logger.info("AdaptiveTokenBucket initialized with capacity={}, rate={}/s, burst_factor={}", 
            config.getMinBucketCapacity(), config.getInitialTokenRate(), burstFactor);
    }
    
    /**
     * 尝试获取指定数量的令牌
     * 
     * @param requestTokens 请求的令牌数量
     * @return 实际获得的令牌数量
     */
    public int tryAcquire(int requestTokens) {
        // 首先补充令牌
        refillTokens();
        
        if (requestTokens <= 0) {
            return 0;
        }
        
        int currentTokenCount = currentTokens.get();
        int tokensToGrant = Math.min(requestTokens, currentTokenCount);
        
        if (tokensToGrant > 0) {
            // 原子性地减少令牌
            int newTokenCount = currentTokens.addAndGet(-tokensToGrant);
            if (newTokenCount < 0) {
                // 并发情况下可能出现负数，需要回滚
                currentTokens.addAndGet(tokensToGrant);
                return 0;
            }
            
            totalTokensConsumed += tokensToGrant;
            
            logger.debug("Granted {} tokens, remaining: {}", tokensToGrant, newTokenCount);
            return tokensToGrant;
        } else {
            rejectedRequests++;
            logger.debug("No tokens available, rejected request for {} tokens", requestTokens);
            return 0;
        }
    }
    
    /**
     * 设置令牌生成速率
     * 
     * @param rate 新的令牌生成速率（每秒）
     */
    public void setTokenGenerationRate(long rate) {
        long oldRate = tokenGenerationRate.getAndSet(Math.max(1, rate));
        
        if (oldRate != rate) {
            logger.debug("Token generation rate changed: {} -> {} tokens/s", oldRate, rate);
        }
    }
    
    /**
     * 根据系统状态和拥塞窗口更新桶参数
     * 
     * @param stats B系统状态
     * @param congestionWindow 当前拥塞窗口大小
     */
    public void updateBucketParameters(TrafficStats stats, int congestionWindow) {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastUpdate = currentTime - lastUpdateTime.get();
        
        // 至少间隔一定时间才更新参数，避免频繁调整
        if (timeSinceLastUpdate < config.getFeedbackIntervalMs()) {
            return;
        }
        
        // 计算自适应因子
        adaptiveFactor = calculateAdaptiveFactor(stats);
        
        // 更新桶容量
        updateBucketCapacity(stats, congestionWindow);
        
        // 更新突发因子
        updateBurstFactor(stats);
        
        lastUpdateTime.set(currentTime);
        
        logger.debug("Updated bucket parameters: capacity={}, adaptive_factor={:.2f}, burst_factor={:.2f}", 
            bucketCapacity.get(), adaptiveFactor, burstFactor);
    }
    
    /**
     * 补充令牌
     */
    private void refillTokens() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRefill = currentTime - lastRefillTime;
        
        // 如果时间间隔太短，不需要补充
        if (timeSinceLastRefill < 10) { // 10ms
            return;
        }
        
        if (refillLock.tryLock()) {
            try {
                // 双重检查
                timeSinceLastRefill = currentTime - lastRefillTime;
                if (timeSinceLastRefill < 10) {
                    return;
                }
                
                // 计算需要添加的令牌数量
                long rate = tokenGenerationRate.get();
                long tokensToAdd = (rate * timeSinceLastRefill * (long)(adaptiveFactor * 1000)) / (1000 * 1000);
                
                if (tokensToAdd > 0) {
                    int capacity = bucketCapacity.get();
                    int currentCount = currentTokens.get();
                    int newTokenCount = Math.min(capacity, (int)(currentCount + tokensToAdd));
                    
                    currentTokens.set(newTokenCount);
                    totalTokensGenerated += (newTokenCount - currentCount);
                    lastRefillTime = currentTime;
                    
                    logger.debug("Refilled {} tokens, current: {}/{}", 
                        (newTokenCount - currentCount), newTokenCount, capacity);
                }
                
            } finally {
                refillLock.unlock();
            }
        }
    }
    
    /**
     * 计算自适应因子
     */
    private double calculateAdaptiveFactor(TrafficStats stats) {
        double factor = 1.0;
        
        // 基于系统负载调整
        if (stats.systemLoad < config.getLowLoadThreshold()) {
            factor *= 1.2; // 低负载时提高生成速率
        } else if (stats.systemLoad > config.getHighLoadThreshold()) {
            factor *= 0.8; // 高负载时降低生成速率
        }
        
        // 基于积压队列调整
        if (stats.backlogCount > 100) {
            factor *= 0.9;
        } else if (stats.backlogCount == 0) {
            factor *= 1.1;
        }
        
        // 基于当前速率与限制速率的比值
        if (stats.limitRate > 0) {
            double rateRatio = stats.currentRate / stats.limitRate;
            if (rateRatio < 0.7) {
                factor *= 1.1; // 当前速率较低，可以提高
            } else if (rateRatio > 0.9) {
                factor *= 0.9; // 当前速率接近限制，需要降低
            }
        }
        
        // 限制因子范围
        return Math.max(0.1, Math.min(3.0, factor));
    }
    
    /**
     * 更新桶容量
     */
    private void updateBucketCapacity(TrafficStats stats, int congestionWindow) {
        // 基础容量基于拥塞窗口
        int baseCapacity = (int)(congestionWindow * burstFactor);
        
        // 根据系统状态调整
        double adjustmentFactor = 1.0;
        
        if (stats.systemLoad > config.getCriticalLoadThreshold()) {
            adjustmentFactor = 0.5; // 严重负载时大幅减少容量
        } else if (stats.systemLoad > config.getHighLoadThreshold()) {
            adjustmentFactor = 0.8; // 高负载时减少容量
        } else if (stats.systemLoad < config.getLowLoadThreshold()) {
            adjustmentFactor = 1.3; // 低负载时增加容量
        }
        
        int newCapacity = (int)(baseCapacity * adjustmentFactor);
        
        // 应用限制
        newCapacity = Math.max(config.getMinBucketCapacity(), 
                      Math.min(config.getMaxBucketCapacity(), newCapacity));
        
        int oldCapacity = bucketCapacity.getAndSet(newCapacity);
        
        // 如果容量减少，需要相应调整当前令牌数
        if (newCapacity < oldCapacity) {
            int currentCount = currentTokens.get();
            if (currentCount > newCapacity) {
                currentTokens.set(newCapacity);
            }
        }
    }
    
    /**
     * 更新突发因子
     */
    private void updateBurstFactor(TrafficStats stats) {
        // 基于历史统计调整突发因子
        double utilizationRate = totalTokensConsumed > 0 ? 
            (double)rejectedRequests / (totalTokensConsumed + rejectedRequests) : 0.0;
        
        if (utilizationRate > 0.1) {
            // 拒绝率较高，增加突发因子
            burstFactor = Math.min(5.0, burstFactor * 1.1);
        } else if (utilizationRate < 0.01) {
            // 拒绝率很低，可以减少突发因子
            burstFactor = Math.max(1.5, burstFactor * 0.95);
        }
        
        // 基于系统负载微调
        if (stats.systemLoad > config.getHighLoadThreshold()) {
            burstFactor = Math.max(1.0, burstFactor * 0.9);
        }
    }
    
    /**
     * 获取当前令牌数量
     */
    public int getCurrentLevel() {
        refillTokens(); // 确保获取最新状态
        return currentTokens.get();
    }
    
    /**
     * 获取桶容量
     */
    public int getCapacity() {
        return bucketCapacity.get();
    }
    
    /**
     * 获取当前令牌生成速率
     */
    public long getTokenGenerationRate() {
        return tokenGenerationRate.get();
    }
    
    /**
     * 获取突发因子
     */
    public double getBurstFactor() {
        return burstFactor;
    }
    
    /**
     * 获取自适应因子
     */
    public double getAdaptiveFactor() {
        return adaptiveFactor;
    }
    
    /**
     * 获取统计信息
     */
    public TokenBucketStats getStats() {
        return new TokenBucketStats(
            totalTokensGenerated,
            totalTokensConsumed,
            rejectedRequests,
            getCurrentLevel(),
            getCapacity(),
            getTokenGenerationRate(),
            adaptiveFactor,
            burstFactor
        );
    }
    
    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalTokensGenerated = 0;
        totalTokensConsumed = 0;
        rejectedRequests = 0;
        
        logger.info("Token bucket statistics reset");
    }
    
    /**
     * 令牌桶统计信息
     */
    public static class TokenBucketStats {
        private final long totalGenerated;
        private final long totalConsumed;
        private final long totalRejected;
        private final int currentLevel;
        private final int capacity;
        private final long generationRate;
        private final double adaptiveFactor;
        private final double burstFactor;
        
        public TokenBucketStats(long totalGenerated, long totalConsumed, long totalRejected,
                               int currentLevel, int capacity, long generationRate,
                               double adaptiveFactor, double burstFactor) {
            this.totalGenerated = totalGenerated;
            this.totalConsumed = totalConsumed;
            this.totalRejected = totalRejected;
            this.currentLevel = currentLevel;
            this.capacity = capacity;
            this.generationRate = generationRate;
            this.adaptiveFactor = adaptiveFactor;
            this.burstFactor = burstFactor;
        }
        
        public long getTotalGenerated() { return totalGenerated; }
        public long getTotalConsumed() { return totalConsumed; }
        public long getTotalRejected() { return totalRejected; }
        public int getCurrentLevel() { return currentLevel; }
        public int getCapacity() { return capacity; }
        public long getGenerationRate() { return generationRate; }
        public double getAdaptiveFactor() { return adaptiveFactor; }
        public double getBurstFactor() { return burstFactor; }
        
        public double getUtilizationRate() {
            return totalConsumed + totalRejected > 0 ? 
                (double)totalConsumed / (totalConsumed + totalRejected) : 0.0;
        }
        
        public double getRejectionRate() {
            return totalConsumed + totalRejected > 0 ? 
                (double)totalRejected / (totalConsumed + totalRejected) : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("TokenBucketStats{level=%d/%d, rate=%d/s, generated=%d, consumed=%d, rejected=%d, util=%.2f%%, reject=%.2f%%, adaptive=%.2f, burst=%.2f}",
                currentLevel, capacity, generationRate, totalGenerated, totalConsumed, totalRejected,
                getUtilizationRate() * 100, getRejectionRate() * 100, adaptiveFactor, burstFactor);
        }
    }
}