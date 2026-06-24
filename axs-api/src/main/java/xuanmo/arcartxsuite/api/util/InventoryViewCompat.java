package xuanmo.arcartxsuite.api.util;

import java.lang.reflect.Method;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.Inventory;

/**
 * Paper 1.21+ 将 {@code InventoryView} 从 abstract class 改为 interface，
 * 导致对 1.20 API 编译的字节码在 1.21+ 运行时抛出 {@link IncompatibleClassChangeError}。
 * <p>
 * 本工具类通过反射调用 {@code InventoryView} 的方法，绕过编译时 class/interface 差异，
 * 同时兼容 1.20（class）和 1.21+（interface）。
 */
public final class InventoryViewCompat {

    private static final Method GET_VIEW;
    private static final Method GET_TOP_INVENTORY;

    static {
        try {
            GET_VIEW = InventoryEvent.class.getMethod("getView");
            GET_TOP_INVENTORY = Class.forName("org.bukkit.inventory.InventoryView")
                .getMethod("getTopInventory");
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private InventoryViewCompat() {
    }

    /**
     * 兼容获取 {@code event.getView().getTopInventory()}。
     */
    public static Inventory getTopInventory(InventoryEvent event) {
        try {
            Object view = GET_VIEW.invoke(event);
            return (Inventory) GET_TOP_INVENTORY.invoke(view);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("InventoryView compat failure", exception);
        }
    }
}
