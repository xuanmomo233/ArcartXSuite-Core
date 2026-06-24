package xuanmo.arcartxsuite.config.diagnostic;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import xuanmo.arcartxsuite.api.config.ChangeSource;
import xuanmo.arcartxsuite.api.config.ConfigDiagnosisReport;
import xuanmo.arcartxsuite.api.config.ConfigIssue;
import xuanmo.arcartxsuite.api.config.ConfigIssueKind;
import xuanmo.arcartxsuite.api.config.ConfigIssueSeverity;
import xuanmo.arcartxsuite.api.config.ModuleConfigSpec;

/**
 * 把诊断结果渲染为 Markdown。
 * <p>
 * 优化编辑器阅读体验：
 * <ul>
 *   <li>快速导航目录</li>
 *   <li>折叠区块组织内容</li>
 *   <li>代码块展示修复命令</li>
 *   <li>清晰的视觉分隔</li>
 * </ul>
 */
final class MarkdownReportWriter {

    private static final int MAX_TABLE_WIDTH = 100;

    private MarkdownReportWriter() {
    }

    static String renderSummary(List<ConfigDiagnosisReport> reports, String sessionTimestamp) {
        StringBuilder sb = new StringBuilder();

        // 头部
        sb.append("# 🔧 AXS 配置诊断报告\n\n");
        sb.append("<!-- 使用 Ctrl+F 搜索模块名快速定位 -->\n");
        sb.append("<!-- 本文件可直接在 VSCode/IDEA 中阅读 -->\n\n");

        // 摘要区块
        sb.append("---\n\n");
        sb.append("## 📊 摘要\n\n");
        sb.append("| 项目 | 值 |\n");
        sb.append("|:---|:---|\n");
        sb.append("| 会话 | `").append(sessionTimestamp).append("` |\n");
        sb.append("| 诊断模块数 | `").append(reports.size()).append("` |\n");

        long info = reports.stream().mapToLong(r -> r.countOf(ConfigIssueSeverity.INFO)).sum();
        long warn = reports.stream().mapToLong(r -> r.countOf(ConfigIssueSeverity.WARN)).sum();
        long error = reports.stream().mapToLong(r -> r.countOf(ConfigIssueSeverity.ERROR)).sum();
        sb.append("| 状态 | ").append(error > 0 ? "❌ 需修复" : (warn > 0 ? "⚠️ 需关注" : "✅ 健康")).append(" |\n");
        sb.append("\n");

        // 统计卡片
        sb.append("```\n");
        sb.append("┌─────────┬─────────┬─────────┐\n");
        sb.append(String.format("│  %-5s  │  %-5s  │  %-5s  │%n", error, warn, info));
        sb.append("│  ERROR  │  WARN   │  INFO   │\n");
        sb.append("└─────────┴─────────┴─────────┘\n");
        sb.append("```\n\n");

        // 变更来源统计
        Map<ChangeSource, Long> sourceStats = reports.stream()
            .flatMap(r -> r.issues().stream())
            .filter(i -> i.source() != null && i.source() != ChangeSource.UNKNOWN)
            .collect(Collectors.groupingBy(ConfigIssue::source, Collectors.counting()));

        if (!sourceStats.isEmpty()) {
            sb.append("### 📈 变更来源统计\n\n");
            sourceStats.entrySet().stream()
                .sorted(Map.Entry.<ChangeSource, Long>comparingByValue().reversed())
                .forEach(e -> {
                    String bar = "█".repeat(Math.min(20, e.getValue().intValue()));
                    sb.append(String.format("%-12s %s %d%n", 
                        e.getKey().icon() + " " + e.getKey().description(), 
                        bar, e.getValue()));
                });
            sb.append("\n");
        }

        // 快速命令
        sb.append("---\n\n");
        sb.append("## ⚡ 快速操作\n\n");
        sb.append("```bash\n");
        sb.append("# 查看某个模块详情\n");
        sb.append("/axs config preview <module>\n\n");
        sb.append("# 应用所有修复（会备份）\n");
        sb.append("/axs config apply <module>\n\n");
        sb.append("# 回滚到修复前\n");
        sb.append("/axs config rollback <module>\n");
        sb.append("```\n\n");

        // 模块列表
        sb.append("---\n\n");
        sb.append("## 📋 模块列表\n\n");
        sb.append("| 模块 | 🟢 INFO | 🟡 WARN | 🔴 ERROR | 报告 | 操作 |\n");
        sb.append("|:---|:---:|:---:|:---:|:---|:---|\n");
        for (ConfigDiagnosisReport r : reports) {
            long e = r.countOf(ConfigIssueSeverity.ERROR);
            long w = r.countOf(ConfigIssueSeverity.WARN);
            long i = r.countOf(ConfigIssueSeverity.INFO);

            String status = e > 0 ? "🔴" : (w > 0 ? "🟡" : "🟢");
            String link = r.markdownPath() == null ? "-" : "[📄](" + relativeName(r.markdownPath()) + ")";
            String cmd = "`preview " + r.ownerId() + "`";

            sb.append("| ").append(status).append(" `").append(r.ownerId()).append("` | ")
                .append(i).append(" | ")
                .append(w).append(" | ")
                .append(e).append(" | ")
                .append(link).append(" | ")
                .append(cmd).append(" |\n");
        }
        sb.append("\n");

        // 说明
        sb.append("---\n\n");
        sb.append("## 📝 图例说明\n\n");
        sb.append("- 🔴 ERROR：必须修复，可能导致功能异常\n");
        sb.append("- 🟡 WARN：建议修复，可能影响体验\n");
        sb.append("- 🟢 INFO：信息提示，无实质影响\n");
        sb.append("- 📄：点击打开详细诊断报告\n\n");

        return sb.toString();
    }

    static String renderSpec(ModuleConfigSpec spec, List<ConfigIssue> issues,
                              Path proposedFile, String sessionTimestamp) {
        StringBuilder sb = new StringBuilder();

        // 头部
        sb.append("# 🔍 `").append(spec.ownerId()).append("` 配置诊断\n\n");
        sb.append("<!-- 本文件由 AXS 智能诊断引擎自动生成 -->\n");
        sb.append("<!-- 生成时间: ").append(sessionTimestamp).append(" -->\n\n");

        // 元信息区块
        sb.append("---\n\n");
        sb.append("## 📋 元信息\n\n");
        sb.append("```yaml\n");
        sb.append("资源路径: ").append(spec.sync().resourcePath()).append("\n");
        sb.append("目标路径: ").append(spec.sync().targetRelativePath()).append("\n");
        sb.append("配置版本: ").append(spec.currentVersion()).append("\n");
        sb.append("修复提案: ").append(proposedFile == null ? "无" : proposedFile.toString()).append("\n");
        sb.append("```\n\n");

        if (issues.isEmpty()) {
            sb.append("---\n\n");
            sb.append("## ✅ 诊断结果\n\n");
            sb.append("```\n");
            sb.append("┌──────────────────────────────┐\n");
            sb.append("│  ✓ 配置健康，无发现问题      │\n");
            sb.append("└──────────────────────────────┘\n");
            sb.append("```\n\n");
            return sb.toString();
        }

        // 问题统计
        long errors = issues.stream().filter(i -> i.severity() == ConfigIssueSeverity.ERROR).count();
        long warns = issues.stream().filter(i -> i.severity() == ConfigIssueSeverity.WARN).count();
        long infos = issues.stream().filter(i -> i.severity() == ConfigIssueSeverity.INFO).count();

        sb.append("---\n\n");
        sb.append("## 📊 问题统计\n\n");
        sb.append(String.format("- 🔴 ERROR: %d 个（需立即修复）%n", errors));
        sb.append(String.format("- 🟡 WARN: %d 个（建议处理）%n", warns));
        sb.append(String.format("- 🟢 INFO: %d 个（仅供参考）%n", infos));
        sb.append("\n");

        // 分组问题
        Map<ConfigIssueSeverity, List<ConfigIssue>> bucket = new EnumMap<>(ConfigIssueSeverity.class);
        for (ConfigIssue issue : issues) {
            bucket.computeIfAbsent(issue.severity(), k -> new java.util.ArrayList<>()).add(issue);
        }

        // 快速修复命令
        if (errors > 0 || warns > 0) {
            sb.append("---\n\n");
            sb.append("## ⚡ 快速修复\n\n");
            sb.append("```bash\n");
            sb.append("# 一键修复本模块所有问题（自动备份）\n");
            sb.append("/axs config apply ").append(spec.ownerId()).append("\n\n");
            sb.append("# 或先预览修复方案\n");
            sb.append("/axs config preview ").append(spec.ownerId()).append("\n");
            sb.append("```\n\n");
        }

        // 详细问题列表
        sb.append("---\n\n");
        sb.append("## 🔍 详细问题\n\n");

        renderIssuesEnhanced(sb, "🔴 ERROR", bucket.get(ConfigIssueSeverity.ERROR));
        renderIssuesEnhanced(sb, "🟡 WARN", bucket.get(ConfigIssueSeverity.WARN));
        renderIssuesEnhanced(sb, "🟢 INFO", bucket.get(ConfigIssueSeverity.INFO));

        return sb.toString();
    }

    private static void renderIssuesEnhanced(StringBuilder sb, String header, List<ConfigIssue> list) {
        if (list == null || list.isEmpty()) {
            return;
        }

        sb.append("<details open>\n\n");
        sb.append("<summary><b>").append(header).append(" (").append(list.size()).append(")</b></summary>\n\n");

        int index = 1;
        for (ConfigIssue issue : list) {
            sb.append("### ").append(index++).append(". ").append(formatPath(issue.configPath())).append("\n\n");

            // 来源和类型
            sb.append("- **来源**: ").append(sourceLabel(issue.source())).append("\n");
            sb.append("- **类型**: ").append(kindLabel(issue.kind())).append("\n");

            // 值对比
            if (issue.currentValue() != null) {
                sb.append("- **当前值**: `").append(escapeInline(issue.currentValue())).append("`\n");
            }
            if (issue.defaultValue() != null) {
                sb.append("- **默认值**: `").append(escapeInline(issue.defaultValue())).append("`\n");
            }
            if (issue.suggestedValue() != null) {
                sb.append("- **建议值**: `").append(escapeInline(issue.suggestedValue())).append("` ⚡\n");
            }

            // 说明
            sb.append("- **说明**: ").append(issue.message()).append("\n");

            sb.append("\n---\n\n");
        }

        sb.append("</details>\n\n");
    }

    private static String formatPath(String path) {
        if (path == null || path.isEmpty()) {
            return "(根)";
        }
        // 将路径分段显示
        return path.replace(".", " › ");
    }

    private static String sourceLabel(ChangeSource source) {
        if (source == null) return "❓ 未知";
        return source.icon() + " " + source.description();
    }

    private static String kindLabel(ConfigIssueKind kind) {
        return switch (kind) {
            case MISSING_DEFAULT -> "📦 缺失默认值";
            case OBSOLETE_KEY -> "🗑️ 废弃键";
            case TYPE_MISMATCH -> "🔧 类型不符";
            case VALUE_OUT_OF_RANGE -> "📏 超出范围";
            case VALUE_NOT_IN_ENUM -> "📋 不在枚举";
            case VERSION_UPGRADE -> "⬆️ 版本升级";
            case MIGRATION_FAILED -> "❌ 迁移失败";
        };
    }

    private static String escapeInline(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        if (s.length() > 80) {
            s = s.substring(0, 77) + "...";
        }
        return s.replace("`", "\\`").replace("\n", " ");
    }

    private static String relativeName(Path path) {
        return path.getFileName().toString();
    }
}
