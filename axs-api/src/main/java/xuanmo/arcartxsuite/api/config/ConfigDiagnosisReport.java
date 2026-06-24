package xuanmo.arcartxsuite.api.config;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 一次配置诊断的完整结果。
 *
 * @param ownerId               归属 spec 的 ownerId
 * @param timestamp             诊断时间
 * @param issues                所有 issue（含 INFO/WARN/ERROR）
 * @param markdownPath          per-spec 详情报告 md 文件，可空
 * @param proposedFile          dry-run 后建议替换的 yml 文件路径，可空（无修改时为 null）
 * @param backupCandidatePath   apply 时建议写入的备份目录（不一定存在）
 */
public record ConfigDiagnosisReport(
    String ownerId,
    Instant timestamp,
    List<ConfigIssue> issues,
    Path markdownPath,
    Path proposedFile,
    Path backupCandidatePath
) {

    public ConfigDiagnosisReport {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public long countOf(ConfigIssueSeverity severity) {
        return issues.stream().filter(i -> i.severity() == severity).count();
    }

    public boolean hasError() {
        return countOf(ConfigIssueSeverity.ERROR) > 0;
    }

    public boolean hasAnyIssue() {
        return !issues.isEmpty();
    }

    public boolean hasProposal() {
        return proposedFile != null;
    }
}
