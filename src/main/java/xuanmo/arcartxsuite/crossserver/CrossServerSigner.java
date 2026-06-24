package xuanmo.arcartxsuite.crossserver;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import xuanmo.arcartxsuite.api.crossserver.CrossServerEnvelope;

public final class CrossServerSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private CrossServerSigner() {
    }

    public static String sign(CrossServerEnvelope envelope, byte[] secret) {
        return toHex(hmac(secret, buildSigningPayload(envelope).getBytes(StandardCharsets.UTF_8)));
    }

    public static boolean verify(CrossServerEnvelope envelope, String signatureHex, byte[] secret) {
        if (signatureHex == null || signatureHex.isBlank()) {
            return false;
        }
        String expected = sign(envelope, secret);
        return constantTimeEquals(expected, signatureHex.trim().toLowerCase(Locale.ROOT));
    }

    public static CrossServerEnvelope withSignature(CrossServerEnvelope unsigned, byte[] secret) {
        String signature = sign(unsigned, secret);
        return new CrossServerEnvelope(
            unsigned.protocol(),
            unsigned.module(),
            unsigned.nodeId(),
            unsigned.messageId(),
            unsigned.timestamp(),
            unsigned.payload(),
            signature
        );
    }

    private static byte[] hmac(byte[] secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
        }
    }

    private static String buildSigningPayload(CrossServerEnvelope envelope) {
        return "axs-cross-envelope-v1\n"
            + envelope.protocol() + '\n'
            + nullToEmpty(envelope.module()) + '\n'
            + nullToEmpty(envelope.nodeId()) + '\n'
            + nullToEmpty(envelope.messageId()) + '\n'
            + envelope.timestamp() + '\n'
            + nullToEmpty(envelope.payload());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int diff = 0;
        for (int index = 0; index < left.length(); index++) {
            diff |= left.charAt(index) ^ right.charAt(index);
        }
        return diff == 0;
    }
}
