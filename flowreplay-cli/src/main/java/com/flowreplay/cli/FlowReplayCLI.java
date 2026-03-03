package com.flowreplay.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowreplay.core.comparator.ComparisonConfig;
import com.flowreplay.core.comparator.ComparisonConfigLoader;
import com.flowreplay.core.comparator.Comparator;
import com.flowreplay.core.model.ComparisonResult;
import com.flowreplay.core.model.Difference;
import com.flowreplay.core.model.ReplayResult;
import com.flowreplay.core.model.TrafficRecord;
import com.flowreplay.core.recorder.SimpleTrafficRecorder;
import com.flowreplay.core.recorder.TrafficRecorder;
import com.flowreplay.core.replayer.TrafficReplayer;
import com.flowreplay.core.report.ComparisonReport;
import com.flowreplay.core.report.HtmlReportGenerator;
import com.flowreplay.core.storage.FileStorage;
import com.flowreplay.core.storage.QueryCriteria;
import com.flowreplay.core.storage.TrafficStorage;
import com.flowreplay.proxy.HttpProxyServer;
import com.flowreplay.proxy.TcpProxyServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * FlowReplay command line tool.
 */
public class FlowReplayCLI {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0];
        switch (command) {
            case "record" -> handleRecord(args, false);
            case "record-replay", "rr" -> handleRecord(args, true);
            case "replay" -> handleReplay(args);
            case "report-from-cache", "report-cache" -> handleReportFromCache(args);
            case "compare" -> handleCompare(args);
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
            }
        }
    }

    private static void handleRecord(String[] args, boolean requireReplayTarget) {
        RecordCommandOptions options;
        try {
            options = parseRecordOptions(args, requireReplayTarget);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid record arguments: " + e.getMessage());
            printUsage();
            return;
        }

        HostPort hostPort = parseHostPort(options.target(), 80);
        if (isSelfProxyLoop(options.port(), hostPort)) {
            System.err.println("Invalid proxy config: --port and --target point to the same endpoint: " + hostPort.host() + ":" + hostPort.port());
            System.err.println("Please set --target to the real upstream service, e.g. --target localhost:8081");
            return;
        }
        LiveReplaySupport liveReplaySupport = new LiveReplaySupport(
            options.replayTarget(),
            options.enableCompare(),
            options.reportPath(),
            options.configPath(),
            options.serviceParser(),
            options.liveReportCachePath()
        );

        System.out.println("Starting " + options.protocol().toUpperCase() + " proxy on port " + options.port());
        System.out.println("Target: " + hostPort.host() + ":" + hostPort.port());
        System.out.println("Output: " + options.output());
        if (liveReplaySupport.enabled()) {
            System.out.println("Live replay enabled: " + options.replayTarget());
            if (liveReplaySupport.compareEnabled()) {
                System.out.println("Live comparison enabled");
                if (options.reportPath() != null) {
                    System.out.println("Live report output: " + options.reportPath());
                }
                if (options.serviceParser() != null) {
                    System.out.println("Live report parser: " + options.serviceParser());
                }
                System.out.println("Live report cache: " + options.liveReportCachePath());
            }
        } else {
            System.out.println("Live replay disabled");
        }

        TrafficRecorder recorder = null;
        AtomicReference<TrafficRecorder> recorderRef = new AtomicReference<>();
        AtomicBoolean cleanedUp = new AtomicBoolean(false);
        Thread shutdownHook = new Thread(() -> {
            System.out.println("\nShutdown signal received, finalizing live replay and report...");
            closeRecordResources(recorderRef.get(), liveReplaySupport, cleanedUp);
        }, "flowreplay-record-shutdown");

        Runtime.getRuntime().addShutdownHook(shutdownHook);
        try {
            TrafficStorage storage = new FileStorage(options.output());
            recorder = new SimpleTrafficRecorder(storage);
            recorderRef.set(recorder);
            Consumer<TrafficRecord> replayConsumer = liveReplaySupport.enabled() ? liveReplaySupport::submit : null;

            if ("tcp".equals(options.protocol())) {
                TcpProxyServer server = new TcpProxyServer(
                    options.port(),
                    hostPort.host(),
                    hostPort.port(),
                    recorder,
                    options.protocolParser(),
                    replayConsumer
                );
                server.start();
            } else {
                HttpProxyServer server = new HttpProxyServer(
                    options.port(),
                    hostPort.host(),
                    hostPort.port(),
                    recorder,
                    replayConsumer
                );
                server.start();
            }
        } catch (Exception e) {
            System.err.println("Failed to start proxy: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeRecordResources(recorderRef.get(), liveReplaySupport, cleanedUp);
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down, hook is running or has run.
            }
        }
    }

    private static void handleReplay(String[] args) {
        String input = "./recordings";
        String target = "http://localhost:8080";
        boolean enableCompare = false;
        String reportPath = null;
        String configPath = null;
        String serviceParser = null;
        String replayMode = "sequential";

        try {
            for (int i = 1; i < args.length; i++) {
                switch (args[i]) {
                    case "--input" -> input = requireOptionValue(args, ++i, "--input");
                    case "--target" -> target = requireOptionValue(args, ++i, "--target");
                    case "--compare" -> enableCompare = true;
                    case "--report" -> reportPath = requireOptionValue(args, ++i, "--report");
                    case "--config" -> configPath = requireOptionValue(args, ++i, "--config");
                    case "--service-parser" -> serviceParser = requireOptionValue(args, ++i, "--service-parser");
                    case "--mode" -> replayMode = requireOptionValue(args, ++i, "--mode");
                    default -> {
                        if (args[i].startsWith("--")) {
                            throw new IllegalArgumentException("Unknown option for replay: " + args[i]);
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid replay arguments: " + e.getMessage());
            printUsage();
            return;
        }

        System.out.println("Replaying traffic from: " + input);
        System.out.println("Target: " + target);
        System.out.println("Replay mode: " + replayMode);

        if (!"sequential".equalsIgnoreCase(replayMode) && !"concurrent".equalsIgnoreCase(replayMode)) {
            System.err.println("Invalid replay mode: " + replayMode + " (supported: sequential|concurrent)");
            return;
        }

        try {
            TrafficStorage storage = new FileStorage(input);
            List<TrafficRecord> records = storage.query(
                QueryCriteria.builder().limit(Integer.MAX_VALUE).build()
            );

            System.out.println("Found " + records.size() + " records");
            if (records.isEmpty()) {
                System.out.println("No records found, nothing to replay.");
                return;
            }

            boolean sequentialMode = !"concurrent".equalsIgnoreCase(replayMode);
            TrafficReplayer replayer = new TrafficReplayer(target, sequentialMode);
            List<ReplayResult> results = replayer.replay(records);

            long successCount = results.stream().filter(ReplayResult::success).count();
            System.out.println("Replay completed: " + successCount + "/" + results.size() + " succeeded");

            if (enableCompare) {
                System.out.println("\nStarting comparison...");

                List<ComparisonConfig> configs = configPath != null
                    ? ComparisonConfigLoader.load(configPath)
                    : ComparisonConfigLoader.loadDefault();

                Comparator comparator = new Comparator(configs);
                List<ComparisonReport> comparisonReports = new ArrayList<>();

                for (int i = 0; i < records.size(); i++) {
                    TrafficRecord record = records.get(i);
                    ReplayResult replayResult = results.get(i);
                    java.time.Instant replayTimestamp = java.time.Instant.now();

                    if (replayResult.success()) {
                        ComparisonResult comparisonResult = comparator.compare(record, replayResult.response());
                        comparisonReports.add(new ComparisonReport(record, replayResult.response(), comparisonResult, replayResult.duration(), replayTimestamp));
                    } else {
                        ComparisonResult failedResult = new ComparisonResult(
                            false,
                            List.of(new Difference("replay", "error", "success", "failed: " + replayResult.errorMessage())),
                            Map.of()
                        );
                        comparisonReports.add(new ComparisonReport(record, null, failedResult, replayResult.duration(), replayTimestamp));
                    }
                }

                long matchedCount = comparisonReports.stream()
                    .filter(r -> r.result().matched())
                    .count();
                System.out.println("Comparison completed: " + matchedCount + "/" + comparisonReports.size() + " matched");

                if (reportPath != null) {
                    System.out.println("\nGenerating HTML report...");
                    if (serviceParser != null) {
                        System.out.println("Using service parser: " + serviceParser);
                    }
                    HtmlReportGenerator reportGenerator = new HtmlReportGenerator();
                    reportGenerator.generateReport(comparisonReports, reportPath, serviceParser);
                    System.out.println("Report generated: " + reportPath);
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to replay: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleCompare(String[] args) {
        System.out.println("Compare functionality is not implemented yet.");
        System.out.println("Use replay --compare to compare recorded vs replayed responses.");
    }

    private static void handleReportFromCache(String[] args) {
        ReportFromCacheOptions options;
        try {
            options = parseReportFromCacheOptions(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid report-from-cache arguments: " + e.getMessage());
            printUsage();
            return;
        }

        try {
            List<ComparisonReport> reports = LiveReportCacheStore.readReports(options.cachePath());
            if (reports.isEmpty()) {
                System.out.println("No cached comparison data found: " + options.cachePath());
                return;
            }

            HtmlReportGenerator reportGenerator = new HtmlReportGenerator();
            reportGenerator.generateReport(reports, options.reportPath(), options.serviceParser());
            System.out.println("Report generated from cache: " + options.reportPath());
            System.out.println("Cached records used: " + reports.size());
        } catch (Exception e) {
            System.err.println("Failed to generate report from cache: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static RecordCommandOptions parseRecordOptions(String[] args, boolean requireReplayTarget) {
        int port = 8080;
        String target = "localhost:8081";
        String output = "./recordings";
        String protocol = "http";
        String protocolParser = "raw";
        String replayTarget = null;
        boolean enableCompare = false;
        String reportPath = null;
        String configPath = null;
        String serviceParser = null;
        String liveReportCachePath = null;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(requireOptionValue(args, ++i, "--port"));
                case "--target" -> target = requireOptionValue(args, ++i, "--target");
                case "--output" -> output = requireOptionValue(args, ++i, "--output");
                case "--protocol" -> protocol = requireOptionValue(args, ++i, "--protocol").toLowerCase();
                case "--protocol-parser" -> protocolParser = requireOptionValue(args, ++i, "--protocol-parser");
                case "--replay-target", "--replay" -> {
                    String option = args[i];
                    replayTarget = requireOptionValue(args, ++i, option);
                }
                case "--compare" -> enableCompare = true;
                case "--report" -> reportPath = requireOptionValue(args, ++i, "--report");
                case "--config" -> configPath = requireOptionValue(args, ++i, "--config");
                case "--service-parser" -> serviceParser = requireOptionValue(args, ++i, "--service-parser");
                case "--cache" -> liveReportCachePath = requireOptionValue(args, ++i, "--cache");
                default -> {
                    if (args[i].startsWith("--")) {
                        throw new IllegalArgumentException("Unknown option for record: " + args[i]);
                    }
                }
            }
        }

        if (!"http".equals(protocol) && !"tcp".equals(protocol)) {
            throw new IllegalArgumentException("Invalid protocol: " + protocol + " (supported: http|tcp)");
        }

        if (reportPath != null && !enableCompare) {
            enableCompare = true;
        }

        if (liveReportCachePath != null && !enableCompare) {
            enableCompare = true;
        }

        if ((configPath != null || serviceParser != null) && !enableCompare) {
            throw new IllegalArgumentException("--config/--service-parser requires --compare or --report");
        }

        boolean replayRelatedCompareOption = enableCompare || reportPath != null || configPath != null || serviceParser != null || liveReportCachePath != null;
        if (replayRelatedCompareOption && (replayTarget == null || replayTarget.isBlank())) {
            throw new IllegalArgumentException("Live compare/report options require --replay-target <url|host:port>");
        }

        if (requireReplayTarget && (replayTarget == null || replayTarget.isBlank())) {
            throw new IllegalArgumentException("record-replay/rr requires --replay-target <url|host:port>");
        }

        if (enableCompare && liveReportCachePath == null) {
            liveReportCachePath = buildDefaultLiveCachePath(output);
        }

        return new RecordCommandOptions(
            port,
            target,
            output,
            protocol,
            protocolParser,
            replayTarget,
            enableCompare,
            reportPath,
            configPath,
            serviceParser,
            liveReportCachePath
        );
    }

    static ReportFromCacheOptions parseReportFromCacheOptions(String[] args) {
        String cachePath = null;
        String reportPath = null;
        String serviceParser = null;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--cache" -> cachePath = requireOptionValue(args, ++i, "--cache");
                case "--report" -> reportPath = requireOptionValue(args, ++i, "--report");
                case "--service-parser" -> serviceParser = requireOptionValue(args, ++i, "--service-parser");
                default -> {
                    if (args[i].startsWith("--")) {
                        throw new IllegalArgumentException("Unknown option for report-from-cache: " + args[i]);
                    }
                }
            }
        }

        if (cachePath == null || cachePath.isBlank()) {
            throw new IllegalArgumentException("--cache is required");
        }
        if (reportPath == null || reportPath.isBlank()) {
            throw new IllegalArgumentException("--report is required");
        }

        return new ReportFromCacheOptions(cachePath, reportPath, serviceParser);
    }

    private static String buildDefaultLiveCachePath(String output) {
        String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .format(Instant.now().atZone(ZoneId.systemDefault()));
        Path cachePath = Paths.get(output).resolve("live-report-cache-" + ts + ".jsonl");
        return cachePath.toString();
    }

    private static HostPort parseHostPort(String target, int defaultPort) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("target must not be empty");
        }

        if (target.contains("://")) {
            URI uri = URI.create(target);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("invalid target: " + target);
            }
            int port = uri.getPort() > 0 ? uri.getPort() : defaultPort;
            return new HostPort(uri.getHost(), port);
        }

        int index = target.lastIndexOf(':');
        if (index > 0 && index < target.length() - 1) {
            String host = target.substring(0, index);
            int port = Integer.parseInt(target.substring(index + 1));
            return new HostPort(host, port);
        }
        return new HostPort(target, defaultPort);
    }

    private static String requireOptionValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private static void closeRecordResources(TrafficRecorder recorder, LiveReplaySupport liveReplaySupport, AtomicBoolean cleanedUp) {
        if (!cleanedUp.compareAndSet(false, true)) {
            return;
        }
        if (recorder != null) {
            try {
                recorder.close();
            } catch (Exception e) {
                System.err.println("Failed to close recorder: " + e.getMessage());
            }
        }
        try {
            liveReplaySupport.close();
        } catch (Exception e) {
            System.err.println("Failed to close live replay support: " + e.getMessage());
        }
    }

    private static void printUsage() {
        System.out.println("FlowReplay - Traffic Recording and Replay Tool");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  flowreplay record [--port <port>] [--target <host:port>] [--output <path>] [--protocol http|tcp] [--protocol-parser <parser>] [--replay-target <url|host:port>] [--compare] [--report <path>] [--cache <path>] [--config <path>] [--service-parser <parser>]");
        System.out.println("  flowreplay record-replay|rr [--port <port>] [--target <host:port>] [--output <path>] --replay-target <url|host:port> [--protocol http|tcp] [--protocol-parser <parser>] [--compare] [--report <path>] [--cache <path>] [--config <path>] [--service-parser <parser>]");
        System.out.println("  flowreplay replay --input <path> --target <url|host:port> [--compare] [--report <path>] [--config <path>] [--service-parser <parser>] [--mode <mode>]");
        System.out.println("  flowreplay report-from-cache --cache <path> --report <path> [--service-parser <parser>]");
        System.out.println("  flowreplay compare (not implemented, use replay --compare)");
        System.out.println();
        System.out.println("Key parameters:");
        System.out.println("  --replay-target <url|host:port>  Enable live replay while recording");
        System.out.println("  --replay <url|host:port>         Alias of --replay-target");
        System.out.println("  --compare                        Compare recorded and replayed responses");
        System.out.println("  --report <path>                  HTML report output path (auto-enables --compare)");
        System.out.println("  --cache <path>                   Cache live comparison data to JSONL");
        System.out.println("  --config <path>                  Comparison config YAML");
        System.out.println("  --mode <mode>                    Replay mode: sequential|concurrent (default: sequential)");
        System.out.println("  --service-parser <parser>        Report parser: uri|esb (default: uri)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  flowreplay record --port 8080 --target localhost:8081 --output ./recordings");
        System.out.println("  flowreplay rr --port 8080 --target localhost:8081 --output ./recordings --replay-target http://localhost:9090");
        System.out.println("  flowreplay rr --port 8080 --target localhost:8081 --output ./recordings --replay-target http://localhost:9090 --compare --report ./live-report.html");
        System.out.println("  flowreplay record --port 8080 --target localhost:8081 --output ./recordings --replay http://localhost:9090");
        System.out.println("  flowreplay replay --input ./recordings --target http://localhost:9090 --mode concurrent");
        System.out.println("  flowreplay replay --input ./recordings --target http://localhost:9090 --compare --report ./report.html");
        System.out.println("  flowreplay report-from-cache --cache ./recordings/live-report-cache-20260303-120000.jsonl --report ./manual-report.html");
    }

    private static boolean isSelfProxyLoop(int port, HostPort target) {
        if (port != target.port()) {
            return false;
        }
        String host = target.host();
        return "localhost".equalsIgnoreCase(host)
            || "127.0.0.1".equals(host)
            || "::1".equals(host);
    }

    private record HostPort(String host, int port) {
    }

    record RecordCommandOptions(
        int port,
        String target,
        String output,
        String protocol,
        String protocolParser,
        String replayTarget,
        boolean enableCompare,
        String reportPath,
        String configPath,
        String serviceParser,
        String liveReportCachePath
    ) {
    }

    record ReportFromCacheOptions(
        String cachePath,
        String reportPath,
        String serviceParser
    ) {
    }

    record CachedComparisonReport(
        String sessionId,
        long seq,
        ComparisonReport report
    ) {
    }

    private static final class LiveReplaySupport implements AutoCloseable {
        private final boolean enabled;
        private final boolean compareEnabled;
        private final String replayTarget;
        private final String reportPath;
        private final String serviceParser;
        private final String liveReportCachePath;
        private final TrafficReplayer replayer;
        private final ExecutorService executor;
        private final Comparator comparator;
        private final HtmlReportGenerator reportGenerator;
        private final LiveReportCacheStore cacheStore;
        private final Map<Long, ComparisonReport> comparisonReports;
        private final AtomicLong total = new AtomicLong();
        private final AtomicLong succeeded = new AtomicLong();
        private final AtomicLong failed = new AtomicLong();
        private final AtomicLong matched = new AtomicLong();
        private final AtomicLong sequence = new AtomicLong();
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final String cacheSessionId;

        private LiveReplaySupport(
            String replayTarget,
            boolean enableCompare,
            String reportPath,
            String configPath,
            String serviceParser,
            String liveReportCachePath
        ) {
            this.replayTarget = replayTarget;
            this.enabled = replayTarget != null && !replayTarget.isBlank();
            this.compareEnabled = enabled && enableCompare;
            this.reportPath = reportPath;
            this.serviceParser = serviceParser;
            this.liveReportCachePath = liveReportCachePath;
            this.cacheSessionId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
                .format(Instant.now().atZone(ZoneId.systemDefault()));
            if (enabled) {
                this.replayer = new TrafficReplayer(replayTarget, true);
                this.executor = Executors.newVirtualThreadPerTaskExecutor();
                if (compareEnabled) {
                    List<ComparisonConfig> configs = configPath != null
                        ? ComparisonConfigLoader.load(configPath)
                        : ComparisonConfigLoader.loadDefault();
                    this.comparator = new Comparator(configs);
                    this.reportGenerator = new HtmlReportGenerator();
                    this.comparisonReports = new ConcurrentHashMap<>();
                    this.cacheStore = LiveReportCacheStore.openForAppend(liveReportCachePath);
                } else {
                    this.comparator = null;
                    this.reportGenerator = null;
                    this.comparisonReports = null;
                    this.cacheStore = null;
                }
            } else {
                this.replayer = null;
                this.executor = null;
                this.comparator = null;
                this.reportGenerator = null;
                this.comparisonReports = null;
                this.cacheStore = null;
            }
        }

        private boolean enabled() {
            return enabled;
        }

        private boolean compareEnabled() {
            return compareEnabled;
        }

        private void submit(TrafficRecord record) {
            if (!enabled || closed.get()) {
                return;
            }

            long seq = sequence.incrementAndGet();
            executor.submit(() -> {
                try {
                    ReplayResult replayResult = replayer.replay(List.of(record)).get(0);
                    total.incrementAndGet();
                    if (replayResult.success()) {
                        succeeded.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                        System.err.println("Live replay failed, recordId=" + record.id() + ", error=" + replayResult.errorMessage());
                    }
                    if (compareEnabled) {
                        ComparisonReport report = buildComparisonReport(record, replayResult);
                        comparisonReports.put(seq, report);
                        cacheReport(seq, report);
                        if (report.result().matched()) {
                            matched.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    total.incrementAndGet();
                    failed.incrementAndGet();
                    String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                    System.err.println("Live replay exception, recordId=" + record.id() + ", error=" + errorMessage);
                    if (compareEnabled) {
                        ReplayResult failedReplayResult = ReplayResult.failure(record.id(), 0, errorMessage);
                        ComparisonReport report = buildComparisonReport(record, failedReplayResult);
                        comparisonReports.put(seq, report);
                        cacheReport(seq, report);
                    }
                }
            });
        }

        private ComparisonReport buildComparisonReport(TrafficRecord record, ReplayResult replayResult) {
            Instant replayTimestamp = Instant.now();
            if (replayResult.success()) {
                ComparisonResult comparisonResult = comparator.compare(record, replayResult.response());
                return new ComparisonReport(record, replayResult.response(), comparisonResult, replayResult.duration(), replayTimestamp);
            }
            ComparisonResult failedResult = new ComparisonResult(
                false,
                List.of(new Difference("replay", "error", "success", "failed: " + replayResult.errorMessage())),
                Map.of()
            );
            return new ComparisonReport(record, null, failedResult, replayResult.duration(), replayTimestamp);
        }

        private void cacheReport(long seq, ComparisonReport report) {
            try {
                cacheStore.append(cacheSessionId, seq, report);
            } catch (Exception e) {
                System.err.println("Failed to persist live report cache, seq=" + seq + ", error=" + e.getMessage());
            }
        }

        @Override
        public void close() {
            if (!enabled || !closed.compareAndSet(false, true)) {
                return;
            }

            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }

            System.out.println(
                "Live replay summary to " + replayTarget + ": "
                    + succeeded.get() + "/" + total.get() + " succeeded, failed=" + failed.get()
            );

            if (compareEnabled) {
                try {
                    cacheStore.close();
                } catch (Exception e) {
                    System.err.println("Failed to close live report cache: " + e.getMessage());
                }

                long compared = comparisonReports.size();
                System.out.println(
                    "Live comparison summary: "
                        + matched.get() + "/" + compared + " matched"
                );
                System.out.println("Live comparison cache saved: " + liveReportCachePath);

                if (reportPath != null) {
                    try {
                        List<ComparisonReport> orderedReports = comparisonReports.entrySet()
                            .stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(Map.Entry::getValue)
                            .toList();
                        reportGenerator.generateReport(orderedReports, reportPath, serviceParser);
                        System.out.println("Live report generated: " + reportPath);
                    } catch (Exception e) {
                        System.err.println("Failed to generate live report: " + e.getMessage());
                    }
                }
            }
        }
    }

    private static final class LiveReportCacheStore implements AutoCloseable {
        private final Path cachePath;
        private final ObjectMapper objectMapper;
        private final BufferedWriter writer;
        private final Object writeLock = new Object();

        private LiveReportCacheStore(Path cachePath, ObjectMapper objectMapper, BufferedWriter writer) {
            this.cachePath = cachePath;
            this.objectMapper = objectMapper;
            this.writer = writer;
        }

        static LiveReportCacheStore openForAppend(String cachePath) {
            try {
                Path path = Paths.get(cachePath);
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                BufferedWriter writer = Files.newBufferedWriter(
                    path,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                );
                return new LiveReportCacheStore(path, objectMapper, writer);
            } catch (IOException e) {
                throw new RuntimeException("Failed to open live report cache: " + cachePath, e);
            }
        }

        static List<ComparisonReport> readReports(String cachePath) {
            Path path = Paths.get(cachePath);
            if (!Files.exists(path)) {
                return List.of();
            }
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            List<CachedComparisonReport> cached = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    cached.add(objectMapper.readValue(line, CachedComparisonReport.class));
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read cache file: " + cachePath, e);
            }
            return cached.stream()
                .sorted((a, b) -> {
                    String sessionA = a.sessionId() == null ? "" : a.sessionId();
                    String sessionB = b.sessionId() == null ? "" : b.sessionId();
                    int sessionCmp = sessionA.compareTo(sessionB);
                    if (sessionCmp != 0) {
                        return sessionCmp;
                    }
                    return Long.compare(a.seq(), b.seq());
                })
                .map(CachedComparisonReport::report)
                .toList();
        }

        void append(String sessionId, long seq, ComparisonReport report) {
            synchronized (writeLock) {
                try {
                    writer.write(objectMapper.writeValueAsString(new CachedComparisonReport(sessionId, seq, report)));
                    writer.newLine();
                    writer.flush();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to append live report cache: " + cachePath, e);
                }
            }
        }

        @Override
        public void close() {
            synchronized (writeLock) {
                try {
                    writer.close();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to close live report cache: " + cachePath, e);
                }
            }
        }
    }
}
