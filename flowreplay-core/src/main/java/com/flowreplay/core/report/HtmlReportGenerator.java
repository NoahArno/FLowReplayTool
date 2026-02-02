package com.flowreplay.core.report;

import com.flowreplay.core.model.ComparisonResult;
import com.flowreplay.core.model.Difference;
import com.flowreplay.core.model.ReplayResult;
import com.flowreplay.core.model.TrafficRecord;

import java.io.FileWriter;
import java.io.IOException;
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
        html.append("        .diff-value { font-family: monospace; }\n");
        html.append("    </style>\n");
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
        
        for (int i = 0; i < reports.size(); i++) {
            ComparisonReport report = reports.get(i);
            String cssClass = report.result().matched() ? "matched" : "mismatched";
            
            html.append("        <div class=\"report-item ").append(cssClass).append("\">\n");
            html.append("            <h3>请求 #").append(i + 1).append(" - ").append(report.record().id()).append("</h3>\n");
            html.append("            <p><strong>URI:</strong> ").append(escapeHtml(report.record().request().uri())).append("</p>\n");
            html.append("            <p><strong>方法:</strong> ").append(report.record().request().method()).append("</p>\n");
            html.append("            <p><strong>状态:</strong> <span class=\"").append(cssClass).append("\">")
                       .append(report.result().matched() ? "✓ 匹配" : "✗ 不匹配").append("</span></p>\n");
            
            if (!report.result().differences().isEmpty()) {
                html.append("            <h4>差异详情:</h4>\n");
                for (Difference diff : report.result().differences()) {
                    html.append("            <div class=\"diff\">\n");
                    html.append("                <div class=\"diff-path\">路径: ").append(escapeHtml(diff.path())).append("</div>\n");
                    html.append("                <div class=\"diff-value\">期望: ").append(escapeHtml(diff.expected())).append("</div>\n");
                    html.append("                <div class=\"diff-value\">实际: ").append(escapeHtml(diff.actual())).append("</div>\n");
                    html.append("            </div>\n");
                }
            }
            
            html.append("        </div>\n");
        }
        
        html.append("    </div>\n");
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
