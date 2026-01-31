package com.flowreplay.core.storage;

import java.time.Instant;

/**
 * 查询条件
 */
public record QueryCriteria(
    String protocol,        // 协议过滤
    Instant startTime,      // 开始时间
    Instant endTime,        // 结束时间
    int limit,              // 限制数量
    int offset              // 偏移量
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String protocol;
        private Instant startTime;
        private Instant endTime;
        private int limit = 100;
        private int offset = 0;

        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder offset(int offset) {
            this.offset = offset;
            return this;
        }

        public QueryCriteria build() {
            return new QueryCriteria(protocol, startTime, endTime, limit, offset);
        }
    }
}
