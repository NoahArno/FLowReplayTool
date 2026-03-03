package com.flowreplay.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowReplayCLITest {

    @Test
    void parsesLiveCompareAndReportOptionsForRecordReplay() {
        String[] args = {
            "rr",
            "--port", "18080",
            "--target", "localhost:8081",
            "--output", "./records",
            "--replay-target", "http://localhost:9090",
            "--compare",
            "--report", "./live-report.html",
            "--config", "./comparison-rules.yaml",
            "--service-parser", "esb"
        };

        FlowReplayCLI.RecordCommandOptions options = FlowReplayCLI.parseRecordOptions(args, true);

        assertEquals(18080, options.port());
        assertEquals("localhost:8081", options.target());
        assertEquals("./records", options.output());
        assertEquals("http", options.protocol());
        assertEquals("raw", options.protocolParser());
        assertEquals("http://localhost:9090", options.replayTarget());
        assertTrue(options.enableCompare());
        assertEquals("./live-report.html", options.reportPath());
        assertEquals("./comparison-rules.yaml", options.configPath());
        assertEquals("esb", options.serviceParser());
        assertNotNull(options.liveReportCachePath());
    }

    @Test
    void autoEnablesCompareWhenReportProvided() {
        String[] args = {
            "record",
            "--replay-target", "http://localhost:9090",
            "--report", "./live-report.html"
        };

        FlowReplayCLI.RecordCommandOptions options = FlowReplayCLI.parseRecordOptions(args, false);

        assertTrue(options.enableCompare());
        assertEquals("./live-report.html", options.reportPath());
        assertTrue(options.liveReportCachePath().contains("live-report-cache-"));
    }

    @Test
    void usesCustomLiveCachePath() {
        String[] args = {
            "record",
            "--replay-target", "http://localhost:9090",
            "--compare",
            "--cache", "./cache/live.jsonl"
        };

        FlowReplayCLI.RecordCommandOptions options = FlowReplayCLI.parseRecordOptions(args, false);
        assertTrue(options.enableCompare());
        assertEquals("./cache/live.jsonl", options.liveReportCachePath());
    }

    @Test
    void failsWhenCompareUsedWithoutReplayTarget() {
        String[] args = {"record", "--compare"};

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> FlowReplayCLI.parseRecordOptions(args, false)
        );
        assertEquals("Live compare/report options require --replay-target <url|host:port>", error.getMessage());
    }

    @Test
    void failsWhenRecordReplayMissingReplayTarget() {
        String[] args = {"rr"};

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> FlowReplayCLI.parseRecordOptions(args, true)
        );
        assertEquals("record-replay/rr requires --replay-target <url|host:port>", error.getMessage());
    }

    @Test
    void failsWhenConfigProvidedWithoutCompareOrReport() {
        String[] args = {
            "record",
            "--replay-target", "http://localhost:9090",
            "--config", "./comparison-rules.yaml"
        };

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> FlowReplayCLI.parseRecordOptions(args, false)
        );
        assertEquals("--config/--service-parser requires --compare or --report", error.getMessage());
    }

    @Test
    void parsesReportFromCacheOptions() {
        String[] args = {
            "report-from-cache",
            "--cache", "./cache/live.jsonl",
            "--report", "./manual-report.html",
            "--service-parser", "uri"
        };

        FlowReplayCLI.ReportFromCacheOptions options = FlowReplayCLI.parseReportFromCacheOptions(args);
        assertEquals("./cache/live.jsonl", options.cachePath());
        assertEquals("./manual-report.html", options.reportPath());
        assertEquals("uri", options.serviceParser());
    }

    @Test
    void failsWhenCacheMissingForReportFromCache() {
        String[] args = {
            "report-from-cache",
            "--report", "./manual-report.html"
        };

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> FlowReplayCLI.parseReportFromCacheOptions(args)
        );
        assertEquals("--cache is required", error.getMessage());
    }
}
