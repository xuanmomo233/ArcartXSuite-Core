package xuanmo.arcartxsuite.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 模块元数据描述符。
 * <p>
 * 通常从模块 Jar 内的 {@code module.yml} 解析得到，
 * 也可由模块主类直接构造返回。
 */
public final class ModuleDescriptor {

    private final String id;
    private final String name;
    private final String version;
    private final String mainClass;
    private final List<String> depends;
    private final List<String> softDepends;
    private final List<String> externalDepends;
    private final List<String> externalSoftDepends;
    private final String signature;

    private ModuleDescriptor(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.name = builder.name != null ? builder.name : builder.id;
        this.version = builder.version != null ? builder.version : "1.0.0";
        this.mainClass = builder.mainClass != null ? builder.mainClass : "";
        this.depends = List.copyOf(builder.depends);
        this.softDepends = List.copyOf(builder.softDepends);
        this.externalDepends = List.copyOf(builder.externalDepends);
        this.externalSoftDepends = List.copyOf(builder.externalSoftDepends);
        this.signature = builder.signature;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    public String mainClass() {
        return mainClass;
    }

    /** 必须已加载的其他 AXS 模块 id 列表 */
    public List<String> depends() {
        return depends;
    }

    /** 可选增强的其他 AXS 模块 id 列表 */
    public List<String> softDepends() {
        return softDepends;
    }

    /** 必须已安装的外部 Bukkit 插件名 */
    public List<String> externalDepends() {
        return externalDepends;
    }

    /** 可选的外部 Bukkit 插件名 */
    public List<String> externalSoftDepends() {
        return externalSoftDepends;
    }

    /** Ed25519 Base64 数字签名（可为 null） */
    public String signature() {
        return signature;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private String name;
        private String version;
        private String mainClass;
        private List<String> depends = Collections.emptyList();
        private List<String> softDepends = Collections.emptyList();
        private List<String> externalDepends = Collections.emptyList();
        private List<String> externalSoftDepends = Collections.emptyList();
        private String signature;

        private Builder(String id) {
            this.id = id;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder mainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        public Builder depends(List<String> depends) {
            this.depends = depends != null ? depends : Collections.emptyList();
            return this;
        }

        public Builder softDepends(List<String> softDepends) {
            this.softDepends = softDepends != null ? softDepends : Collections.emptyList();
            return this;
        }

        public Builder externalDepends(List<String> externalDepends) {
            this.externalDepends = externalDepends != null ? externalDepends : Collections.emptyList();
            return this;
        }

        public Builder externalSoftDepends(List<String> externalSoftDepends) {
            this.externalSoftDepends = externalSoftDepends != null ? externalSoftDepends : Collections.emptyList();
            return this;
        }

        public Builder signature(String signature) {
            this.signature = signature;
            return this;
        }

        public ModuleDescriptor build() {
            return new ModuleDescriptor(this);
        }
    }
}
