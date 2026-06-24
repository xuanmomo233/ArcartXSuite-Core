package xuanmo.arcartxsuite.api.currency;

public record CurrencyDefinition(
    String id,
    String provider,
    String displayName,
    int scale,
    String balancePlaceholder,
    String withdrawCommand,
    String depositCommand
) {
}
