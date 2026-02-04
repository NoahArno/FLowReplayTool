package com.flowreplay.cli;

import com.flowreplay.core.comparator.*;
import com.flowreplay.core.model.*;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FlowReplay命令行工具
 */
public class FlowReplayCLI {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0];
        switch (command) {
            case "record" -> handleRecord(args);
            case "replay" -> handleReplay(args);
            case "compare" -> handleCompare(args);
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
            }
        }
    }

    private static void handleRecord(String[] args) {
        // 解析参数：--port 8080 --target localhost:8080 --output ./recordings --protocol http
        int port = 8080;
        String target = "localhost:8080";
        String output = "./recordings";
        String protocol = "http";
        String protocolParser = "raw";

        for (int i = 1; i < args.length; i += 2) {
            if (i + 1 >= args.length) break;
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[i + 1]);
                case "--target" -> target = args[i + 1];
                case "--output" -> output = args[i + 1];
                case "--protocol" -> protocol = args[i + 1].toLowerCase();
                case "--protocol-parser" -> protocolParser = args[i + 1];
            }
        }

        System.out.println("Starting " + protocol.toUpperCase() + " proxy on port " + port);
        System.out.println("Target: " + target);
        System.out.println("Output: " + output);

        try {
            TrafficStorage storage = new FileStorage(output);
            TrafficRecorder recorder = new SimpleTrafficRecorder(storage);

            String[] hostPort = target.split(":");

            if ("tcp".equals(protocol)) {
                // TCP代理
                TcpProxyServer server = new TcpProxyServer(
                    port,
                    hostPort[0],
                    hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 80,
                    recorder,
                    protocolParser
                );
                server.start();
            } else {
                // HTTP代理
                HttpProxyServer server = new HttpProxyServer(
                    port,
                    hostPort[0],
                    hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 80,
                    recorder
                );
                server.start();
            }
        } catch (Exception e) {
            System.err.println("Failed to start proxy: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleReplay(String[] args) {
        // 解析参数：--input ./recordings --target http://localhost:8080 --compare --report ./report.html --config ./rules.yaml --service-parser uri --mode sequential
        String input = "./recordings";
        String target = "http://localhost:8080";
        boolean enableCompare = false;
        String reportPath = null;
        String configPath = null;
        String serviceParser = null;
        String replayMode = "sequential";  // 默认顺序回放

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--input" -> input = args[++i];
                case "--target" -> target = args[++i];
                case "--compare" -> enableCompare = true;
                case "--report" -> reportPath = args[++i];
                case "--config" -> configPath = args[++i];
                case "--service-parser" -> serviceParser = args[++i];
                case "--mode" -> replayMode = args[++i];
            }
        }

        System.out.println("Replaying traffic from: " + input);
        System.out.println("Target: " + target);
        System.out.println("Replay mode: " + replayMode);

        try {
            TrafficStorage storage = new FileStorage(input);
            List<TrafficRecord> records = storage.query(QueryCriteria.builder().build());

            System.out.println("Found " + records.size() + " records");

            boolean sequentialMode = !"concurrent".equalsIgnoreCase(replayMode);
            TrafficReplayer replayer = new TrafficReplayer(target, sequentialMode);
            List<ReplayResult> results = replayer.replay(records);

            long successCount = results.stream().filter(ReplayResult::success).count();
            System.out.println("Replay completed: " + successCount + "/" + results.size() + " succeeded");

            // 执行比对
            if (enableCompare) {
                System.out.println("\nStarting comparison...");

                // 加载比对配置
                List<ComparisonConfig> configs = configPath != null
                    ? ComparisonConfigLoader.load(configPath)
                    : ComparisonConfigLoader.loadDefault();

                Comparator comparator = new Comparator(configs);
                List<ComparisonReport> comparisonReports = new ArrayList<>();

                // 对每个回放结果进行比对
                for (int i = 0; i < records.size(); i++) {
                    TrafficRecord record = records.get(i);
                    ReplayResult replayResult = results.get(i);
                    java.time.Instant replayTimestamp = java.time.Instant.now();

                    if (replayResult.success()) {
                        ComparisonResult comparisonResult = comparator.compare(record, replayResult.response());
                        comparisonReports.add(new ComparisonReport(record, replayResult.response(), comparisonResult, replayResult.duration(), replayTimestamp));
                    } else {
                        // 回放失败的记录，创建失败的比对结果
                        ComparisonResult failedResult = new ComparisonResult(
                            false,
                            List.of(new Difference("replay", "error", "success", "failed: " + replayResult.errorMessage())),
                            Map.of()
                        );
                        comparisonReports.add(new ComparisonReport(record, null, failedResult, replayResult.duration(), replayTimestamp));
                    }
                }

                // 统计比对结果
                long matchedCount = comparisonReports.stream()
                    .filter(r -> r.result().matched())
                    .count();
                System.out.println("Comparison completed: " + matchedCount + "/" + comparisonReports.size() + " matched");

                // 生成HTML报告
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
        System.out.println("Compare functionality - to be implemented");
        System.out.println("This will compare recorded vs replayed responses");
    }

    private static void printUsage() {
        System.out.println("FlowReplay - Traffic Recording and Replay Tool");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  flowreplay record --port <port> --target <host:port> --output <path> [--protocol http|tcp] [--protocol-parser <parser>]");
        System.out.println("  flowreplay replay --input <path> --target <url> [--compare] [--report <path>] [--config <path>] [--service-parser <parser>] [--mode <mode>]");
        System.out.println("  flowreplay compare --recorded <path> --replayed <path>");
        System.out.println();
        System.out.println("Parameters:");
        System.out.println("  --service-parser <parser>  接口名解析器，用于按接口统计（可选值：uri, esb，默认：uri）");
        System.out.println("                             uri: 使用URI作为接口名");
        System.out.println("                             esb: 从报文中解析ServiceCode作为接口名");
        System.out.println("  --mode <mode>              回放模式（可选值：sequential, concurrent，默认：sequential）");
        System.out.println("                             sequential: 顺序回放，按录制顺序依次执行");
        System.out.println("                             concurrent: 并发回放，使用Virtual Threads并发执行");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # HTTP录制");
        System.out.println("  flowreplay record --port 8080 --target localhost:8080 --output ./recordings");
        System.out.println();
        System.out.println("  # TCP录制（Redis）");
        System.out.println("  flowreplay record --protocol tcp --port 6380 --target localhost:6379 --output ./recordings --protocol-parser redis");
        System.out.println();
        System.out.println("  # TCP录制（原始模式）");
        System.out.println("  flowreplay record --protocol tcp --port 9999 --target localhost:9999 --output ./recordings --protocol-parser raw");
        System.out.println();
        System.out.println("  # 回放");
        System.out.println("  flowreplay replay --input ./recordings --target http://localhost:9090");
        System.out.println();
        System.out.println("  # 回放并比对，生成HTML报告（默认使用URI作为接口名）");
        System.out.println("  flowreplay replay --input ./recordings --target http://localhost:9090 --compare --report ./report.html");
        System.out.println();
        System.out.println("  # 使用ESB解析器，按ServiceCode统计接口");
        System.out.println("  flowreplay replay --input ./recordings --target http://localhost:9090 --compare --report ./report.html --service-parser esb");
        System.out.println();
        System.out.println("  # 顺序回放（默认模式，保证执行顺序）");
        System.out.println("  flowreplay replay --input ./recordings --target http://localhost:9090 --mode sequential");
        System.out.println();
        System.out.println("  # 并发回放（高性能，但不保证执行顺序）");
        System.out.println("  flowreplay replay --input ./recordings --target http://localhost:9090 --mode concurrent");
        System.out.println();
        System.out.println("  # 使用自定义比对规则");
        System.out.println("  flowreplay replay --input ./recordings --target http://localhost:9090 --compare --report ./report.html --config ./rules.yaml");
    }
}
