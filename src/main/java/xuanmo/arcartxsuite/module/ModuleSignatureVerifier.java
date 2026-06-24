package xuanmo.arcartxsuite.module;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.ModuleDescriptor;

/**
 * 模块数字签名验证器（Ed25519）。
 * <p>
 * 验证 module.yml 中的 {@code signature} 字段是否为作者私钥对以下 payload 的签名：
 * <pre>id + ":" + version + ":" + mainClass</pre>
 * <p>
 * 公钥通过 {@code config.yml} 的 {@code module-signature-public-keys} 配置注入（支持多个公钥）。
 * 如果公钥未配置，则跳过验证（向后兼容）。
 */
public final class ModuleSignatureVerifier {

    private final List<PublicKey> publicKeys;
    private final Logger logger;

    /**
     * @param publicKeyBase64List Base64 编码的 Ed25519 公钥列表；null 或空表示不启用验证
     * @param logger              日志输出
     */
    public ModuleSignatureVerifier(List<String> publicKeyBase64List, Logger logger) {
        this.logger = logger;
        if (publicKeyBase64List == null || publicKeyBase64List.isEmpty()) {
            this.publicKeys = Collections.emptyList();
            return;
        }
        List<PublicKey> keys = new ArrayList<>();
        for (String base64 : publicKeyBase64List) {
            if (base64 == null || base64.isBlank()) continue;
            try {
                byte[] decoded = Base64.getDecoder().decode(base64.trim());
                KeyFactory kf = KeyFactory.getInstance("Ed25519");
                keys.add(kf.generatePublic(new java.security.spec.X509EncodedKeySpec(decoded)));
            } catch (Exception e) {
                logger.warning("[ModuleSignature] 无法解析 Ed25519 公钥: " + e.getMessage());
            }
        }
        this.publicKeys = Collections.unmodifiableList(keys);
    }

    /** 是否启用了签名验证 */
    public boolean isEnabled() {
        return !publicKeys.isEmpty();
    }

    /**
     * 验证模块描述符的签名。
     * <p>
     * 遍历所有配置的公钥，只要有一个验证通过即算成功。
     *
     * @return true = 验证通过（或无公钥不验证）；false = 签名缺失或验证失败
     */
    public boolean verify(ModuleDescriptor descriptor) {
        if (publicKeys.isEmpty()) {
            return true; // 未配置公钥，跳过验证（向后兼容）
        }
        String sigB64 = descriptor.signature();
        if (sigB64 == null || sigB64.isBlank()) {
            logger.warning("[ModuleSignature] 模块 " + descriptor.id() + " 缺少 signature，拒绝加载。");
            return false;
        }
        try {
            byte[] signature = Base64.getDecoder().decode(sigB64.trim());
            byte[] payload = buildPayload(descriptor).getBytes(StandardCharsets.UTF_8);
            for (PublicKey pk : publicKeys) {
                Signature sig = Signature.getInstance("Ed25519");
                sig.initVerify(pk);
                sig.update(payload);
                if (sig.verify(signature)) {
                    return true;
                }
            }
            logger.warning("[ModuleSignature] 模块 " + descriptor.id() + " 签名验证失败，拒绝加载。");
            return false;
        } catch (Exception e) {
            logger.warning("[ModuleSignature] 模块 " + descriptor.id() + " 签名验证异常: " + e.getMessage());
            return false;
        }
    }

    private static String buildPayload(ModuleDescriptor descriptor) {
        return descriptor.id() + ":" + descriptor.version() + ":" + descriptor.mainClass();
    }
}
