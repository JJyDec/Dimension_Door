package com.adaptive.qps.algorithm;

import com.adaptive.qps.config.AdaptiveQpsConfig;
import com.adaptive.qps.TrafficStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TCP拥塞控制算法实现
 * 融合TCP Reno和TCP Vegas算法，支持慢启动、拥塞避免、快速恢复
 * 
 * @author AI Assistant
 */
public class TcpCongestionController {
    
    private static final Logger logger = LoggerFactory.getLogger(TcpCongestionController.class);
    
    /**
     * 拥塞控制状态
     */
    public enum CongestionState {
        SLOW_START,      // 慢启动
        CONGESTION_AVOIDANCE,  // 拥塞避免
        FAST_RECOVERY    // 快速恢复
    }
    
    private final AdaptiveQpsConfig config;
    
    // 拥塞窗口相关参数
    private final AtomicInteger congestionWindow = new AtomicInteger(1);
    private final AtomicInteger slowStartThreshold = new AtomicInteger(65535);
    private volatile CongestionState currentState = CongestionState.SLOW_START;
    
    // RTT相关参数
    private final AtomicLong baseRtt = new AtomicLong(100); // 基础RTT，初始值100ms
    private final AtomicLong currentRtt = new AtomicLong(100);
    private final AtomicLong smoothedRtt = new AtomicLong(100);
    private final AtomicLong rttVariation = new AtomicLong(50);
    
    // Vegas算法参数
    private final int alpha;
    private final int beta;
    
    // 统计信息
    private volatile int duplicateAckCount = 0;
    private volatile long lastCongestionTime = 0;
    private volatile int consecutiveSuccesses = 0;
    private volatile int consecutiveFailures = 0;
    
    public TcpCongestionController(AdaptiveQpsConfig config) {
        this.config = config;
        this.alpha = config.getVegasAlpha();
        this.beta = config.getVegasBeta();
        
        this.congestionWindow.set(config.getInitialCwnd());
        this.slowStartThreshold.set(config.getInitialSsthresh());
        
        logger.info("TcpCongestionController initialized with cwnd={}, ssthresh={}", 
            config.getInitialCwnd(), config.getInitialSsthresh());
    }
    
    /**
     * 处理反馈信息
     */
    public void onFeedbackReceived(TrafficStats stats, long rtt) {
        updateRtt(rtt);
        
        // 计算系统健康度
        double healthScore = calculateHealthScore(stats);
        
        // 根据健康度调整拥塞窗口
        if (healthScore > 0.8) {
            onSuccess();
        } else if (healthScore < 0.5) {
            onCongestion(stats);
        } else {
            // 使用Vegas算法进行精细调整
            vegasAlgorithm(stats);
        }
        
        logger.debug("Feedback received: health={:.2f}, cwnd={}, state={}, rtt={}ms", 
            healthScore, congestionWindow.get(), currentState, currentRtt.get());
    }
    
    /**
     * 处理成功响应
     */
    public void onSuccess() {
        consecutiveSuccesses++;
        consecutiveFailures = 0;
        
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
    }
    
    /**
     * 处理失败响应
     */
    public void onFailure() {
        consecutiveFailures++;
        consecutiveSuccesses = 0;
        duplicateAckCount++;
        
        if (duplicateAckCount >= 3) {
            // 快速重传触发
            fastRetransmit();
        }
    }
    
    /**
     * 处理超时
     */
    public void onTimeout() {
        logger.warn("Timeout detected, entering congestion recovery");
        
        slowStartThreshold.set(Math.max(congestionWindow.get() / 2, 1));
        congestionWindow.set(1);
        currentState = CongestionState.SLOW_START;
        duplicateAckCount = 0;
        lastCongestionTime = System.currentTimeMillis();
    }
    
    /**
     * 慢启动算法
     */
    private void slowStart() {
        int currentCwnd = congestionWindow.get();
        int ssthresh = slowStartThreshold.get();
        
        if (currentCwnd < ssthresh) {
            // 指数增长：每个RTT窗口翻倍
            int newCwnd = Math.min(currentCwnd * 2, config.getMaxCongestionWindow());
            congestionWindow.set(newCwnd);
            
            logger.debug("Slow start: cwnd {} -> {}", currentCwnd, newCwnd);
        } else {
            // 达到阈值，切换到拥塞避免
            currentState = CongestionState.CONGESTION_AVOIDANCE;
            logger.debug("Switching to congestion avoidance, cwnd={}", currentCwnd);
        }
    }
    
    /**
     * 拥塞避免算法
     */
    private void congestionAvoidance() {
        int currentCwnd = congestionWindow.get();
        
        // 线性增长：每个RTT增加1/cwnd
        if (consecutiveSuccesses >= currentCwnd) {
            int newCwnd = Math.min(currentCwnd + 1, config.getMaxCongestionWindow());
            congestionWindow.set(newCwnd);
            consecutiveSuccesses = 0;
            
            logger.debug("Congestion avoidance: cwnd {} -> {}", currentCwnd, newCwnd);
        }
    }
    
    /**
     * 快速恢复算法
     */
    private void fastRecovery() {
        // 在快速恢复期间，每收到一个重复ACK，窗口增加1
        int currentCwnd = congestionWindow.get();
        int newCwnd = Math.min(currentCwnd + 1, config.getMaxCongestionWindow());
        congestionWindow.set(newCwnd);
        
        // 如果连续成功超过阈值，退出快速恢复
        if (consecutiveSuccesses > slowStartThreshold.get()) {
            currentState = CongestionState.CONGESTION_AVOIDANCE;
            congestionWindow.set(slowStartThreshold.get());
            duplicateAckCount = 0;
            
            logger.debug("Exit fast recovery, switching to congestion avoidance");
        }
    }
    
    /**
     * 快速重传
     */
    private void fastRetransmit() {
        logger.debug("Fast retransmit triggered, duplicate ACKs: {}", duplicateAckCount);
        
        slowStartThreshold.set(Math.max(congestionWindow.get() / 2, 1));
        congestionWindow.set(slowStartThreshold.get() + 3); // 3是重复ACK的数量
        currentState = CongestionState.FAST_RECOVERY;
        lastCongestionTime = System.currentTimeMillis();
    }
    
    /**
     * Vegas算法 - 基于延迟的拥塞检测
     */
    private void vegasAlgorithm(TrafficStats stats) {
        long baseRttValue = baseRtt.get();
        long currentRttValue = currentRtt.get();
        int currentCwnd = congestionWindow.get();
        
        // 计算期望吞吐量和实际吞吐量
        double expectedThroughput = (double) currentCwnd / baseRttValue;
        double actualThroughput = (double) currentCwnd / currentRttValue;
        
        // 计算队列积压长度
        double queueLength = (expectedThroughput - actualThroughput) * currentRttValue;
        
        if (queueLength < alpha) {
            // 队列较短，可以增加窗口
            if (currentState == CongestionState.CONGESTION_AVOIDANCE) {
                int newCwnd = Math.min(currentCwnd + 1, config.getMaxCongestionWindow());
                congestionWindow.set(newCwnd);
            }
        } else if (queueLength > beta) {
            // 队列较长，需要减小窗口
            int newCwnd = Math.max(currentCwnd - 1, 1);
            congestionWindow.set(newCwnd);
        }
        // 在alpha和beta之间时，保持当前窗口大小不变
        
        logger.debug("Vegas algorithm: queue_length={:.2f}, alpha={}, beta={}, cwnd={}", 
            queueLength, alpha, beta, currentCwnd);
    }
    
    /**
     * 处理拥塞
     */
    private void onCongestion(TrafficStats stats) {
        int currentCwnd = congestionWindow.get();
        
        // 设置慢启动阈值为当前窗口的一半
        slowStartThreshold.set(Math.max(currentCwnd / 2, 1));
        
        // 根据拥塞严重程度调整窗口
        double severityFactor = calculateCongestionSeverity(stats);
        int newCwnd = Math.max((int)(currentCwnd * (1.0 - severityFactor)), 1);
        
        congestionWindow.set(newCwnd);
        currentState = CongestionState.CONGESTION_AVOIDANCE;
        lastCongestionTime = System.currentTimeMillis();
        
        logger.warn("Congestion detected: severity={:.2f}, cwnd {} -> {}", 
            severityFactor, currentCwnd, newCwnd);
    }
    
    /**
     * 计算系统健康度
     */
    private double calculateHealthScore(TrafficStats stats) {
        // 基于系统负载
        double loadScore = Math.max(0, (100.0 - stats.systemLoad) / 100.0);
        
        // 基于积压队列
        double backlogScore = stats.backlogCount == 0 ? 1.0 : 
            Math.max(0, 1.0 - (double)stats.backlogCount / 1000.0);
        
        // 基于当前速率与限制速率的比值
        double rateScore = stats.limitRate == 0 ? 1.0 : 
            Math.min(1.0, stats.currentRate / stats.limitRate);
        
        // 综合评分
        return 0.4 * loadScore + 0.3 * backlogScore + 0.3 * rateScore;
    }
    
    /**
     * 计算拥塞严重程度
     */
    private double calculateCongestionSeverity(TrafficStats stats) {
        double severity = 0.0;
        
        // 基于系统负载
        if (stats.systemLoad > 90) {
            severity += 0.8;
        } else if (stats.systemLoad > 80) {
            severity += 0.5;
        } else if (stats.systemLoad > 70) {
            severity += 0.3;
        }
        
        // 基于积压队列
        if (stats.backlogCount > 1000) {
            severity += 0.6;
        } else if (stats.backlogCount > 500) {
            severity += 0.4;
        } else if (stats.backlogCount > 100) {
            severity += 0.2;
        }
        
        // 基于连续失败次数
        if (consecutiveFailures > 10) {
            severity += 0.7;
        } else if (consecutiveFailures > 5) {
            severity += 0.4;
        } else if (consecutiveFailures > 2) {
            severity += 0.2;
        }
        
        return Math.min(severity, 1.0);
    }
    
    /**
     * 更新RTT
     */
    private void updateRtt(long measuredRtt) {
        // 更新基础RTT（最小RTT）
        long currentBase = baseRtt.get();
        if (measuredRtt < currentBase) {
            baseRtt.set(measuredRtt);
        }
        
        // 更新当前RTT
        currentRtt.set(measuredRtt);
        
        // 更新平滑RTT (RFC 793)
        long currentSmoothed = smoothedRtt.get();
        long newSmoothed = (7 * currentSmoothed + measuredRtt) / 8;
        smoothedRtt.set(newSmoothed);
        
        // 更新RTT变化 (RFC 793)
        long currentVariation = rttVariation.get();
        long newVariation = (3 * currentVariation + Math.abs(measuredRtt - newSmoothed)) / 4;
        rttVariation.set(newVariation);
    }
    
    /**
     * 获取当前拥塞窗口大小
     */
    public int getCurrentWindow() {
        return congestionWindow.get();
    }
    
    /**
     * 获取当前RTT
     */
    public long getCurrentRtt() {
        return currentRtt.get();
    }
    
    /**
     * 获取平滑RTT
     */
    public long getSmoothedRtt() {
        return smoothedRtt.get();
    }
    
    /**
     * 获取基础RTT
     */
    public long getBaseRtt() {
        return baseRtt.get();
    }
    
    /**
     * 获取当前状态
     */
    public CongestionState getCurrentState() {
        return currentState;
    }
    
    /**
     * 获取慢启动阈值
     */
    public int getSlowStartThreshold() {
        return slowStartThreshold.get();
    }
    
    /**
     * 重置统计信息
     */
    public void reset() {
        congestionWindow.set(config.getInitialCwnd());
        slowStartThreshold.set(config.getInitialSsthresh());
        currentState = CongestionState.SLOW_START;
        duplicateAckCount = 0;
        consecutiveSuccesses = 0;
        consecutiveFailures = 0;
        lastCongestionTime = 0;
        
        logger.info("TcpCongestionController reset to initial state");
    }
}