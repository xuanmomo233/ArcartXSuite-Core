package xuanmo.arcartxsuite.api.currency;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Set;
import org.bukkit.entity.Player;

/**
 * 货币桥接管理 API。
 * <p>
 * 提供统一的多货币查询、扣款、充值能力。
 */
public interface CurrencyBridgeAPI {

    /** 获取指定货币的桥接实例，不存在返回 null */
    CurrencyBridge bridge(String currencyId);

    /** 获取货币定义 */
    CurrencyDefinition definition(String currencyId);

    /** 获取所有已配置的货币定义 */
    Collection<CurrencyDefinition> definitions();

    /** 获取所有已配置的货币 ID */
    Set<String> currencyIds();

    /** 按货币精度格式化金额 */
    String format(String currencyId, BigDecimal amount);

    /**
     * 单个货币的操作接口。
     */
    interface CurrencyBridge {
        CurrencyDefinition definition();

        boolean available();

        String unavailableReason();

        BigDecimal balance(Player player);

        CurrencyTransactionResult withdraw(Player player, BigDecimal amount);

        CurrencyTransactionResult deposit(Player player, BigDecimal amount);
    }
}
