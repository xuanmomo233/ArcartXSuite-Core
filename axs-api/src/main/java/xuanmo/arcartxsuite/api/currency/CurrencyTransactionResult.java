package xuanmo.arcartxsuite.api.currency;

public record CurrencyTransactionResult(
    boolean success,
    String message
) {
    public static CurrencyTransactionResult ok() {
        return new CurrencyTransactionResult(true, "");
    }

    public static CurrencyTransactionResult failure(String message) {
        return new CurrencyTransactionResult(false, message == null ? "" : message);
    }
}
