package com.flowreplay.core.report;

/**
 * 接口统计信息
 */
public class ServiceStatistics {
    private final String serviceName;
    private int totalCount;
    private int matchedCount;
    private int mismatchedCount;

    // 原始耗时统计
    private long originalTotalDuration;  // 原始总耗时
    private long originalMinDuration;    // 原始最小耗时
    private long originalMaxDuration;    // 原始最大耗时

    // 回放耗时统计
    private long replayTotalDuration;    // 回放总耗时
    private long replayMinDuration;      // 回放最小耗时
    private long replayMaxDuration;      // 回放最大耗时

    public ServiceStatistics(String serviceName) {
        this.serviceName = serviceName;
        this.totalCount = 0;
        this.matchedCount = 0;
        this.mismatchedCount = 0;
        this.originalTotalDuration = 0;
        this.originalMinDuration = Long.MAX_VALUE;
        this.originalMaxDuration = 0;
        this.replayTotalDuration = 0;
        this.replayMinDuration = Long.MAX_VALUE;
        this.replayMaxDuration = 0;
    }

    public void incrementTotal() {
        this.totalCount++;
    }

    public void incrementMatched() {
        this.matchedCount++;
    }

    public void incrementMismatched() {
        this.mismatchedCount++;
    }

    public void addDuration(long originalDuration, long replayDuration) {
        // 更新原始耗时统计
        this.originalTotalDuration += originalDuration;
        this.originalMinDuration = Math.min(this.originalMinDuration, originalDuration);
        this.originalMaxDuration = Math.max(this.originalMaxDuration, originalDuration);

        // 更新回放耗时统计
        this.replayTotalDuration += replayDuration;
        this.replayMinDuration = Math.min(this.replayMinDuration, replayDuration);
        this.replayMaxDuration = Math.max(this.replayMaxDuration, replayDuration);
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public int getMismatchedCount() {
        return mismatchedCount;
    }

    public double getSuccessRate() {
        if (totalCount == 0) {
            return 0.0;
        }
        return (matchedCount * 100.0) / totalCount;
    }

    // 原始平均耗时
    public double getOriginalAvgDuration() {
        if (totalCount == 0) {
            return 0.0;
        }
        return (double) originalTotalDuration / totalCount;
    }

    // 原始最小耗时
    public long getOriginalMinDuration() {
        return originalMinDuration == Long.MAX_VALUE ? 0 : originalMinDuration;
    }

    // 原始最大耗时
    public long getOriginalMaxDuration() {
        return originalMaxDuration;
    }

    // 回放平均耗时
    public double getReplayAvgDuration() {
        if (totalCount == 0) {
            return 0.0;
        }
        return (double) replayTotalDuration / totalCount;
    }

    // 回放最小耗时
    public long getReplayMinDuration() {
        return replayMinDuration == Long.MAX_VALUE ? 0 : replayMinDuration;
    }

    // 回放最大耗时
    public long getReplayMaxDuration() {
        return replayMaxDuration;
    }
}
