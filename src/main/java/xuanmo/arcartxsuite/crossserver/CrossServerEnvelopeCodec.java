package xuanmo.arcartxsuite.crossserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import xuanmo.arcartxsuite.api.crossserver.CrossServerEnvelope;

public final class CrossServerEnvelopeCodec {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private CrossServerEnvelopeCodec() {
    }

    public static String encode(CrossServerEnvelope envelope) {
        return GSON.toJson(envelope);
    }

    public static CrossServerEnvelope decode(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty cross-server envelope");
        }
        CrossServerEnvelope envelope = GSON.fromJson(json, CrossServerEnvelope.class);
        if (envelope == null || envelope.module() == null || envelope.module().isBlank()) {
            throw new IllegalArgumentException("invalid cross-server envelope");
        }
        return envelope;
    }
}
