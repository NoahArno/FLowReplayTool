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
        // 解析参数：--input ./recordings --target http://localhost:8080 --compare --report ./report.html --config ./rules.yaml
        String input = "./recordings";
        String target = "http://localhost:8080";
        boolean enableCompare = false;
        String reportPath = null;
        String configPath = null;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--input" -> input = args[++i];
                case "--target" -> target = args[++i];
                case "--compare" -> enableCompare = true;
                case "--report" -> reportPath = args[++i];
                case "--config" -> configPath = args[++i];
            }
        }

        System.out.println("Replaying traffic from: " + input);
        System.out.println("Target: " + target);

        try {
            TrafficStorage storage = new FileStorage(input);
            List<TrafficRecord> records = storage.query(QueryCriteria.builder().build());

            System.out.println("Found " + records.size() + " records");

            TrafficReplayer replayer = new TrafficReplayer(target);
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

                    if (replayResult.success()) {
                        ComparisonResult comparisonResult = comparator.compare(record, replayResult.response());
                        comparisonReports.add(new ComparisonReport(record, comparisonResult));
                    } else {
                        // 回放失败的记录，创建失败的比对结果
                        ComparisonResult failedResult = new ComparisonResult(
                            false,
                            List.of(new Difference("replay", "error", "success", "failed: " + replayResult.errorMessage())),
                            Map.of()
                        );
                        comparisonReports.add(new ComparisonReport(record, failedResult));
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
                    HtmlReportGenerator reportGenerator = new HtmlReportGenerator();
                    reportGenerator.generateReport(comparisonReports, reportPath);
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
        System.out.println("  flowreplay replay --input <path> --target <url> [--compare] [--report <path>] [--config <path>]");
        System.out.println("  flowreplay compare --recorded <path> --replayed <path>");
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
        System.out.println("  # 回放并比对，生成HTML报告");
        System.out.println("  flowreplay replay --input ./recordings --target http://localhost:9090 --compare --report ./report.html");
        System.out.println();
        System.out.println("  # 使用自定义比对规则");
        System.out.println("  flowreplay replay --input ./recordings --target http://localhost:9090 --compare --report ./report.html --config ./rules.yaml");
    }
}
