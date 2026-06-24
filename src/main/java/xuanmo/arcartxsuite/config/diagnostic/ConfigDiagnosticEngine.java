package xuanmo.arcartxsuite.config.diagnostic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.config.ChangeSource;
import xuanmo.arcartxsuite.api.config.ConfigDiagnosisReport;
import xuanmo.arcartxsuite.api.config.ConfigIssue;
import xuanmo.arcartxsuite.api.config.ConfigIssueKind;
import xuanmo.arcartxsuite.api.config.ConfigIssueSeverity;
import xuanmo.arcartxsuite.api.config.ConfigMigrationDescriptor;
import xuanmo.arcartxsuite.api.config.ConfigSyncSpec;
import xuanmo.arcartxsuite.api.config.MigrationOperation;
import xuanmo.arcartxsuite.api.config.ModuleConfigSpec;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.config.YamlConfigSynchronizer;
import xuanmo.arcartxsuite.config.YamlConfigSynchronizer.MergeOutcome;

/**
 * 配置诊断引擎。
 * <p>
 * 对一份 {@link ModuleConfigSpec} 跑完整流程：版本检测 → migration（dry-run）→ 结构合并 → 类型/值校验，
 * 输出 {@link ConfigDiagnosisReport} 并把 dry-run 后的目标 yml 内容写到 proposal 文件。
 * 任何阶段都不会动玩家原 yml 文件。
 */
public final class ConfigDiagnosticEngine {

    private static final DateTimeFormatter SESSION_TS = DateTimeFormatter
        .ofPattern("yyyy-MM-dd_HH-mm-ss")
        .withZone(ZoneId.systemDefault());

    private final File pluginDataFolder;
    private final File diagnosisRoot;
    private final File backupRoot;
    private final String sessionTimestamp;
    private final MigrationLoader.ProtectedResourceOpener defaultsOpener;
    private final ClassLoader hostClassLoader;
    private final Logger logger;

    public ConfigDiagnosticEngine(
        File pluginDataFolder,
        Instant sessionStart,
        MigrationLoader.ProtectedResourceOpener defaultsOpener,
        ClassLoader hostClassLoader,
        Logger logger
    ) {
        this.pluginDataFolder = pluginDataFolder;
        this.sessionTimestamp = SESSION_TS.format(sessionStart);
        this.diagnosisRoot = new File(pluginDataFolder, "diagnosis/" + sessionTimestamp);
        this.backupRoot = new File(pluginDataFolder, "backup");
        this.defaultsOpener = defaultsOpener;
        this.hostClassLoader = hostClassLoader;
        this.logger = logger;
    }

    public String sessionTimestamp() {
        return sessionTimestamp;
    }

    public File diagnosisRoot() {
        return diagnosisRoot;
    }

    public File backupRoot() {
        return backupRoot;
    }

    /**
     * 对单个 spec 跑诊断（dry-run），返回报告。不写 proposal 与 markdown 文件。
     */
    public ConfigDiagnosisReport diagnose(ModuleConfigSpec spec, ClassLoader resourceClassLoader) {
        return diagnose(spec, resourceClassLoader, /*writeArtifacts=*/ false);
    }

    /**
     * 对单个 spec 跑诊断；{@code writeArtifacts=true} 时同步写出 proposal yml 与 per-spec markdown。
     */
    public ConfigDiagnosisReport diagnose(ModuleConfigSpec spec, ClassLoader resourceClassLoader, boolean writeArtifacts) {
        Instant timestamp = Instant.now();
        ClassLoader cl = resourceClassLoader == null ? hostClassLoader : resourceClassLoader;
        ConfigSyncSpec sync = spec.sync();
        File targetFile = new File(pluginDataFolder, sync.targetRelativePath().replace('/', File.separatorChar));

        List<ConfigIssue> issues = new ArrayList<>();

        // 1. 加载默认值流
        byte[] defaultsBytes;
        YamlConfiguration defaults;
        try (InputStream input = defaultsOpener.open(spec.ownerId(), sync.resourcePath(), cl)) {
            if (input == null) {
                issues.add(new ConfigIssue(
                    spec.ownerId(), sync.resourcePath(), "",
                    ConfigIssueKind.MIGRATION_FAILED, ConfigIssueSeverity.ERROR,
                    null, null,
                    "内置默认资源不存在: " + sync.resourcePath()
                ));
                return new ConfigDiagnosisReport(spec.ownerId(), timestamp, issues, null, null,
                    backupCandidate(spec, timestamp));
            }
            defaultsBytes = input.readAllBytes();
            defaults = YamlConfigSynchronizer.parseDefaults(sync.resourcePath(), defaultsBytes);
        } catch (IOException exception) {
            issues.add(new ConfigIssue(
                spec.ownerId(), sync.resourcePath(), "",
                ConfigIssueKind.MIGRATION_FAILED, ConfigIssueSeverity.ERROR,
                null, null,
                "读取内置默认资源失败: " + exception.getMessage()
            ));
            return new ConfigDiagnosisReport(spec.ownerId(), timestamp, issues, null, null,
                backupCandidate(spec, timestamp));
        }

        // 2. 加载 live 配置（可能不存在 → 视为空）
        YamlConfiguration live = new YamlConfiguration();
        live.options().parseComments(true);
        boolean liveExists = targetFile.isFile();
        if (liveExists) {
            try {
                live.load(targetFile);
            } catch (InvalidConfigurationException | IOException exception) {
                issues.add(new ConfigIssue(
                    spec.ownerId(), sync.resourcePath(), "",
                    ConfigIssueKind.MIGRATION_FAILED, ConfigIssueSeverity.ERROR,
                    null, null,
                    "现有 yml 解析失败: " + exception.getMessage()
                ));
                return new ConfigDiagnosisReport(spec.ownerId(), timestamp, issues, null, null,
                    backupCandidate(spec, timestamp));
            }
        }

        // 3. 文件不存在的情况：直接把默认值视作 proposal
        if (!liveExists) {
            issues.add(new ConfigIssue(
                spec.ownerId(), sync.resourcePath(), "",
                ConfigIssueKind.MISSING_DEFAULT, ConfigIssueSeverity.INFO,
                null, sync.resourcePath(),
                "目标文件不存在，将创建默认配置: " + targetFile.getAbsolutePath()
            ));
            Path proposed = writeArtifacts ? writeProposal(spec, defaultsBytes) : null;
            return new ConfigDiagnosisReport(
                spec.ownerId(), timestamp, issues,
                writeArtifacts ? writeMarkdown(spec, issues, proposed) : null,
                proposed,
                backupCandidate(spec, timestamp)
            );
        }

        // 4. 版本检测 + migration（在 live 副本上）
        int currentVersion = spec.currentVersion();
        int liveVersion = live.getInt(spec.versionPath(), 0);
        if (liveVersion < currentVersion && !spec.migrationFolder().isBlank()) {
            applyMigrations(spec, live, liveVersion, currentVersion, cl, issues);
            // 升级版本号
            live.set(spec.versionPath(), currentVersion);
        } else if (liveVersion < currentVersion) {
            // 没有迁移目录但版本落后 → 仅升 version 字段并提示
            issues.add(new ConfigIssue(
                spec.ownerId(), sync.resourcePath(), spec.versionPath(),
                ConfigIssueKind.VERSION_UPGRADE, ConfigIssueSeverity.INFO,
                liveVersion, currentVersion,
                "版本号将从 " + liveVersion + " 升级到 " + currentVersion + "（无 migration 文件，仅更新版本字段）"
            ));
            live.set(spec.versionPath(), currentVersion);
        }

        // 5. 结构合并 dry-run
        MergeOutcome outcome = YamlConfigSynchronizer.merge(live, defaults, sync.policy());
        for (String added : outcome.addedPaths()) {
            // jar 默认有但用户没有 → 这是 Jar 新增字段
            issues.add(new ConfigIssue(
                spec.ownerId(), sync.resourcePath(), added,
                ConfigIssueKind.MISSING_DEFAULT, ConfigIssueSeverity.INFO,
                null, defaults.get(added),
                "新增缺失的默认键",
                ChangeSource.JAR_NEW, defaults.get(added)
            ));
        }
        for (String removed : outcome.removedPaths()) {
            // 用户有但 jar 默认没有，且不在动态节 → 用户保留的废弃字段
            issues.add(new ConfigIssue(
                spec.ownerId(), sync.resourcePath(), removed,
                ConfigIssueKind.OBSOLETE_KEY, ConfigIssueSeverity.WARN,
                "(已删除)", null,
                "废弃键已被剪除",
                ChangeSource.USER_DEPRECATED, null
            ));
        }

        // 6. 校验规则（传入 defaults 用于来源识别）
        runValidations(spec, live, defaults, issues);

        // 7. 是否真的有 proposal（变更后 yml 与 live 不同）
        Path proposedFile = null;
        if (writeArtifacts && hasChanges(outcome, issues)) {
            proposedFile = writeProposalFromConfig(spec, live);
        }
        Path mdPath = writeArtifacts ? writeMarkdown(spec, issues, proposedFile) : null;

        return new ConfigDiagnosisReport(
            spec.ownerId(), timestamp, issues, mdPath, proposedFile, backupCandidate(spec, timestamp));
    }

    private boolean hasChanges(MergeOutcome outcome, List<ConfigIssue> issues) {
        if (!outcome.isEmpty()) {
            return true;
        }
        for (ConfigIssue issue : issues) {
            if (issue.kind() == ConfigIssueKind.VERSION_UPGRADE
                || issue.kind() == ConfigIssueKind.MISSING_DEFAULT
                || issue.kind() == ConfigIssueKind.OBSOLETE_KEY) {
                return true;
            }
        }
        return false;
    }

    private void applyMigrations(
        ModuleConfigSpec spec,
        YamlConfiguration live,
        int fromVersion,
        int toVersion,
        ClassLoader cl,
        List<ConfigIssue> issues
    ) {
        List<ConfigMigrationDescriptor> descriptors = MigrationLoader.loadAll(
            spec.ownerId(), spec.migrationFolder(), cl, defaultsOpener);
        int applied = 0;
        int currentVersion = fromVersion;
        for (ConfigMigrationDescriptor descriptor : descriptors) {
            if (descriptor.fromVersion() != currentVersion) {
                continue; // 不连续，跳过
            }
            for (MigrationOperation op : descriptor.operations()) {
                try {
                    boolean changed = MigrationOperationExecutor.execute(live, op);
                    if (changed) {
                        applied++;
                    }
                } catch (MigrationOperationExecutor.MigrationFailureException exception) {
                    issues.add(new ConfigIssue(
                        spec.ownerId(), spec.sync().resourcePath(), "",
                        ConfigIssueKind.MIGRATION_FAILED, ConfigIssueSeverity.ERROR,
                        op.toString(), null,
                        "迁移操作失败: " + exception.getMessage()
                    ));
                }
            }
            currentVersion = descriptor.toVersion();
            if (currentVersion >= toVersion) {
                break;
            }
        }
        issues.add(new ConfigIssue(
            spec.ownerId(), spec.sync().resourcePath(), spec.versionPath(),
            ConfigIssueKind.VERSION_UPGRADE,
            applied > 0 ? ConfigIssueSeverity.WARN : ConfigIssueSeverity.INFO,
            fromVersion, toVersion,
            "版本号从 " + fromVersion + " 升级到 " + toVersion + "（应用 " + applied + " 项迁移操作）"
        ));
    }

    private void runValidations(ModuleConfigSpec spec, YamlConfiguration live, YamlConfiguration defaults, List<ConfigIssue> issues) {
        for (ValidationRule rule : spec.validations()) {
            Object value = live.get(rule.path());
            Object defaultValue = defaults != null ? defaults.get(rule.path()) : null;
            // 通过对比默认值识别用户是否修改过该字段
            ChangeSource fieldSource = identifySource(value, defaultValue);

            if (value == null) {
                if (rule.required()) {
                    issues.add(new ConfigIssue(
                        spec.ownerId(), spec.sync().resourcePath(), rule.path(),
                        ConfigIssueKind.MISSING_DEFAULT, ConfigIssueSeverity.ERROR,
                        null, null,
                        "必填字段缺失",
                        ChangeSource.JAR_NEW, defaultValue
                    ));
                }
                continue;
            }
            if (!TypeCoercer.matches(value, rule.type())) {
                var coerced = TypeCoercer.coerce(value, rule.type());
                if (coerced.isPresent()) {
                    live.set(rule.path(), coerced.get());
                    issues.add(new ConfigIssue(
                        spec.ownerId(), spec.sync().resourcePath(), rule.path(),
                        ConfigIssueKind.TYPE_MISMATCH, ConfigIssueSeverity.WARN,
                        value, coerced.get(),
                        "类型 " + describe(value) + " 已转换为 " + rule.type(),
                        fieldSource, defaultValue
                    ));
                    value = coerced.get();
                } else {
                    issues.add(new ConfigIssue(
                        spec.ownerId(), spec.sync().resourcePath(), rule.path(),
                        ConfigIssueKind.TYPE_MISMATCH, ConfigIssueSeverity.ERROR,
                        value, null,
                        "类型 " + describe(value) + " 无法转换为 " + rule.type(),
                        fieldSource, defaultValue
                    ));
                    continue;
                }
            }
            // 范围
            if (value instanceof Number n && (rule.min() != null || rule.max() != null)) {
                double dv = n.doubleValue();
                if (rule.min() != null && dv < rule.min().doubleValue()) {
                    issues.add(new ConfigIssue(
                        spec.ownerId(), spec.sync().resourcePath(), rule.path(),
                        ConfigIssueKind.VALUE_OUT_OF_RANGE, ConfigIssueSeverity.WARN,
                        value, rule.min(),
                        "值 " + value + " 小于下限 " + rule.min(),
                        fieldSource, defaultValue
                    ));
                }
                if (rule.max() != null && dv > rule.max().doubleValue()) {
                    issues.add(new ConfigIssue(
                        spec.ownerId(), spec.sync().resourcePath(), rule.path(),
                        ConfigIssueKind.VALUE_OUT_OF_RANGE, ConfigIssueSeverity.WARN,
                        value, rule.max(),
                        "值 " + value + " 超过上限 " + rule.max(),
                        fieldSource, defaultValue
                    ));
                }
            }
            // 枚举
            Set<String> allowed = rule.allowedValues();
            if (allowed != null && value instanceof String s && !allowed.contains(s)) {
                issues.add(new ConfigIssue(
                    spec.ownerId(), spec.sync().resourcePath(), rule.path(),
                    ConfigIssueKind.VALUE_NOT_IN_ENUM, ConfigIssueSeverity.ERROR,
                    s, null,
                    "值 \"" + s + "\" 不在允许集合 " + allowed,
                    fieldSource, defaultValue
                ));
            }
        }
    }

    /**
     * 根据当前值与默认值的对比，识别字段来源。
     * <ul>
     *   <li>默认值不存在 → USER_DYNAMIC（仅在 live 端，且通过校验规则进入）</li>
     *   <li>当前值与默认值相等 → JAR_DEFAULT</li>
     *   <li>当前值与默认值不等 → USER_MODIFIED</li>
     * </ul>
     */
    private ChangeSource identifySource(Object current, Object defaultValue) {
        if (defaultValue == null && current == null) {
            return ChangeSource.UNKNOWN;
        }
        if (defaultValue == null) {
            return ChangeSource.USER_DYNAMIC;
        }
        if (current == null) {
            return ChangeSource.JAR_NEW;
        }
        return java.util.Objects.equals(current, defaultValue)
            ? ChangeSource.JAR_DEFAULT
            : ChangeSource.USER_MODIFIED;
    }

    private String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof ConfigurationSection) {
            return "section";
        }
        return value.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    private Path backupCandidate(ModuleConfigSpec spec, Instant timestamp) {
        return new File(backupRoot,
            SESSION_TS.format(timestamp) + "/" + spec.sync().targetRelativePath()).toPath();
    }

    private Path writeProposal(ModuleConfigSpec spec, byte[] yamlBytes) {
        File proposed = new File(diagnosisRoot,
            "proposals/" + spec.sync().targetRelativePath().replace('/', File.separatorChar));
        try {
            Files.createDirectories(proposed.getParentFile().toPath());
            Files.write(proposed.toPath(), yamlBytes);
            return proposed.toPath();
        } catch (IOException exception) {
            logger.warning("写入 proposal 失败 " + proposed + ": " + exception.getMessage());
            return null;
        }
    }

    private Path writeProposalFromConfig(ModuleConfigSpec spec, YamlConfiguration mergedConfig) {
        return writeProposal(spec, mergedConfig.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    private Path writeMarkdown(ModuleConfigSpec spec, List<ConfigIssue> issues, Path proposedFile) {
        File md = new File(diagnosisRoot, spec.ownerId() + ".md");
        try {
            Files.createDirectories(md.getParentFile().toPath());
            Files.writeString(md.toPath(),
                MarkdownReportWriter.renderSpec(spec, issues, proposedFile, sessionTimestamp),
                StandardCharsets.UTF_8);
            return md.toPath();
        } catch (IOException exception) {
            logger.warning("写入诊断 markdown 失败 " + md + ": " + exception.getMessage());
            return null;
        }
    }

    /** 写汇总 markdown。 */
    public Path writeSummary(List<ConfigDiagnosisReport> reports) {
        File summary = new File(diagnosisRoot, "summary.md");
        try {
            Files.createDirectories(summary.getParentFile().toPath());
            Files.writeString(summary.toPath(),
                MarkdownReportWriter.renderSummary(reports, sessionTimestamp),
                StandardCharsets.UTF_8);
            return summary.toPath();
        } catch (IOException exception) {
            logger.warning("写入诊断 summary 失败 " + summary + ": " + exception.getMessage());
            return null;
        }
    }

    /** 给定一份报告，把 proposal 写到目标 yml，原文件备份到 backup/<ts>/。 */
    public ApplyResult apply(ModuleConfigSpec spec, ConfigDiagnosisReport report, boolean force) {
        if (report == null || report.proposedFile() == null) {
            return new ApplyResult(false, "无可应用的 proposal", null);
        }
        if (report.hasError() && !force) {
            return new ApplyResult(false,
                "存在 " + report.countOf(ConfigIssueSeverity.ERROR) + " 个 ERROR 级 issue，需要 --force 才能应用。",
                null);
        }
        File targetFile = new File(pluginDataFolder, spec.sync().targetRelativePath().replace('/', File.separatorChar));
        File backupTs = new File(backupRoot, SESSION_TS.format(Instant.now()));
        File backupFile = new File(backupTs, spec.sync().targetRelativePath().replace('/', File.separatorChar));
        try {
            if (targetFile.isFile()) {
                Files.createDirectories(backupFile.getParentFile().toPath());
                Files.copy(targetFile.toPath(), backupFile.toPath());
            }
            Files.createDirectories(targetFile.getParentFile().toPath());
            Files.copy(report.proposedFile(), targetFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return new ApplyResult(true, "已应用，备份 " + backupFile, backupFile.toPath());
        } catch (IOException exception) {
            return new ApplyResult(false, "应用失败: " + exception.getMessage(), null);
        }
    }

    /** 用最近一次（或指定时间戳的）备份恢复目标 yml。 */
    public ApplyResult rollback(ModuleConfigSpec spec, String timestamp) {
        File targetFile = new File(pluginDataFolder, spec.sync().targetRelativePath().replace('/', File.separatorChar));
        File backupTs;
        if (timestamp != null && !timestamp.isBlank()) {
            backupTs = new File(backupRoot, timestamp);
        } else {
            backupTs = latestBackup();
            if (backupTs == null) {
                return new ApplyResult(false, "未找到任何备份。", null);
            }
        }
        File backupFile = new File(backupTs, spec.sync().targetRelativePath().replace('/', File.separatorChar));
        if (!backupFile.isFile()) {
            return new ApplyResult(false, "备份不存在: " + backupFile, null);
        }
        try {
            Files.createDirectories(targetFile.getParentFile().toPath());
            Files.copy(backupFile.toPath(), targetFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return new ApplyResult(true, "已从 " + backupTs.getName() + " 还原。", backupFile.toPath());
        } catch (IOException exception) {
            return new ApplyResult(false, "还原失败: " + exception.getMessage(), null);
        }
    }

    private File latestBackup() {
        if (!backupRoot.isDirectory()) {
            return null;
        }
        File[] children = backupRoot.listFiles(File::isDirectory);
        if (children == null || children.length == 0) {
            return null;
        }
        File latest = children[0];
        for (File f : children) {
            if (f.getName().compareTo(latest.getName()) > 0) {
                latest = f;
            }
        }
        return latest;
    }

    /** apply / rollback 操作结果。 */
    public record ApplyResult(boolean success, String message, Path artifact) {
    }
}
