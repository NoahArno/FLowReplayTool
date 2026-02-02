package com.flowreplay.core.report;

import com.flowreplay.core.model.ComparisonResult;
import com.flowreplay.core.model.Difference;
import com.flowreplay.core.model.TrafficRecord;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * HTML差异报告生成器
 */
public class HtmlReportGenerator {

    public void generateReport(List<ComparisonReport> reports, String outputPath) throws IOException {
        StringBuilder html = new StringBuilder();

        // HTML头部
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

        // 标题和统计信息
        appendHeader(html, reports);
        appendSummary(html, reports);

        // 详细差异列表
        appendDetailedReports(html, reports);

        // HTML尾部
        html.append("</body>\n");
        html.append("</html>\n");

        // 写入文件
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
        html.append("        .diff { background: #fff3cd; padding: 10px; margin: 5px 0; border-radius: 3px; }\n");
        html.append("        .diff-path { font-weight: bold; color: #856404; }\n");
        html.append("        .diff-value { font-family: monospace; white-space: pre-wrap; word-break: break-all; }\n");
        html.append("        .content-section { margin: 15px 0; }\n");
        html.append("        .content-title { font-weight: bold; margin: 10px 0 5px 0; color: #333; cursor: pointer; user-select: none; }\n");
        html.append("        .content-title:hover { color: #2196F3; }\n");
        html.append("        .content-title::before { content: '▼ '; display: inline-block; transition: transform 0.2s; }\n");
        html.append("        .content-title.collapsed::before { transform: rotate(-90deg); }\n");
        html.append("        .content-box { background: #f8f9fa; padding: 10px; border-radius: 3px; font-family: monospace; white-space: pre-wrap; word-break: break-all; max-height: 300px; overflow: auto; border: 1px solid #ddd; }\n");
        html.append("        .content-box.collapsed { display: none; }\n");
        html.append("        .diff-container { display: flex; gap: 10px; margin: 10px 0; }\n");
        html.append("        .diff-side { flex: 1; }\n");
        html.append("        .diff-side-title { font-weight: bold; margin-bottom: 5px; padding: 5px; background: #e9ecef; border-radius: 3px; }\n");
        html.append("        .diff-side-title.expected { background: #d4edda; color: #155724; }\n");
        html.append("        .diff-side-title.actual { background: #f8d7da; color: #721c24; }\n");
        html.append("        .diff-line { font-family: monospace; white-space: pre-wrap; word-break: break-all; padding: 2px 5px; }\n");
        html.append("        .diff-line.added { background: #d4edda; }\n");
        html.append("        .diff-line.removed { background: #f8d7da; }\n");
        html.append("        .diff-line.changed { background: #fff3cd; }\n");
        html.append("        .toggle-all { margin: 10px 0; }\n");
        html.append("        .toggle-all button { padding: 8px 16px; margin-right: 10px; cursor: pointer; border: none; border-radius: 3px; background: #2196F3; color: white; }\n");
        html.append("        .toggle-all button:hover { background: #1976D2; }\n");
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
        html.append("        <h2>统计摘要</h2>\n");
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

        html.append("        <div class=\"report-item ").append(cssClass).append("\">\n");
        html.append("            <h3>请求 #").append(index + 1).append(" - ").append(escapeHtml(record.id())).append("</h3>\n");
        html.append("            <p><strong>协议:</strong> ").append(record.protocol()).append("</p>\n");
        html.append("            <p><strong>URI:</strong> ").append(escapeHtml(record.request().uri())).append("</p>\n");
        html.append("            <p><strong>方法:</strong> ").append(record.request().method()).append("</p>\n");
        html.append("            <p><strong>状态:</strong> <span class=\"").append(cssClass).append("\">")
                   .append(report.result().matched() ? "✓ 匹配" : "✗ 不匹配").append("</span></p>\n");

        // 显示原始请求
        appendRequestDetails(html, record, index);

        // 显示原始响应对比
        appendResponseComparison(html, report, index);

        // 显示差异详情
        if (!report.result().differences().isEmpty()) {
            appendDifferences(html, report.result().differences());
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

        // 请求头
        html.append("<strong>Headers:</strong>\n");
        record.request().headers().forEach((key, value) ->
            html.append(escapeHtml(key)).append(": ").append(escapeHtml(value)).append("\n")
        );

        // 请求体
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
        // 状态码
        html.append("<strong>Status:</strong> ").append(response.statusCode()).append("\n\n");

        // 响应头
        html.append("<strong>Headers:</strong>\n");
        response.headers().forEach((key, value) ->
            html.append(escapeHtml(key)).append(": ").append(escapeHtml(value)).append("\n")
        );

        // 响应体
        if (response.body() != null && response.body().length > 0) {
            html.append("\n<strong>Body:</strong>\n");
            html.append(escapeHtml(bytesToString(response.body())));
        }
    }

    private void appendDifferences(StringBuilder html, List<Difference> differences) {
        html.append("            <h4>差异详情:</h4>\n");
        for (Difference diff : differences) {
            html.append("            <div class=\"diff\">\n");
            html.append("                <div class=\"diff-path\">路径: ").append(escapeHtml(diff.path())).append("</div>\n");
            html.append("                <div class=\"diff-path\">类型: ").append(escapeHtml(diff.type())).append("</div>\n");
            html.append("                <div class=\"diff-value\">期望: ").append(escapeHtml(diff.expected())).append("</div>\n");
            html.append("                <div class=\"diff-value\">实际: ").append(escapeHtml(diff.actual())).append("</div>\n");
            html.append("            </div>\n");
        }
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
}
