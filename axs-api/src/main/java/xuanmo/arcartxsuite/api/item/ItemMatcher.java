package xuanmo.arcartxsuite.api.item;

import java.util.List;
import java.util.regex.Pattern;

public record ItemMatcher(
    List<String> materialIds,
    List<String> mythicItemIds,
    List<String> neigeItemIds,
    List<String> overtureItemIds,
    List<String> kinds,
    List<String> nameContains,
    List<String> loreContains,
    List<Pattern> namePatterns,
    List<Pattern> lorePatterns
) {

    public static ItemMatcher empty() {
        return new ItemMatcher(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public boolean emptyMatcher() {
        return materialIds.isEmpty()
            && mythicItemIds.isEmpty()
            && neigeItemIds.isEmpty()
            && overtureItemIds.isEmpty()
            && kinds.isEmpty()
            && nameContains.isEmpty()
            && loreContains.isEmpty()
            && namePatterns.isEmpty()
            && lorePatterns.isEmpty();
    }
}
