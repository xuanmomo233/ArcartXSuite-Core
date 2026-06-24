# ArcartXSuite 模块开发指南

本文档面向第三方开发者，介绍如何基于 ArcartXSuite（AXS）宿主编写自定义模块。

---

## 前置条件

- 已安装 AXS 宿主插件（`ArcartXSuite.jar`）并正确配置
- **玩家客户端已安装 ArcartX 模组**（AXS 的所有 UI/HUD/Packet 功能依赖此客户端模组）
- Java 17+
- Gradle（推荐）或 Maven

> **关于 ArcartX 客户端模组：** 模块开发者在服务器端编写 Java 代码，但所有 UI 交互（如打开菜单、发送 HUD、播放粒子、显示伤害飘字等）都通过 ArcartX 客户端模组在玩家本地渲染。请确保目标玩家群体已安装对应版本的 ArcartX 模组。

---

## 第一步：创建 Gradle 模块项目

完整目录结构：

```
MyAXSModule/
├── build.gradle.kts
├── settings.gradle.kts
└── src/main/
    ├── java/com/example/
    │   ├── MyModule.java
    │   ├── MyListener.java
    │   └── MyPacketHandler.java
    └── resources/
        ├── module.yml
        ├── arcartx/
        │   └── ui/
        │       └── my_ui.yml
        └── MyModule.yml
```

### build.gradle.kts

```kotlin
plugins {
    id("java")
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    // AXS 公共 API（从 GitHub Releases 下载预编译 JAR）
    compileOnly(files("libs/axs-api-x.x.x.jar"))

    // Bukkit API
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")

    // 可选：PlaceholderAPI
    compileOnly("me.clip:placeholderapi:2.11.7")
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.jar {
    archiveBaseName.set("MyAXSModule")
}
```

---

## 第二步：编写 module.yml

每个模块必须包含 `module.yml`，放在 `src/main/resources/` 下：

```yaml
id: mymodule
name: MyModule
version: 1.0.0
main: com.example.MyModule
api-version: 1.0
depends: []
softdepends: []
external-depends: []
external-softdepends: []
```

| 字段 | 说明 |
|------|------|
| `id` | 模块唯一标识（字母、数字、下划线、连字符）|
| `name` | 显示名称 |
| `version` | 版本号 |
| `main` | 模块入口类全限定名 |
| `api-version` | 兼容的 AXS API 版本 |
| `depends` | 硬依赖的其他模块 ID（未加载则本模块拒绝启动）|
| `softdepends` | 软依赖的其他模块 ID（未加载则跳过，不报错）|

---

## 第三步：实现模块入口类

### 极简模式：直接实现 AXSModule

适用于无配置、无 UI、无监听器的极简功能模块。

```java
package com.example;

import xuanmo.arcartxsuite.api.AXSModule;
import xuanmo.arcartxsuite.api.ModuleContext;
import xuanmo.arcartxsuite.api.ModuleDescriptor;

public final class MyModule implements AXSModule {

    private ModuleContext context;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("mymodule")
            .name("MyModule")
            .version("1.0.0")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    public boolean onEnable(ModuleContext context) throws Exception {
        this.context = context;
        context.logger().info("MyModule 已启用！");
        return true;
    }

    @Override
    public void onDisable() {
        context.logger().info("MyModule 已关闭。");
    }

    @Override
    public void onReload() throws Exception {
        onDisable();
        if (context != null) onEnable(context);
    }

    @Override
    public boolean isReady() {
        return true;
    }
}
```

### 推荐模式：继承 AbstractAXSModule

`AbstractAXSModule` 封装了配置导出、UI 绑定、监听器注册、命令注册、PAPI 扩展等通用逻辑。只需覆写声明式方法和抽象方法即可。

```java
package com.example;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.bukkit.command.TabExecutor;
import org.bukkit.event.Listener;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ModuleDescriptor;

public final class MyModule extends AbstractAXSModule {

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("mymodule")
            .name("MyModule")
            .version("1.0.0")
            .mainClass(getClass().getName())
            .build();
    }

    // ── 声明式配置 ──

    @Override
    protected String configFileName() {
        return "MyModule.yml"; // 会从模块 Jar 自动导出到 plugins/ArcartXSuite/data/mymodule/config.yml
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml"; // 外部化消息文件，支持 & 颜色码和 {0} 占位符
    }

    @Override
    protected Map<String, String> uiResourceMappings() {
        return Map.of(
            "arcartx/ui/my_ui.yml", "ui/my_ui.yml"
        );
    }

    @Override
    protected List<Listener> createListeners() {
        return List.of(new MyListener(context));
    }

    @Override
    protected Map<String, TabExecutor> commandBindings() {
        return Map.of("mycmd", new MyCommand(this));
    }

    // ── 抽象方法实现 ──

    @Override
    protected void loadConfiguration(File configFile) {
        // 读取你的配置文件（configFile 已确保存在）
    }

    @Override
    protected void startService() {
        // 注册 ArcartX UI
        bindUi("my_ui", "ui/my_ui.yml");

        context.logger().info("MyModule 服务已启动");
    }

    @Override
    protected void stopService() {
        // AbstractAXSModule 会自动清理监听器、命令、占位符、UI 等
        context.logger().info("MyModule 服务已停止");
    }
}
```

---

## 第四步：AbstractAXSModule 声明式 API 详解

继承 `AbstractAXSModule` 时，可通过覆写以下方法声明模块能力，基类在 `onEnable` 时自动处理：

### 配置管理

| 方法 | 说明 | 示例 |
|------|------|------|
| `configFileName()` | 模块默认配置文件名（从 Jar 导出到 `data/<moduleId>/config.yml`） | `"MyModule.yml"` |
| `messagesFileName()` | 外部化消息文件名（支持 `&` 颜色码和 `{0}` 占位符） | `"messages.yml"` |
| `defaultSyncPolicy()` | 配置同步策略，动态节点用 `SyncPolicy.builder().dynamicSection("xxx").build()` | 默认 `strict()` |
| `currentConfigVersion()` | 配置版本号，破坏性改动时递增 | 默认 `1` |
| `configVersionPath()` | 版本号字段路径 | 默认 `"config-version"` |
| `migrationFolder()` | 迁移规则文件夹（Jar 内相对路径） | 默认 `"migrations"` |
| `mainConfigValidations()` | 配置字段校验规则列表 | 默认空列表 |
| `additionalConfigSpecs()` | 附属配置规约（如 `chat/channels/*.yml`） | 默认空列表 |

### UI 与资源

| 方法 | 说明 | 示例 |
|------|------|------|
| `uiResourceMappings()` | Jar 内资源路径 → 宿主数据目录输出路径 | `Map.of("arcartx/ui/xxx.yml", "ui/xxx.yml")` |
| `overwriteUiFiles()` | 是否覆写已有 UI 文件 | 默认 `false` |

### 事件与命令

| 方法 | 说明 | 示例 |
|------|------|------|
| `createListeners()` | Bukkit 事件监听器列表（自动注册/注销） | `List.of(new MyListener())` |
| `commandBindings()` | 独立玩家命令：命令名 → Executor（需在 plugin.yml 声明命令） | `Map.of("mycmd", new MyCmd())` |

### PAPI 与客户端

| 方法 | 说明 | 示例 |
|------|------|------|
| `createPlaceholderExpansion()` | PAPI 占位符扩展实例（返回 null 不注册） | `new MyExpansion()` |
| `createPacketHandler()` | 客户端自定义包处理器 | `new MyPacketHandler()` |
| `packetHandlerPriority()` | 处理器优先级（越小越优先） | 默认 `0` |
| `createInitializedHandler()` | 客户端初始化完成回调 | `new MyInitHandler()` |

---

## 第五步：UI 绑定与资源路径

AXS 模块的 UI 由两部分组成：**资源文件** 和 **运行时绑定**。

### 1. 声明 UI 资源映射

在 `src/main/resources/` 下放置 UI YAML 文件：

```
src/main/resources/
  arcartx/
    └── ui/
        └── my_ui.yml
```

覆写 `uiResourceMappings()`：

```java
@Override
protected Map<String, String> uiResourceMappings() {
    return Map.of(
        "arcartx/ui/my_ui.yml", "ui/my_ui.yml"
    );
}
```

基类在 `onEnable` 时会自动将 `arcartx/ui/my_ui.yml` 从模块 Jar 导出到 `plugins/ArcartXSuite/ui/my_ui.yml`。

### 2. 运行时绑定 UI

在 `startService()` 中调用 `bindUi()`：

```java
@Override
protected void startService() {
    // 将导出的 UI 文件注册到 ArcartX 客户端
    bindUi("my_ui", "ui/my_ui.yml");
}
```

`bindUi(uiId, relativePath)` 封装了以下逻辑：

```java
// 等效于：
File uiFile = new File(context.uiFolder(), "ui/my_ui.yml");
UiBinding binding = context.prepareUiBinding("MyModule", "my_ui", true, uiFile);
recordUiBinding("ui/my_ui.yml", binding);
```

### 3. reload 时 UI 不注销

`AbstractAXSModule` 内部使用 `reloading` 标志处理 reload 逻辑：

```java
@Override
public void onReload() throws Exception {
    reloading = true;   // 标记 reload 中
    try {
        onDisable();
        onEnable(context);
    } finally {
        reloading = false;
    }
}
```

`onDisable()` 检测到 `reloading == true` 时会**跳过 UI 注销**，避免客户端丢失已打开的 HUD 或菜单界面。reload 完成后新配置自动生效，UI 保持打开状态。

> **开发者无需手动处理**：基类已内置此逻辑，你只需在 `loadConfiguration()` 中重新读取配置即可。

---

## 第六步：使用 ModuleContext

`ModuleContext` 是模块与宿主通信的唯一接口，禁止直接引用宿主实现类。

### 基础设施

```java
// 宿主插件实例（注册事件、调度任务）
JavaPlugin plugin = context.plugin();

// 带模块前缀的 Logger
Logger logger = context.logger();

// 模块私有数据目录（plugins/ArcartXSuite/data/mymodule/）
File dataFolder = context.dataFolder();

// UI 文件输出目录（plugins/ArcartXSuite/ui/）
File uiFolder = context.uiFolder();
```

### ArcartX 桥接

```java
// 获取 ArcartX 发包桥接（UI 注册、自定义数据包）
PacketBridgeAPI packetBridge = context.packetBridge();

// 发送自定义数据包到客户端
packetBridge.sendPacket(player, "my_channel", Map.of("key", "value"));

// 获取客户端桥接（检测客户端是否在线、发送客户端事件）
ClientBridgeAPI clientBridge = context.clientBridge();

// 获取 ItemStack 桥接（序列化/反序列化带 NBT 的物品）
ItemBridgeAPI itemBridge = context.itemStackBridge();

// 获取 Prop 桥接（快捷道具栏）
PropBridgeAPI propBridge = context.propBridge();
```

### 全局桥接

```java
// 物品来源注册表（统一 MythicMobs/NeigeItems/MMOItems/Overture）
ItemSourceRegistry itemSource = context.itemSourceRegistry();

// 物品匹配器（按 id/名称/NBT 匹配）
ItemMatcherAPI matcher = context.itemMatcher();

// 货币管理器（Vault/PlayerPoints 等）
CurrencyBridgeAPI currency = context.currencyManager();

// 属性桥接注册表（AttributePlus/CraneAttribute/MythicLib/Symphony）
AttributeBridgeRegistry attr = context.attributeBridge();

// Aria 脚本桥接（需服务器安装 Blink 系插件）
AriaBridge aria = context.ariaBridge();
if (aria.available()) {
    // 执行 Aria 脚本
}

// 条件评估器（PlaceholderAPI + Aria 脚本混合条件）
ScriptConditionEvaluator eval = context.scriptConditionEvaluator();
boolean ok = eval.evaluate(player, "%player_name% == 'Steve' && aria:hasItem('diamond')");

// TACZ 兼容状态查询
boolean taczActive = context.taczActive();
```

### 高级桥接（模块独立管理生命周期）

```java
// 创建路标桥接（模块卸载时自动清理）
WaypointBridgeAPI waypoint = context.createWaypointBridge();
waypoint.create(player, "目标", x, y, z, Color.RED);

// 创建 Adyeshach NPC 桥接
AdyeshachNpcBridgeAPI npc = context.createAdyeshachNpcBridge();
```

### 跨模块通信

```java
// 按类型获取其他模块实例
Optional<OtherModule> other = context.getModule(OtherModule.class);
other.ifPresent(m -> m.doSomething());

// 按 ID 获取
Optional<AXSModule> mod = context.getModule("othermodule");

// Capability 跨模块通信
context.registerCapability(MyService.class, new MyServiceImpl());
MyService service = context.getCapability(MyService.class);
```

### 安全与账号

```java
// 客户端包频率限制（可能为 null，开源版宿主不含实现）
PacketGuardAPI guard = context.packetGuard();

// 账号类型识别（微软正版 / LittleSkin / 离线）
AccountTypeService account = context.accountTypeService();
AccountType type = account.resolve(player);
```

### 跨服传输

```java
// Redis + Proxy 双后端统一跨服通道
CrossServerAPI cross = context.crossServer();
cross.broadcast("my_channel", message);
cross.sendToServer("lobby", "my_channel", payload);
```

### 资源与工具

```java
// 从模块 Jar 读取资源
InputStream in = context.openProtectedResource("arcartx/ui/my_ui.yml", getClass().getClassLoader());

// 导出资源到宿主目录
context.exportResource("config.yml", targetFile, false);

// 检查外部插件是否安装
boolean hasPapi = context.hasPlugin("PlaceholderAPI");
```

---

## 第七步：注册子命令

实现 `ModuleCommandHandler` 接口即可自动注册 `/axs mymodule ...` 子命令：

```java
import xuanmo.arcartxsuite.api.ModuleCommandHandler;

public final class MyModule extends AbstractAXSModule implements ModuleCommandHandler {

    @Override
    public String commandId() {
        return "mymodule"; // /axs mymodule ...
    }

    @Override
    public List<String> actions() {
        return List.of("help", "status", "reload");
    }

    @Override
    public boolean onCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("用法: /axs mymodule <action>");
            return true;
        }
        String action = args[1];
        switch (action) {
            case "status":
                sender.sendMessage("MyModule 运行中");
                break;
            default:
                sender.sendMessage("未知命令: " + action);
        }
        return true;
    }
}
```

---

## 第八步：处理客户端包

客户端通过 ArcartX 模组向服务器发送自定义数据包，模块实现 `ClientPacketHandler` 接收：

```java
import xuanmo.arcartxsuite.api.ClientPacketHandler;

public class MyPacketHandler implements ClientPacketHandler {

    @Override
    public String action() {
        return "my_action"; // 客户端发送 action=my_action 时触发
    }

    @Override
    public void handle(Player player, Map<String, Object> data) {
        String value = (String) data.get("value");
        player.sendMessage("收到客户端数据: " + value);
    }

    @Override
    public int priority() {
        return 0; // 数字越小优先级越高
    }
}
```

在 `AbstractAXSModule` 中通过声明式方法注册：

```java
@Override
protected ClientPacketHandler createPacketHandler() {
    return new MyPacketHandler();
}

@Override
protected int packetHandlerPriority() {
    return 0;
}
```

---

## 第九步：注册 Capability（跨模块通信）

模块可以暴露自己的能力供其他模块调用：

```java
// 定义接口（放在公共包中，或 axs-api 内）
public interface MyService {
    void doSomething(Player player);
}

// 模块内实现并注册
@Override
protected void startService() {
    context.registerCapability(MyService.class, new MyServiceImpl());
}

// 其他模块获取
MyService service = context.getCapability(MyService.class);
if (service != null) {
    service.doSomething(player);
}
```

---

## 第十步：打包与部署

1. 执行 `./gradlew jar` 构建模块 JAR
2. 将 `build/libs/MyAXSModule.jar` 复制到服务器：

```
plugins/
  ArcartXSuite.jar
  ArcartXSuite/
    config.yml
    modules/
      MyAXSModule.jar   ← 你的模块
```

3. 在 `config.yml` 中启用：

```yaml
modules:
  mymodule:
    enabled: true
```

4. 重启服务器或使用 `/axs load mymodule` 热加载

---

## 常见问题与排坑

### Q: 模块加载失败，控制台报 `ClassNotFoundException`
- 检查 `module.yml` 中的 `main` 字段是否与 Java 类全限定名一致
- 检查模块 JAR 是否包含编译后的 `.class` 文件

### Q: `module.yml` 放错位置
- 必须放在 `src/main/resources/module.yml`，打包后会位于 JAR 根目录
- 放在 `src/main/resources/META-INF/` 或其他子目录下会找不到

### Q: UI 文件导出后客户端看不到
- 确认玩家客户端已安装 ArcartX 模组
- 检查 `uiResourceMappings()` 的键值是否正确（Jar 内路径 → 输出路径）
- 检查 `bindUi()` 的 `relativePath` 是否与 `uiResourceMappings()` 的值一致

### Q: reload 后 UI 丢失
- 确保使用 `AbstractAXSModule` 的 reload 机制（基类已处理 UI 保持逻辑）
- 不要手动调用 `context.unregisterUi()` 后再重新注册

### Q: 配置没有生效
- 配置文件位置：`plugins/ArcartXSuite/data/<moduleId>/config.yml`
- 旧版本（1.0.x）的配置在宿主根目录，基类会自动迁移到新位置
- 迁移后会提示运行 `/axs config preview <moduleId>` 检查兼容性

### Q: 依赖模块未加载导致本模块启动失败
- `depends` 为硬依赖，缺少时本模块会拒绝启动
- `softdepends` 为软依赖，缺少时仅跳过，不报错
- 检查被依赖模块的 `id` 是否拼写正确

### Q: 占位符扩展未注册
- 确保服务器已安装 PlaceholderAPI
- `createPlaceholderExpansion()` 返回的对象需符合 PAPI 扩展规范

### Q: 可以 `import xuanmo.arcartxsuite.bridge.*` 吗？
- **不可以**。模块只能通过 `ModuleContext` 获取的 API 接口与宿主交互
- 直接引用宿主实现类会导致 ClassLoader 隔离问题，且可能在不同版本中不兼容

---

## 更多参考

- `axs-api/src/main/java/xuanmo/arcartxsuite/api/` — 所有公共接口的源码与 Javadoc
- `src/main/java/xuanmo/arcartxsuite/module/` — 宿主加载模块的参考实现
