package com.flowreplay.core.storage;

import com.flowreplay.core.model.TrafficRecord;

import java.util.List;
import java.util.Optional;

/**
 * 流量存储接口
 */
public interface TrafficStorage {

    /**
     * 保存流量记录
     */
    void save(TrafficRecord record);

    /**
     * 根据ID查找记录
     */
    Optional<TrafficRecord> findById(String id);

    /**
     * 查询记录列表
     */
    List<TrafficRecord> query(QueryCriteria criteria);

    /**
     * 删除记录
     */
    void delete(String id);

    /**
     * 关闭存储
     */
    void close();
}
