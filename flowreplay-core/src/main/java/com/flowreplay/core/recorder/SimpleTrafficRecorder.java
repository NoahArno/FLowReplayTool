package com.flowreplay.core.recorder;

import com.flowreplay.core.model.TrafficRecord;
import com.flowreplay.core.storage.TrafficStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 简单的流量录制器实现
 */
public class SimpleTrafficRecorder implements TrafficRecorder {

    private static final Logger log = LoggerFactory.getLogger(SimpleTrafficRecorder.class);
    private final TrafficStorage storage;

    public SimpleTrafficRecorder(TrafficStorage storage) {
        this.storage = storage;
    }

    @Override
    public void record(TrafficRecord record) {
        try {
            storage.save(record);
            log.debug("Recorded traffic: {}", record.id());
        } catch (Exception e) {
            log.error("Failed to record traffic: {}", record.id(), e);
        }
    }

    @Override
    public void close() {
        storage.close();
    }
}
