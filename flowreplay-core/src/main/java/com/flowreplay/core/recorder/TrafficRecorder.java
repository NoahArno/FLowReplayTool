package com.flowreplay.core.recorder;

import com.flowreplay.core.model.TrafficRecord;

/**
 * 流量录制器接口
 */
public interface TrafficRecorder {

    /**
     * 录制流量
     */
    void record(TrafficRecord record);

    /**
     * 关闭录制器
     */
    void close();
}
