package com.adaptive.qps.metrics;

import com.adaptive.qps.TrafficStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 系统指标管理器
 * 收集、管理和分析B系统的性能指标
 * 
 * @author AI Assistant
 */
public class SystemMetrics {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemMetrics.class);
    
    // 当前指标
    private final AtomicInteger systemLoad = new AtomicInteger(0);
    private final AtomicInteger backlogCount = new AtomicInteger(0);
    private final AtomicLong currentRate = new AtomicLong(0);
    private final AtomicLong limitRate = new AtomicLong(0);
    
    // 历史指标（用于趋势分析）
    private volatile double avgSystemLoad = 0.0;
    private volatile double avgBacklogCount = 0.0;
    private volatile double avgCurrentRate = 0.0;
    
    // 统计计数器
    private volatile long updateCount = 0;
    private volatile long lastUpdateTime = System.currentTimeMillis();
    
    // 平滑系数
    private static final double SMOOTHING_FACTOR = 0.8;
    
    /**
     * 更新系统指标
     * 
     * @param stats B系统返回的统计信息
     */
    public void updateStats(TrafficStats stats) {
        if (stats == null) {
            return;
        }
        
        // 更新当前值
        systemLoad.set(stats.systemLoad);
        backlogCount.set(stats.backlogCount);
        currentRate.set((long) stats.currentRate);
        
        // 更新历史平均值（指数平滑）
        if (updateCount == 0) {
            avgSystemLoad = stats.systemLoad;
            avgBacklogCount = stats.backlogCount;
            avgCurrentRate = stats.currentRate;
        } else {
            avgSystemLoad = SMOOTHING_FACTOR * avgSystemLoad + (1 - SMOOTHING_FACTOR) * stats.systemLoad;
            avgBacklogCount = SMOOTHING_FACTOR * avgBacklogCount + (1 - SMOOTHING_FACTOR) * stats.backlogCount;
            avgCurrentRate = SMOOTHING_FACTOR * avgCurrentRate + (1 - SMOOTHING_FACTOR) * stats.currentRate;
        }
        
        updateCount++;
        lastUpdateTime = System.currentTimeMillis();
        
        logger.debug("System metrics updated: load={}%, backlog={}, rate={:.2f}/s, avg_load={:.1f}%", 
            stats.systemLoad, stats.backlogCount, stats.currentRate, avgSystemLoad);
    }
    
    /**
     * 获取当前系统负载
     */
    public int getSystemLoad() {
        return systemLoad.get();
    }
    
    /**
     * 获取当前积压数量
     */
    public int getBacklogCount() {
        return backlogCount.get();
    }
    
    /**
     * 获取当前处理速率
     */
    public long getCurrentRate() {
        return currentRate.get();
    }
    
    /**
     * 获取限制速率
     */
    public long getLimitRate() {
        return limitRate.get();
    }
    
    /**
     * 获取平均系统负载
     */
    public double getAvgSystemLoad() {
        return avgSystemLoad;
    }
    
    /**
     * 获取平均积压数量
     */
    public double getAvgBacklogCount() {
        return avgBacklogCount;
    }
    
    /**
     * 获取平均处理速率
     */
    public double getAvgCurrentRate() {
        return avgCurrentRate;
    }
    
    /**
     * 获取系统健康评分
     * 
     * @return 0.0-1.0之间的评分，1.0为最健康
     */
    public double getHealthScore() {
        // 基于负载的评分
        double loadScore = Math.max(0, (100.0 - systemLoad.get()) / 100.0);
        
        // 基于积压的评分
        double backlogScore = backlogCount.get() == 0 ? 1.0 : 
            Math.max(0, 1.0 - backlogCount.get() / 1000.0);
        
        // 基于处理速率的评分（假设理想速率为限制速率的80%）
        double rateScore = 1.0;
        if (limitRate.get() > 0) {
            double idealRate = limitRate.get() * 0.8;
            double actualRate = currentRate.get();
            rateScore = actualRate <= idealRate ? 1.0 : Math.max(0, 2.0 - actualRate / idealRate);
        }
        
        // 综合评分
        return 0.5 * loadScore + 0.3 * backlogScore + 0.2 * rateScore;
    }
    
    /**
     * 获取系统压力级别
     * 
     * @return 0=轻松, 1=正常, 2=繁忙, 3=高压, 4=过载
     */
    public int getPressureLevel() {
        int load = systemLoad.get();
        int backlog = backlogCount.get();
        
        if (load >= 95 || backlog >= 1000) {
            return 4; // 过载
        } else if (load >= 85 || backlog >= 500) {
            return 3; // 高压
        } else if (load >= 70 || backlog >= 100) {
            return 2; // 繁忙
        } else if (load >= 50 || backlog >= 10) {
            return 1; // 正常
        } else {
            return 0; // 轻松
        }
    }
    
    /**
     * 检查系统是否处于拥塞状态
     */
    public boolean isCongested() {
        return getPressureLevel() >= 3;
    }
    
    /**
     * 获取负载趋势
     * 
     * @return 正数表示上升趋势，负数表示下降趋势，0表示稳定
     */
    public double getLoadTrend() {
        double currentLoad = systemLoad.get();
        return currentLoad - avgSystemLoad;
    }
    
    /**
     * 获取积压趋势
     */
    public double getBacklogTrend() {
        double currentBacklog = backlogCount.get();
        return currentBacklog - avgBacklogCount;
    }
    
    /**
     * 获取速率趋势
     */
    public double getRateTrend() {
        double currentRateValue = currentRate.get();
        return currentRateValue - avgCurrentRate;
    }
    
    /**
     * 检查指标是否过期
     * 
     * @param timeoutMs 超时时间（毫秒）
     * @return true表示指标已过期
     */
    public boolean isStale(long timeoutMs) {
        return System.currentTimeMillis() - lastUpdateTime > timeoutMs;
    }
    
    /**
     * 重置所有统计信息
     */
    public void reset() {
        systemLoad.set(0);
        backlogCount.set(0);
        currentRate.set(0);
        limitRate.set(0);
        
        avgSystemLoad = 0.0;
        avgBacklogCount = 0.0;
        avgCurrentRate = 0.0;
        
        updateCount = 0;
        lastUpdateTime = System.currentTimeMillis();
        
        logger.info("System metrics reset");
    }
    
    /**
     * 获取详细的指标快照
     */
    public MetricsSnapshot getSnapshot() {
        return new MetricsSnapshot(
            systemLoad.get(),
            backlogCount.get(),
            currentRate.get(),
            limitRate.get(),
            avgSystemLoad,
            avgBacklogCount,
            avgCurrentRate,
            getHealthScore(),
            getPressureLevel(),
            getLoadTrend(),
            getBacklogTrend(),
            getRateTrend(),
            updateCount,
            lastUpdateTime
        );
    }
    
    /**
     * 指标快照
     */
    public static class MetricsSnapshot {
        private final int systemLoad;
        private final int backlogCount;
        private final long currentRate;
        private final long limitRate;
        private final double avgSystemLoad;
        private final double avgBacklogCount;
        private final double avgCurrentRate;
        private final double healthScore;
        private final int pressureLevel;
        private final double loadTrend;
        private final double backlogTrend;
        private final double rateTrend;
        private final long updateCount;
        private final long lastUpdateTime;
        
        public MetricsSnapshot(int systemLoad, int backlogCount, long currentRate, long limitRate,
                              double avgSystemLoad, double avgBacklogCount, double avgCurrentRate,
                              double healthScore, int pressureLevel, double loadTrend,
                              double backlogTrend, double rateTrend, long updateCount, long lastUpdateTime) {
            this.systemLoad = systemLoad;
            this.backlogCount = backlogCount;
            this.currentRate = currentRate;
            this.limitRate = limitRate;
            this.avgSystemLoad = avgSystemLoad;
            this.avgBacklogCount = avgBacklogCount;
            this.avgCurrentRate = avgCurrentRate;
            this.healthScore = healthScore;
            this.pressureLevel = pressureLevel;
            this.loadTrend = loadTrend;
            this.backlogTrend = backlogTrend;
            this.rateTrend = rateTrend;
            this.updateCount = updateCount;
            this.lastUpdateTime = lastUpdateTime;
        }
        
        // Getters
        public int getSystemLoad() { return systemLoad; }
        public int getBacklogCount() { return backlogCount; }
        public long getCurrentRate() { return currentRate; }
        public long getLimitRate() { return limitRate; }
        public double getAvgSystemLoad() { return avgSystemLoad; }
        public double getAvgBacklogCount() { return avgBacklogCount; }
        public double getAvgCurrentRate() { return avgCurrentRate; }
        public double getHealthScore() { return healthScore; }
        public int getPressureLevel() { return pressureLevel; }
        public double getLoadTrend() { return loadTrend; }
        public double getBacklogTrend() { return backlogTrend; }
        public double getRateTrend() { return rateTrend; }
        public long getUpdateCount() { return updateCount; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        
        @Override
        public String toString() {
            return String.format("MetricsSnapshot{load=%d%%(%.1f), backlog=%d(%.1f), rate=%d/s(%.1f), " +
                "health=%.2f, pressure=%d, trends=[%.1f,%.1f,%.1f], updates=%d}",
                systemLoad, avgSystemLoad, backlogCount, avgBacklogCount, currentRate, avgCurrentRate,
                healthScore, pressureLevel, loadTrend, backlogTrend, rateTrend, updateCount);
        }
    }
}