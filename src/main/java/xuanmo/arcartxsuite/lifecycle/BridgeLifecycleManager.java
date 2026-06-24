package xuanmo.arcartxsuite.lifecycle;

import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.bridge.ArcartXClientBridge;
import xuanmo.arcartxsuite.bridge.ArcartXItemStackBridge;
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;
import xuanmo.arcartxsuite.bridge.ArcartXPropBridge;
import xuanmo.arcartxsuite.bridge.TaczCombatBridge;

/**
 * 核心桥接生命周期管理器。
 * <p>
 * 负责创建、初始化、关闭所有 ArcartX 桥接实现。
 * 仅由核心入口调用，模块不直接访问此类。
 */
public final class BridgeLifecycleManager {

    private final JavaPlugin plugin;
    private final ArcartXPacketBridge packetBridge;
    private final ArcartXClientBridge clientBridge;
    private final ArcartXItemStackBridge itemStackBridge;
    private final ArcartXPropBridge propBridge;
    private TaczCombatBridge taczCombatBridge;

    public BridgeLifecycleManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.packetBridge = new ArcartXPacketBridge(plugin);
        this.clientBridge = new ArcartXClientBridge(plugin);
        this.itemStackBridge = new ArcartXItemStackBridge(plugin);
        this.propBridge = new ArcartXPropBridge(plugin);
    }

    /** 初始化全部桥接。返回 false 表示 packetBridge 初始化失败，应中止启动。 */
    public boolean initialize(boolean taczEnabled, boolean taczDebug) {
        if (!packetBridge.initialize()) {
            return false;
        }
        clientBridge.initialize();
        itemStackBridge.initialize();
        propBridge.initialize();
        taczCombatBridge = TaczCombatBridge.tryInitialize(plugin, taczEnabled, taczDebug);
        return true;
    }

    public void shutdown() {
        if (taczCombatBridge != null) {
            taczCombatBridge.shutdown();
            taczCombatBridge = null;
        }
        propBridge.shutdown();
        itemStackBridge.shutdown();
        clientBridge.shutdown();
        packetBridge.shutdown();
    }

    public ArcartXPacketBridge packetBridge() {
        return packetBridge;
    }

    public ArcartXClientBridge clientBridge() {
        return clientBridge;
    }

    public ArcartXItemStackBridge itemStackBridge() {
        return itemStackBridge;
    }

    public ArcartXPropBridge propBridge() {
        return propBridge;
    }

    public TaczCombatBridge taczCombatBridge() {
        return taczCombatBridge;
    }

    public String describePacketMode() {
        return packetBridge == null ? "unavailable" : packetBridge.describePacketMode();
    }
}
