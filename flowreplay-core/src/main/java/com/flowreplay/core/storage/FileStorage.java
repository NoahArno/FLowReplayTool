package com.flowreplay.core.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowreplay.core.model.TrafficRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 文件存储实现
 */
public class FileStorage implements TrafficStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);
    private final Path basePath;
    private final ObjectMapper objectMapper;

    public FileStorage(String basePath) {
        this.basePath = Paths.get(basePath);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create storage directory", e);
        }
    }

    @Override
    public void save(TrafficRecord record) {
        try {
            Path filePath = getFilePath(record.id());
            Files.createDirectories(filePath.getParent());
            objectMapper.writeValue(filePath.toFile(), record);
            log.debug("Saved record: {}", record.id());
        } catch (IOException e) {
            log.error("Failed to save record: {}", record.id(), e);
            throw new RuntimeException("Failed to save record", e);
        }
    }

    @Override
    public Optional<TrafficRecord> findById(String id) {
        try {
            Path filePath = getFilePath(id);
            if (!Files.exists(filePath)) {
                return Optional.empty();
            }
            TrafficRecord record = objectMapper.readValue(filePath.toFile(), TrafficRecord.class);
            return Optional.of(record);
        } catch (IOException e) {
            log.error("Failed to read record: {}", id, e);
            return Optional.empty();
        }
    }

    @Override
    public List<TrafficRecord> query(QueryCriteria criteria) {
        List<TrafficRecord> results = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(basePath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".json"))
                 .skip(criteria.offset())
                 .limit(criteria.limit())
                 .forEach(path -> {
                     try {
                         TrafficRecord record = objectMapper.readValue(path.toFile(), TrafficRecord.class);
                         if (matchesCriteria(record, criteria)) {
                             results.add(record);
                         }
                     } catch (IOException e) {
                         log.warn("Failed to read file: {}", path, e);
                     }
                 });
        } catch (IOException e) {
            log.error("Failed to query records", e);
        }
        return results;
    }

    @Override
    public void delete(String id) {
        try {
            Path filePath = getFilePath(id);
            Files.deleteIfExists(filePath);
            log.debug("Deleted record: {}", id);
        } catch (IOException e) {
            log.error("Failed to delete record: {}", id, e);
        }
    }

    @Override
    public void close() {
        // No resources to close for file storage
    }

    private Path getFilePath(String id) {
        // 按日期分片存储：basePath/2024-01-30/record-id.json
        String date = DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.now().atZone(java.time.ZoneId.systemDefault()));
        return basePath.resolve(date).resolve(id + ".json");
    }

    private boolean matchesCriteria(TrafficRecord record, QueryCriteria criteria) {
        if (criteria.protocol() != null && !criteria.protocol().equals(record.protocol())) {
            return false;
        }
        if (criteria.startTime() != null && record.timestamp().isBefore(criteria.startTime())) {
            return false;
        }
        if (criteria.endTime() != null && record.timestamp().isAfter(criteria.endTime())) {
            return false;
        }
        return true;
    }
}
