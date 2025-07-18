package com.adaptive.qps;

import java.util.List;

/**
 * 流量释放SDK接口
 * 用户提供的接口，用于获取B系统状态和释放流量
 */
public interface TrafficReleaseSDK {
    
    /**
     * 积压压流量查询(释放成功的不会查到，只返回未释放成功的)
     * 
     * @param pageSize 分页大小
     * @return 流量统计信息
     */
    TrafficStats queryBacklog(int pageSize);
    
    /**
     * 流量释放执行（执行里面会嵌入验证Case）
     * 
     * @param taskId 任务ID
     * @return 释放结果
     */
    ReleaseResult releaseTraffic(Long taskId);
}

/**
 * 流量统计信息
 */
class TrafficStats {
    public int backlogCount;      // 积压请求数
    public double currentRate;    // 当前释放速率
    public int systemLoad;        // 系统负载百分比
    public int curCaseId;         // 当前执行的caseId
    public int preCaseId;         // 上一个执行完成的caseId
    public int nextCaseId;        // 下一个待执行的caseId
    public int pendingCaseNum;    // 待执行的case数量
    public List<Long> taskIds;    // 待释放的任务ID
    
    public TrafficStats() {}
    
    public TrafficStats(int backlogCount, double currentRate, int systemLoad, 
                       int curCaseId, int preCaseId, int nextCaseId, 
                       int pendingCaseNum, List<Long> taskIds) {
        this.backlogCount = backlogCount;
        this.currentRate = currentRate;
        this.systemLoad = systemLoad;
        this.curCaseId = curCaseId;
        this.preCaseId = preCaseId;
        this.nextCaseId = nextCaseId;
        this.pendingCaseNum = pendingCaseNum;
        this.taskIds = taskIds;
    }
    
    @Override
    public String toString() {
        return String.format("TrafficStats{backlog=%d, rate=%.2f, load=%d%%, cases=%d/%d/%d, pending=%d, tasks=%d}",
            backlogCount, currentRate, systemLoad, preCaseId, curCaseId, nextCaseId, 
            pendingCaseNum, taskIds != null ? taskIds.size() : 0);
    }
}

/**
 * 释放结果
 */
class ReleaseResult {
    public boolean isSuccess;
    public String failureReason;
    public int backlogCount;      // 积压请求数
    public double currentRate;    // 当前释放速率
    public double limitRate;      // 当前三方配置的限流值，并不是三方实际动态限流
    public long rtt;              // RTT
    
    public ReleaseResult() {}
    
    public ReleaseResult(boolean isSuccess, String failureReason, 
                        int backlogCount, double currentRate, double limitRate, long rtt) {
        this.isSuccess = isSuccess;
        this.failureReason = failureReason;
        this.backlogCount = backlogCount;
        this.currentRate = currentRate;
        this.limitRate = limitRate;
        this.rtt = rtt;
    }
    
    @Override
    public String toString() {
        return String.format("ReleaseResult{success=%s, reason='%s', backlog=%d, rate=%.2f, limit=%.2f, rtt=%dms}",
            isSuccess, failureReason, backlogCount, currentRate, limitRate, rtt);
    }
}