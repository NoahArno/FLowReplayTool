package com.flowreplay.cli;

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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
            case "compare" -> handleCompare(args);
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
            }
        }
    }

    private static void handleRecord(String[] args, boolean requireReplayTarget) {
        int port = 8080;
        String target = "localhost:8081";
        String output = "./recordings";
        String protocol = "http";
        String protocolParser = "raw";
        String replayTarget = null;

        try {
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
                    default -> {
                        if (args[i].startsWith("--")) {
                            throw new IllegalArgumentException("Unknown option for record: " + args[i]);
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid record arguments: " + e.getMessage());
            printUsage();
            return;
        }

        if (!"http".equals(protocol) && !"tcp".equals(protocol)) {
            System.err.println("Invalid protocol: " + protocol + " (supported: http|tcp)");
            return;
        }

        if (requireReplayTarget && (replayTarget == null || replayTarget.isBlank())) {
            System.err.println("record-replay/rr requires --replay-target <url|host:port>");
            return;
        }

        HostPort hostPort = parseHostPort(target, 80);
        if (isSelfProxyLoop(port, hostPort)) {
            System.err.println("Invalid proxy config: --port and --target point to the same endpoint: " + hostPort.host() + ":" + hostPort.port());
            System.err.println("Please set --target to the real upstream service, e.g. --target localhost:8081");
            return;
        }
        LiveReplaySupport liveReplaySupport = new LiveReplaySupport(replayTarget);

        System.out.println("Starting " + protocol.toUpperCase() + " proxy on port " + port);
        System.out.println("Target: " + hostPort.host() + ":" + hostPort.port());
        System.out.println("Output: " + output);
        if (liveReplaySupport.enabled()) {
            System.out.println("Live replay enabled: " + replayTarget);
        } else {
            System.out.println("Live replay disabled");
        }

        TrafficRecorder recorder = null;
        try {
            TrafficStorage storage = new FileStorage(output);
            recorder = new SimpleTrafficRecorder(storage);
            Consumer<TrafficRecord> replayConsumer = liveReplaySupport.enabled() ? liveReplaySupport::submit : null;

            if ("tcp".equals(protocol)) {
                TcpProxyServer server = new TcpProxyServer(
                    port,
                    hostPort.host(),
                    hostPort.port(),
                    recorder,
                    protocolParser,
                    replayConsumer
                );
                server.start();
            } else {
                HttpProxyServer server = new HttpProxyServer(
                    port,
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
            if (recorder != null) {
                recorder.close();
            }
            liveReplaySupport.close();
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

    private static void printUsage() {
        System.out.println("FlowReplay - Traffic Recording and Replay Tool");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  flowreplay record [--port <port>] [--target <host:port>] [--output <path>] [--protocol http|tcp] [--protocol-parser <parser>] [--replay-target <url|host:port>]");
        System.out.println("  flowreplay record-replay|rr [--port <port>] [--target <host:port>] [--output <path>] --replay-target <url|host:port> [--protocol http|tcp] [--protocol-parser <parser>]");
        System.out.println("  flowreplay replay --input <path> --target <url|host:port> [--compare] [--report <path>] [--config <path>] [--service-parser <parser>] [--mode <mode>]");
        System.out.println("  flowreplay compare (not implemented, use replay --compare)");
        System.out.println();
        System.out.println("Key parameters:");
        System.out.println("  --replay-target <url|host:port>  Enable live replay while recording");
        System.out.println("  --replay <url|host:port>         Alias of --replay-target");
        System.out.println("  --mode <mode>                    Replay mode: sequential|concurrent (default: sequential)");
        System.out.println("  --service-parser <parser>        Report parser: uri|esb (default: uri)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  flowreplay record --port 8080 --target localhost:8081 --output ./recordings");
        System.out.println("  flowreplay rr --port 8080 --target localhost:8081 --output ./recordings --replay-target http://localhost:9090");
        System.out.println("  flowreplay record --port 8080 --target localhost:8081 --output ./recordings --replay http://localhost:9090");
        System.out.println("  flowreplay replay --input ./recordings --target http://localhost:9090 --mode concurrent");
        System.out.println("  flowreplay replay --input ./recordings --target http://localhost:9090 --compare --report ./report.html");
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

    private static final class LiveReplaySupport implements AutoCloseable {
        private final boolean enabled;
        private final String replayTarget;
        private final TrafficReplayer replayer;
        private final ExecutorService executor;
        private final AtomicLong total = new AtomicLong();
        private final AtomicLong succeeded = new AtomicLong();
        private final AtomicLong failed = new AtomicLong();
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private LiveReplaySupport(String replayTarget) {
            this.replayTarget = replayTarget;
            this.enabled = replayTarget != null && !replayTarget.isBlank();
            if (enabled) {
                this.replayer = new TrafficReplayer(replayTarget, true);
                this.executor = Executors.newVirtualThreadPerTaskExecutor();
            } else {
                this.replayer = null;
                this.executor = null;
            }
        }

        private boolean enabled() {
            return enabled;
        }

        private void submit(TrafficRecord record) {
            if (!enabled || closed.get()) {
                return;
            }

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
                } catch (Exception e) {
                    total.incrementAndGet();
                    failed.incrementAndGet();
                    System.err.println("Live replay exception, recordId=" + record.id() + ", error=" + e.getMessage());
                }
            });
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
        }
    }
}
