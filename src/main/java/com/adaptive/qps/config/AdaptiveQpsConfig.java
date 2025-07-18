package com.adaptive.qps.config;

/**
 * 自适应QPS控制系统配置
 * 
 * @author AI Assistant
 */
public class AdaptiveQpsConfig {
    
    // TCP拥塞控制参数
    private int initialCwnd = 1;
    private int initialSsthresh = 65535;
    private int maxCongestionWindow = 1000;
    private int vegasAlpha = 2;
    private int vegasBeta = 4;
    
    // 令牌桶参数
    private int minBucketCapacity = 10;
    private int maxBucketCapacity = 1000;
    private long initialTokenRate = 10;
    private double burstFactor = 2.0;
    
    // 反馈控制参数
    private long feedbackIntervalMs = 100;
    private long adjustIntervalMs = 1000;
    private long releaseIntervalMs = 50;
    private int pageSize = 100;
    
    // 系统保护参数
    private long maxQps = 10000;
    private int maxRetryTimes = 3;
    private long timeoutThresholdMs = 3000;
    private double emergencyFallbackRate = 0.1;
    
    // 负载阈值
    private int highLoadThreshold = 80;
    private int lowLoadThreshold = 30;
    private int criticalLoadThreshold = 95;
    
    // 调整策略
    private double loadAdjustFactor = 0.8;
    private double recoveryAdjustFactor = 1.1;
    private double emergencyAdjustFactor = 0.3;
    
    // 预测参数
    private double predictionAlpha = 0.7;
    private double[] healthWeight = {0.4, 0.3, 0.3};
    
    // 构造函数
    public AdaptiveQpsConfig() {
        // 使用默认值
    }
    
    public static AdaptiveQpsConfig defaultConfig() {
        return new AdaptiveQpsConfig();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public int getInitialCwnd() { return initialCwnd; }
    public int getInitialSsthresh() { return initialSsthresh; }
    public int getMaxCongestionWindow() { return maxCongestionWindow; }
    public int getVegasAlpha() { return vegasAlpha; }
    public int getVegasBeta() { return vegasBeta; }
    
    public int getMinBucketCapacity() { return minBucketCapacity; }
    public int getMaxBucketCapacity() { return maxBucketCapacity; }
    public long getInitialTokenRate() { return initialTokenRate; }
    public double getBurstFactor() { return burstFactor; }
    
    public long getFeedbackIntervalMs() { return feedbackIntervalMs; }
    public long getAdjustIntervalMs() { return adjustIntervalMs; }
    public long getReleaseIntervalMs() { return releaseIntervalMs; }
    public int getPageSize() { return pageSize; }
    
    public long getMaxQps() { return maxQps; }
    public int getMaxRetryTimes() { return maxRetryTimes; }
    public long getTimeoutThresholdMs() { return timeoutThresholdMs; }
    public double getEmergencyFallbackRate() { return emergencyFallbackRate; }
    
    public int getHighLoadThreshold() { return highLoadThreshold; }
    public int getLowLoadThreshold() { return lowLoadThreshold; }
    public int getCriticalLoadThreshold() { return criticalLoadThreshold; }
    
    public double getLoadAdjustFactor() { return loadAdjustFactor; }
    public double getRecoveryAdjustFactor() { return recoveryAdjustFactor; }
    public double getEmergencyAdjustFactor() { return emergencyAdjustFactor; }
    
    public double getPredictionAlpha() { return predictionAlpha; }
    public double[] getHealthWeight() { return healthWeight.clone(); }
    
    @Override
    public String toString() {
        return String.format("AdaptiveQpsConfig{cwnd=%d, ssthresh=%d, bucket=%d-%d, rate=%d/s, " +
            "feedback=%dms, load_threshold=%d/%d/%d, adjust_factor=%.2f/%.2f/%.2f}",
            initialCwnd, initialSsthresh, minBucketCapacity, maxBucketCapacity, initialTokenRate,
            feedbackIntervalMs, lowLoadThreshold, highLoadThreshold, criticalLoadThreshold,
            loadAdjustFactor, recoveryAdjustFactor, emergencyAdjustFactor);
    }
    
    /**
     * 配置构建器
     */
    public static class Builder {
        private AdaptiveQpsConfig config = new AdaptiveQpsConfig();
        
        // TCP拥塞控制参数设置
        public Builder initialCwnd(int initialCwnd) {
            config.initialCwnd = Math.max(1, initialCwnd);
            return this;
        }
        
        public Builder initialSsthresh(int initialSsthresh) {
            config.initialSsthresh = Math.max(1, initialSsthresh);
            return this;
        }
        
        public Builder maxCongestionWindow(int maxCongestionWindow) {
            config.maxCongestionWindow = Math.max(10, maxCongestionWindow);
            return this;
        }
        
        public Builder vegasParams(int alpha, int beta) {
            config.vegasAlpha = Math.max(1, alpha);
            config.vegasBeta = Math.max(alpha + 1, beta);
            return this;
        }
        
        // 令牌桶参数设置
        public Builder bucketCapacity(int min, int max) {
            config.minBucketCapacity = Math.max(1, min);
            config.maxBucketCapacity = Math.max(min, max);
            return this;
        }
        
        public Builder initialTokenRate(long rate) {
            config.initialTokenRate = Math.max(1, rate);
            return this;
        }
        
        public Builder burstFactor(double factor) {
            config.burstFactor = Math.max(1.0, factor);
            return this;
        }
        
        // 反馈控制参数设置
        public Builder feedbackInterval(long intervalMs) {
            config.feedbackIntervalMs = Math.max(10, intervalMs);
            return this;
        }
        
        public Builder adjustInterval(long intervalMs) {
            config.adjustIntervalMs = Math.max(100, intervalMs);
            return this;
        }
        
        public Builder releaseInterval(long intervalMs) {
            config.releaseIntervalMs = Math.max(10, intervalMs);
            return this;
        }
        
        public Builder pageSize(int size) {
            config.pageSize = Math.max(1, size);
            return this;
        }
        
        // 系统保护参数设置
        public Builder maxQps(long maxQps) {
            config.maxQps = Math.max(1, maxQps);
            return this;
        }
        
        public Builder maxRetryTimes(int times) {
            config.maxRetryTimes = Math.max(0, times);
            return this;
        }
        
        public Builder timeoutThreshold(long timeoutMs) {
            config.timeoutThresholdMs = Math.max(100, timeoutMs);
            return this;
        }
        
        public Builder emergencyFallbackRate(double rate) {
            config.emergencyFallbackRate = Math.max(0.01, Math.min(1.0, rate));
            return this;
        }
        
        // 负载阈值设置
        public Builder loadThresholds(int low, int high, int critical) {
            config.lowLoadThreshold = Math.max(0, Math.min(100, low));
            config.highLoadThreshold = Math.max(low, Math.min(100, high));
            config.criticalLoadThreshold = Math.max(high, Math.min(100, critical));
            return this;
        }
        
        // 调整策略设置
        public Builder adjustFactors(double load, double recovery, double emergency) {
            config.loadAdjustFactor = Math.max(0.1, Math.min(1.0, load));
            config.recoveryAdjustFactor = Math.max(1.0, Math.min(5.0, recovery));
            config.emergencyAdjustFactor = Math.max(0.01, Math.min(1.0, emergency));
            return this;
        }
        
        // 预测参数设置
        public Builder predictionAlpha(double alpha) {
            config.predictionAlpha = Math.max(0.1, Math.min(1.0, alpha));
            return this;
        }
        
        public Builder healthWeight(double loadWeight, double backlogWeight, double rateWeight) {
            double total = loadWeight + backlogWeight + rateWeight;
            if (total > 0) {
                config.healthWeight[0] = loadWeight / total;
                config.healthWeight[1] = backlogWeight / total;
                config.healthWeight[2] = rateWeight / total;
            }
            return this;
        }
        
        public AdaptiveQpsConfig build() {
            // 验证配置合理性
            validate();
            return config;
        }
        
        private void validate() {
            if (config.initialCwnd > config.maxCongestionWindow) {
                throw new IllegalArgumentException("Initial congestion window cannot be larger than max window");
            }
            
            if (config.vegasAlpha >= config.vegasBeta) {
                throw new IllegalArgumentException("Vegas alpha must be less than beta");
            }
            
            if (config.minBucketCapacity > config.maxBucketCapacity) {
                throw new IllegalArgumentException("Min bucket capacity cannot be larger than max capacity");
            }
            
            if (config.lowLoadThreshold >= config.highLoadThreshold || 
                config.highLoadThreshold >= config.criticalLoadThreshold) {
                throw new IllegalArgumentException("Load thresholds must be in ascending order");
            }
            
            if (config.adjustIntervalMs < config.feedbackIntervalMs) {
                throw new IllegalArgumentException("Adjust interval should not be less than feedback interval");
            }
        }
    }
}