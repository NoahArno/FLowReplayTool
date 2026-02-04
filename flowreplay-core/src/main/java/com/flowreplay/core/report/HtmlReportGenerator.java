package com.flowreplay.core.report;

import com.flowreplay.core.model.ComparisonResult;
import com.flowreplay.core.model.Difference;
import com.flowreplay.core.model.TrafficRecord;
import com.flowreplay.core.parser.ServiceNameParser;
import com.flowreplay.core.parser.ServiceNameParserFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HTML差异报告生成器 - IDEA风格
 */
public class HtmlReportGenerator {

    private ServiceNameParser serviceNameParser;

    public void generateReport(List<ComparisonReport> reports, String outputPath) throws IOException {
        generateReport(reports, outputPath, null);
    }

    public void generateReport(List<ComparisonReport> reports, String outputPath, String parserName) throws IOException {
        this.serviceNameParser = ServiceNameParserFactory.getParser(parserName);

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>FlowReplay 差异报告</title>\n");
        appendStyles(html);
        appendScripts(html);
        html.append("</head>\n");
        html.append("<body>\n");

        appendHeader(html, reports);
        appendSummary(html, reports);
        appendDetailedReports(html, reports);

        html.append("</body>\n");
        html.append("</html>\n");

        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(html.toString());
        }
    }

    private void appendStyles(StringBuilder html) {
        html.append("    <style>\n");
        html.append("        body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }\n");
        html.append("        .header { background: #2196F3; color: white; padding: 20px; border-radius: 5px; }\n");
        html.append("        .summary { background: white; padding: 20px; margin: 20px 0; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        html.append("        .stat { display: inline-block; margin: 10px 20px; }\n");
        html.append("        .stat-value { font-size: 32px; font-weight: bold; }\n");
        html.append("        .stat-label { color: #666; }\n");
        html.append("        .success { color: #4CAF50; }\n");
        html.append("        .failed { color: #f44336; }\n");
        html.append("        .report-item { background: white; margin: 10px 0; padding: 15px; border-radius: 5px; border-left: 4px solid #ddd; }\n");
        html.append("        .report-item.matched { border-left-color: #4CAF50; }\n");
        html.append("        .report-item.mismatched { border-left-color: #f44336; }\n");
        html.append("        .content-section { margin: 15px 0; }\n");
        html.append("        .content-title { font-weight: bold; margin: 10px 0 5px 0; color: #333; cursor: pointer; user-select: none; }\n");
        html.append("        .content-title:hover { color: #2196F3; }\n");
        html.append("        .content-title::before { content: '▼ '; display: inline-block; transition: transform 0.2s; }\n");
        html.append("        .content-title.collapsed::before { transform: rotate(-90deg); }\n");
        html.append("        .content-box { background: #f8f9fa; padding: 10px; border-radius: 3px; font-family: monospace; white-space: pre-wrap; word-break: break-all; max-height: 500px; overflow: auto; border: 1px solid #ddd; }\n");
        html.append("        .content-box.collapsed { display: none; }\n");
        html.append("        .toggle-all { margin: 10px 0; }\n");
        html.append("        .toggle-all button { padding: 8px 16px; margin-right: 10px; cursor: pointer; border: none; border-radius: 3px; background: #2196F3; color: white; }\n");
        html.append("        .toggle-all button:hover { background: #1976D2; }\n");

        // 并排响应对比样式
        html.append("        .diff-container { display: flex; gap: 10px; margin: 10px 0; }\n");
        html.append("        .diff-side { flex: 1; }\n");
        html.append("        .diff-side-title { font-weight: bold; margin-bottom: 5px; padding: 5px; background: #e9ecef; border-radius: 3px; }\n");
        html.append("        .diff-side-title.expected { background: #d4edda; color: #155724; }\n");
        html.append("        .diff-side-title.actual { background: #f8d7da; color: #721c24; }\n");

        // IDEA风格的差异对比样式
        html.append("        .idea-diff-container { display: flex; border: 1px solid #d1d5da; border-radius: 3px; overflow: hidden; margin: 10px 0; }\n");
        html.append("        .idea-diff-side { flex: 1; overflow: auto; max-height: 600px; }\n");
        html.append("        .idea-diff-side.left { border-right: 1px solid #d1d5da; }\n");
        html.append("        .idea-diff-header { background: #f6f8fa; padding: 8px 12px; border-bottom: 1px solid #d1d5da; font-weight: bold; font-size: 13px; }\n");
        html.append("        .idea-diff-header.expected { background: #e6ffed; color: #22863a; }\n");
        html.append("        .idea-diff-header.actual { background: #ffeef0; color: #cb2431; }\n");
        html.append("        .idea-diff-line { display: flex; font-family: 'Consolas', 'Monaco', 'Courier New', monospace; font-size: 12px; line-height: 20px; border-bottom: 1px solid #f0f0f0; }\n");
        html.append("        .idea-diff-line-number { min-width: 50px; padding: 0 8px; text-align: right; color: #8c8c8c; background: #fafafa; border-right: 1px solid #e8e8e8; user-select: none; }\n");
        html.append("        .idea-diff-line-content { flex: 1; padding: 0 8px; white-space: pre-wrap; word-break: break-all; }\n");
        html.append("        .idea-diff-line.unchanged { background: #fff; }\n");
        html.append("        .idea-diff-line.changed { background: #fff5b1; }\n");
        html.append("        .idea-diff-line.added { background: #ddfbe6; }\n");
        html.append("        .idea-diff-line.removed { background: #ffebe9; }\n");
        html.append("        .idea-diff-line.empty { background: #f5f5f5; }\n");

        // 接口统计表格样式
        html.append("        .service-stats-table { width: 100%; border-collapse: collapse; margin-top: 15px; }\n");
        html.append("        .service-stats-table th, .service-stats-table td { padding: 10px; text-align: left; border-bottom: 1px solid #ddd; }\n");
        html.append("        .service-stats-table th { background: #f6f8fa; font-weight: bold; color: #333; }\n");
        html.append("        .service-stats-table tbody tr:hover { background: #f8f9fa; }\n");
        html.append("        .service-stats-table td:nth-child(2), .service-stats-table td:nth-child(3), .service-stats-table td:nth-child(4), .service-stats-table td:nth-child(5) { text-align: center; }\n");
        html.append("        .service-stats-table th:nth-child(2), .service-stats-table th:nth-child(3), .service-stats-table th:nth-child(4), .service-stats-table th:nth-child(5) { text-align: center; }\n");
        html.append("    </style>\n");
    }

    private void appendScripts(StringBuilder html) {
        html.append("    <script>\n");
        html.append("        function toggleContent(id) {\n");
        html.append("            const title = document.getElementById('title-' + id);\n");
        html.append("            const content = document.getElementById('content-' + id);\n");
        html.append("            title.classList.toggle('collapsed');\n");
        html.append("            content.classList.toggle('collapsed');\n");
        html.append("        }\n");
        html.append("        function expandAll() {\n");
        html.append("            document.querySelectorAll('.content-title').forEach(el => el.classList.remove('collapsed'));\n");
        html.append("            document.querySelectorAll('.content-box').forEach(el => el.classList.remove('collapsed'));\n");
        html.append("        }\n");
        html.append("        function collapseAll() {\n");
        html.append("            document.querySelectorAll('.content-title').forEach(el => el.classList.add('collapsed'));\n");
        html.append("            document.querySelectorAll('.content-box').forEach(el => el.classList.add('collapsed'));\n");
        html.append("        }\n");
        html.append("    </script>\n");
    }

    private void appendHeader(StringBuilder html, List<ComparisonReport> reports) {
        html.append("    <div class=\"header\">\n");
        html.append("        <h1>FlowReplay 差异报告</h1>\n");
        html.append("        <p>生成时间: ").append(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(Instant.now().atZone(java.time.ZoneId.systemDefault()))).append("</p>\n");
        html.append("    </div>\n");
    }

    private void appendSummary(StringBuilder html, List<ComparisonReport> reports) {
        long totalCount = reports.size();
        long matchedCount = reports.stream().filter(r -> r.result().matched()).count();
        long mismatchedCount = totalCount - matchedCount;
        double successRate = totalCount > 0 ? (matchedCount * 100.0 / totalCount) : 0;

        html.append("    <div class=\"summary\">\n");
        html.append("        <h2>总体统计</h2>\n");
        html.append("        <div class=\"stat\">\n");
        html.append("            <div class=\"stat-value\">").append(totalCount).append("</div>\n");
        html.append("            <div class=\"stat-label\">总请求数</div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"stat\">\n");
        html.append("            <div class=\"stat-value success\">").append(matchedCount).append("</div>\n");
        html.append("            <div class=\"stat-label\">匹配成功</div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"stat\">\n");
        html.append("            <div class=\"stat-value failed\">").append(mismatchedCount).append("</div>\n");
        html.append("            <div class=\"stat-label\">匹配失败</div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"stat\">\n");
        html.append("            <div class=\"stat-value\">").append(String.format("%.2f%%", successRate)).append("</div>\n");
        html.append("            <div class=\"stat-label\">成功率</div>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");

        // 按接口统计
        appendServiceStatistics(html, reports);
    }

    private void appendServiceStatistics(StringBuilder html, List<ComparisonReport> reports) {
        // 统计每个接口的数据
        Map<String, ServiceStatistics> statsMap = new LinkedHashMap<>();

        for (ComparisonReport report : reports) {
            String serviceName = serviceNameParser.parseServiceName(report.record());
            ServiceStatistics stats = statsMap.computeIfAbsent(serviceName, ServiceStatistics::new);

            stats.incrementTotal();
            if (report.result().matched()) {
                stats.incrementMatched();
            } else {
                stats.incrementMismatched();
            }

            // 添加耗时统计
            long originalDuration = getOriginalDuration(report.record());
            long replayDuration = report.replayDuration();
            stats.addDuration(originalDuration, replayDuration);
        }

        // 生成HTML表格
        html.append("    <div class=\"summary\">\n");
        html.append("        <h2>接口统计</h2>\n");
        html.append("        <table class=\"service-stats-table\">\n");
        html.append("            <thead>\n");
        html.append("                <tr>\n");
        html.append("                    <th rowspan=\"2\">接口名称</th>\n");
        html.append("                    <th rowspan=\"2\">总请求数</th>\n");
        html.append("                    <th rowspan=\"2\">匹配成功</th>\n");
        html.append("                    <th rowspan=\"2\">匹配失败</th>\n");
        html.append("                    <th rowspan=\"2\">成功率</th>\n");
        html.append("                    <th colspan=\"3\">原始耗时(ms)</th>\n");
        html.append("                    <th colspan=\"3\">回放耗时(ms)</th>\n");
        html.append("                </tr>\n");
        html.append("                <tr>\n");
        html.append("                    <th>平均</th>\n");
        html.append("                    <th>最小</th>\n");
        html.append("                    <th>最大</th>\n");
        html.append("                    <th>平均</th>\n");
        html.append("                    <th>最小</th>\n");
        html.append("                    <th>最大</th>\n");
        html.append("                </tr>\n");
        html.append("            </thead>\n");
        html.append("            <tbody>\n");

        for (ServiceStatistics stats : statsMap.values()) {
            html.append("                <tr>\n");
            html.append("                    <td>").append(escapeHtml(stats.getServiceName())).append("</td>\n");
            html.append("                    <td>").append(stats.getTotalCount()).append("</td>\n");
            html.append("                    <td class=\"success\">").append(stats.getMatchedCount()).append("</td>\n");
            html.append("                    <td class=\"failed\">").append(stats.getMismatchedCount()).append("</td>\n");
            html.append("                    <td>").append(String.format("%.2f%%", stats.getSuccessRate())).append("</td>\n");
            // 原始耗时
            html.append("                    <td>").append(String.format("%.2f", stats.getOriginalAvgDuration())).append("</td>\n");
            html.append("                    <td>").append(stats.getOriginalMinDuration()).append("</td>\n");
            html.append("                    <td>").append(stats.getOriginalMaxDuration()).append("</td>\n");
            // 回放耗时
            html.append("                    <td>").append(String.format("%.2f", stats.getReplayAvgDuration())).append("</td>\n");
            html.append("                    <td>").append(stats.getReplayMinDuration()).append("</td>\n");
            html.append("                    <td>").append(stats.getReplayMaxDuration()).append("</td>\n");
            html.append("                </tr>\n");
        }

        html.append("            </tbody>\n");
        html.append("        </table>\n");
        html.append("    </div>\n");
    }

    private void appendDetailedReports(StringBuilder html, List<ComparisonReport> reports) {
        html.append("    <div class=\"details\">\n");
        html.append("        <h2>详细差异列表</h2>\n");
        html.append("        <div class=\"toggle-all\">\n");
        html.append("            <button onclick=\"expandAll()\">展开全部</button>\n");
        html.append("            <button onclick=\"collapseAll()\">折叠全部</button>\n");
        html.append("        </div>\n");

        for (int i = 0; i < reports.size(); i++) {
            ComparisonReport report = reports.get(i);
            appendSingleReport(html, report, i);
        }

        html.append("    </div>\n");
    }

    private void appendSingleReport(StringBuilder html, ComparisonReport report, int index) {
        String cssClass = report.result().matched() ? "matched" : "mismatched";
        TrafficRecord record = report.record();
        String serviceName = serviceNameParser.parseServiceName(record);

        html.append("        <div class=\"report-item ").append(cssClass).append("\">\n");
        html.append("            <h3>请求 #").append(index + 1).append(" - ").append(escapeHtml(record.id())).append("</h3>\n");
        html.append("            <p><strong>协议:</strong> ").append(record.protocol()).append("</p>\n");
        html.append("            <p><strong>URI:</strong> ").append(escapeHtml(record.request().uri())).append("</p>\n");
        html.append("            <p><strong>方法:</strong> ").append(record.request().method()).append("</p>\n");
        html.append("            <p><strong>接口名:</strong> ").append(escapeHtml(serviceName)).append("</p>\n");
        html.append("            <p><strong>状态:</strong> <span class=\"").append(cssClass).append("\">")
                   .append(report.result().matched() ? "✓ 匹配" : "✗ 不匹配").append("</span></p>\n");

        // 显示时间信息
        long originalDuration = getOriginalDuration(record);
        long replayDuration = report.replayDuration();
        String originalTime = formatTimestamp(record.timestamp());
        String replayTime = formatTimestamp(report.replayTimestamp());

        html.append("            <p><strong>原始请求时间:</strong> ").append(originalTime).append("</p>\n");
        html.append("            <p><strong>原始耗时:</strong> ").append(originalDuration).append(" ms</p>\n");
        html.append("            <p><strong>回放请求时间:</strong> ").append(replayTime).append("</p>\n");
        html.append("            <p><strong>回放耗时:</strong> ").append(replayDuration).append(" ms</p>\n");

        // 显示原始请求
        appendRequestDetails(html, record, index);

        // 显示并排响应对比
        appendResponseComparison(html, report, index);

        // 显示IDEA风格的差异详情
        if (!report.result().matched() && report.replayedResponse() != null) {
            appendIdeaDiffComparison(html, report, index);
        }

        html.append("        </div>\n");
    }

    private void appendRequestDetails(StringBuilder html, TrafficRecord record, int index) {
        String id = "req-" + index;
        html.append("            <div class=\"content-section\">\n");
        html.append("                <div class=\"content-title collapsed\" id=\"title-").append(id).append("\" onclick=\"toggleContent('").append(id).append("')\">\n");
        html.append("                    原始请求\n");
        html.append("                </div>\n");
        html.append("                <div class=\"content-box collapsed\" id=\"content-").append(id).append("\">\n");

        html.append("<strong>Headers:</strong>\n");
        record.request().headers().forEach((key, value) ->
            html.append(escapeHtml(key)).append(": ").append(escapeHtml(value)).append("\n")
        );

        if (record.request().body() != null && record.request().body().length > 0) {
            html.append("\n<strong>Body:</strong>\n");
            html.append(escapeHtml(bytesToString(record.request().body())));
        }

        html.append("                </div>\n");
        html.append("            </div>\n");
    }

    private void appendResponseComparison(StringBuilder html, ComparisonReport report, int index) {
        String id = "resp-" + index;
        html.append("            <div class=\"content-section\">\n");
        html.append("                <div class=\"content-title collapsed\" id=\"title-").append(id).append("\" onclick=\"toggleContent('").append(id).append("')\">\n");
        html.append("                    响应对比\n");
        html.append("                </div>\n");
        html.append("                <div class=\"content-box collapsed\" id=\"content-").append(id).append("\">\n");
        html.append("                    <div class=\"diff-container\">\n");

        // 录制的响应（期望）
        html.append("                        <div class=\"diff-side\">\n");
        html.append("                            <div class=\"diff-side-title expected\">录制的响应（期望）</div>\n");
        html.append("                            <div class=\"content-box\">\n");
        appendResponseContent(html, report.record().response());
        html.append("                            </div>\n");
        html.append("                        </div>\n");

        // 回放的响应（实际）
        html.append("                        <div class=\"diff-side\">\n");
        html.append("                            <div class=\"diff-side-title actual\">回放的响应（实际）</div>\n");
        html.append("                            <div class=\"content-box\">\n");
        if (report.replayedResponse() != null) {
            appendResponseContent(html, report.replayedResponse());
        } else {
            html.append("回放失败，无响应数据");
        }
        html.append("                            </div>\n");
        html.append("                        </div>\n");

        html.append("                    </div>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
    }

    private void appendResponseContent(StringBuilder html, com.flowreplay.core.model.ResponseData response) {
        html.append("<strong>Status:</strong> ").append(response.statusCode()).append("\n\n");
        html.append("<strong>Headers:</strong>\n");
        response.headers().forEach((key, value) ->
            html.append(escapeHtml(key)).append(": ").append(escapeHtml(value)).append("\n")
        );
        if (response.body() != null && response.body().length > 0) {
            html.append("\n<strong>Body:</strong>\n");
            html.append(escapeHtml(bytesToString(response.body())));
        }
    }

    private void appendIdeaDiffComparison(StringBuilder html, ComparisonReport report, int index) {
        String id = "diff-" + index;
        html.append("            <div class=\"content-section\">\n");
        html.append("                <div class=\"content-title collapsed\" id=\"title-").append(id).append("\" onclick=\"toggleContent('").append(id).append("')\">\n");
        html.append("                    差异详情 (IDEA 风格)\n");
        html.append("                </div>\n");
        html.append("                <div class=\"content-box collapsed\" id=\"content-").append(id).append("\" style=\"padding: 0; background: transparent; border: none;\">\n");

        String expected = formatResponse(report.record().response());
        String actual = formatResponse(report.replayedResponse());

        List<DiffLine> diffLines = generateDiff(expected, actual);
        appendIdeaDiffView(html, diffLines);

        html.append("                </div>\n");
        html.append("            </div>\n");
    }

    private String formatResponse(com.flowreplay.core.model.ResponseData response) {
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(response.statusCode()).append("\n");
        sb.append("\n");
        sb.append("Headers:\n");
        response.headers().forEach((key, value) ->
            sb.append(key).append(": ").append(value).append("\n")
        );
        sb.append("\n");
        sb.append("Body:\n");
        if (response.body() != null && response.body().length > 0) {
            sb.append(bytesToString(response.body()));
        }
        return sb.toString();
    }

    private List<DiffLine> generateDiff(String expected, String actual) {
        List<DiffLine> diffLines = new ArrayList<>();
        String[] expectedLines = expected.split("\n", -1);
        String[] actualLines = actual.split("\n", -1);

        int maxLen = Math.max(expectedLines.length, actualLines.length);
        for (int i = 0; i < maxLen; i++) {
            String expectedLine = i < expectedLines.length ? expectedLines[i] : null;
            String actualLine = i < actualLines.length ? actualLines[i] : null;

            if (expectedLine != null && actualLine != null) {
                if (expectedLine.equals(actualLine)) {
                    diffLines.add(new DiffLine(DiffType.UNCHANGED, i + 1, i + 1, expectedLine, actualLine));
                } else {
                    diffLines.add(new DiffLine(DiffType.CHANGED, i + 1, i + 1, expectedLine, actualLine));
                }
            } else if (expectedLine != null) {
                diffLines.add(new DiffLine(DiffType.REMOVED, i + 1, -1, expectedLine, ""));
            } else if (actualLine != null) {
                diffLines.add(new DiffLine(DiffType.ADDED, -1, i + 1, "", actualLine));
            }
        }

        return diffLines;
    }

    private void appendIdeaDiffView(StringBuilder html, List<DiffLine> diffLines) {
        html.append("                    <div class=\"idea-diff-container\">\n");

        // 左侧：期望响应
        html.append("                        <div class=\"idea-diff-side left\">\n");
        html.append("                            <div class=\"idea-diff-header expected\">期望响应</div>\n");
        for (DiffLine line : diffLines) {
            String cssClass = switch (line.type) {
                case REMOVED -> "removed";
                case CHANGED -> "changed";
                case UNCHANGED -> "unchanged";
                case ADDED -> "empty";
            };
            html.append("                            <div class=\"idea-diff-line ").append(cssClass).append("\">\n");
            html.append("                                <div class=\"idea-diff-line-number\">").append(line.oldLineNum > 0 ? line.oldLineNum : "").append("</div>\n");
            html.append("                                <div class=\"idea-diff-line-content\">").append(escapeHtml(line.expectedContent)).append("</div>\n");
            html.append("                            </div>\n");
        }
        html.append("                        </div>\n");

        // 右侧：实际响应
        html.append("                        <div class=\"idea-diff-side right\">\n");
        html.append("                            <div class=\"idea-diff-header actual\">实际响应</div>\n");
        for (DiffLine line : diffLines) {
            String cssClass = switch (line.type) {
                case ADDED -> "added";
                case CHANGED -> "changed";
                case UNCHANGED -> "unchanged";
                case REMOVED -> "empty";
            };
            html.append("                            <div class=\"idea-diff-line ").append(cssClass).append("\">\n");
            html.append("                                <div class=\"idea-diff-line-number\">").append(line.newLineNum > 0 ? line.newLineNum : "").append("</div>\n");
            html.append("                                <div class=\"idea-diff-line-content\">").append(escapeHtml(line.actualContent)).append("</div>\n");
            html.append("                            </div>\n");
        }
        html.append("                        </div>\n");

        html.append("                    </div>\n");
    }

    private String bytesToString(byte[] bytes) {
        if (bytes == null) return "";
        try {
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[Binary data, length: " + bytes.length + " bytes]";
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * 获取原始请求的耗时（从metadata中读取）
     */
    private long getOriginalDuration(TrafficRecord record) {
        Object duration = record.metadata().get("duration");
        if (duration instanceof Long) {
            return (Long) duration;
        } else if (duration instanceof Integer) {
            return ((Integer) duration).longValue();
        }
        return 0L;
    }

    /**
     * 格式化时间戳
     */
    private String formatTimestamp(java.time.Instant timestamp) {
        if (timestamp == null) {
            return "N/A";
        }
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME
            .format(timestamp.atZone(java.time.ZoneId.systemDefault()));
    }

    // 内部类：差异行
    private static class DiffLine {
        DiffType type;
        int oldLineNum;
        int newLineNum;
        String expectedContent;
        String actualContent;

        DiffLine(DiffType type, int oldLineNum, int newLineNum, String expectedContent, String actualContent) {
            this.type = type;
            this.oldLineNum = oldLineNum;
            this.newLineNum = newLineNum;
            this.expectedContent = expectedContent;
            this.actualContent = actualContent;
        }
    }

    // 内部枚举：差异类型
    private enum DiffType {
        ADDED,      // 新增行
        REMOVED,    // 删除行
        CHANGED,    // 修改行
        UNCHANGED   // 未改变行
    }
}
