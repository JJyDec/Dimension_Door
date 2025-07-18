import java.util.*;

/**
 * 自适应QPS控制核心功能使用示例
 */
public class AdaptiveQpsExample {
    
    public static void main(String[] args) {
        // 创建核心控制器
        AdaptiveQpsCore controller = new AdaptiveQpsCore();
        
        System.out.println("=== 自适应QPS控制系统演示 ===\n");
        
        // 模拟不同场景的B系统状态
        demonstrateScenarios(controller);
    }
    
    private static void demonstrateScenarios(AdaptiveQpsCore controller) {
        
        // 场景1: 系统正常负载
        System.out.println("🟢 场景1: 系统正常负载 (50%)");
        AdaptiveQpsCore.TrafficStats normalStats = new AdaptiveQpsCore.TrafficStats(
            50, 25.0, 50, createTaskIds(100)
        );
        demonstrateAdjustmentAndRelease(controller, normalStats);
        
        // 场景2: 系统高负载
        System.out.println("\n🟡 场景2: 系统高负载 (85%)");
        AdaptiveQpsCore.TrafficStats highLoadStats = new AdaptiveQpsCore.TrafficStats(
            200, 15.0, 85, createTaskIds(80)
        );
        demonstrateAdjustmentAndRelease(controller, highLoadStats);
        
        // 场景3: 系统过载
        System.out.println("\n🔴 场景3: 系统过载 (95%)");
        AdaptiveQpsCore.TrafficStats overloadStats = new AdaptiveQpsCore.TrafficStats(
            500, 8.0, 95, createTaskIds(30)
        );
        demonstrateAdjustmentAndRelease(controller, overloadStats);
        
        // 场景4: 系统负载恢复
        System.out.println("\n🟢 场景4: 系统负载恢复 (25%)");
        AdaptiveQpsCore.TrafficStats recoveryStats = new AdaptiveQpsCore.TrafficStats(
            10, 40.0, 25, createTaskIds(150)
        );
        demonstrateAdjustmentAndRelease(controller, recoveryStats);
    }
    
    /**
     * 演示调整参数和释放流量的完整流程
     */
    private static void demonstrateAdjustmentAndRelease(AdaptiveQpsCore controller, 
                                                       AdaptiveQpsCore.TrafficStats stats) {
        
        // 核心函数一: 调整限流参数
        AdaptiveQpsCore.TrafficParameters parameters = controller.adjustTrafficParameters(stats);
        
        System.out.println("📊 系统状态:");
        System.out.printf("   负载: %d%%, 积压: %d, 当前速率: %.1f/s\n", 
            stats.systemLoad, stats.backlogCount, stats.currentRate);
        
        System.out.println("⚙️ 调整结果:");
        System.out.printf("   目标QPS: %d, 拥塞窗口: %d, 令牌桶容量: %d\n", 
            parameters.targetQps, parameters.congestionWindow, parameters.tokenBucketCapacity);
        System.out.printf("   令牌生成速率: %.1f/s, 调整原因: %s\n", 
            parameters.tokenGenerationRate, parameters.adjustReason);
        
        // 核心函数二: 批量释放流量
        AdaptiveQpsCore.BatchReleaseResult result = controller.releaseTrafficBatch(
            stats.taskIds, AdaptiveQpsExample::mockReleaseFunction
        );
        
        System.out.println("🚀 释放结果:");
        System.out.printf("   释放任务: %d, 成功: %d, 失败: %d, 成功率: %.1f%%\n", 
            result.totalRequests, result.successCount, result.failureCount, result.getSuccessRate() * 100);
        System.out.printf("   平均RTT: %.1fms\n", result.avgRtt);
        
        System.out.println("📈 系统状态: " + controller.getSystemStatus());
    }
    
    /**
     * 创建任务ID列表
     */
    private static List<Long> createTaskIds(int count) {
        List<Long> taskIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            taskIds.add(System.currentTimeMillis() + i);
        }
        return taskIds;
    }
    
    /**
     * 模拟流量释放函数
     * 实际使用时需要替换为真实的业务逻辑
     */
    private static AdaptiveQpsCore.ReleaseResult mockReleaseFunction(Long taskId) {
        try {
            // 模拟处理时间
            Thread.sleep(10 + new Random().nextInt(40));
            
            // 模拟成功率 (90%)
            boolean success = new Random().nextInt(100) < 90;
            String failureReason = success ? null : "Mock failure for testing";
            long rtt = 20 + new Random().nextInt(80); // 20-100ms RTT
            
            return new AdaptiveQpsCore.ReleaseResult(success, failureReason, rtt);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AdaptiveQpsCore.ReleaseResult(false, "Interrupted", 0);
        }
    }
}