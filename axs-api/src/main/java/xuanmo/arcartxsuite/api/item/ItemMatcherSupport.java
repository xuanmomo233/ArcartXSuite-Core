package xuanmo.arcartxsuite.api.item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemMatcherSupport implements ItemMatcherAPI {

    private final Function<ItemStack, String> mythicItemIdResolver;
    private final Function<ItemStack, String> neigeItemIdResolver;
    private final Function<ItemStack, String> overtureItemIdResolver;

    public ItemMatcherSupport(Function<ItemStack, String> mythicItemIdResolver, Function<ItemStack, String> neigeItemIdResolver) {
        this(mythicItemIdResolver, neigeItemIdResolver, null);
    }

    public ItemMatcherSupport(Function<ItemStack, String> mythicItemIdResolver, Function<ItemStack, String> neigeItemIdResolver, Function<ItemStack, String> overtureItemIdResolver) {
        this.mythicItemIdResolver = mythicItemIdResolver == null ? item -> "" : mythicItemIdResolver;
        this.neigeItemIdResolver = neigeItemIdResolver == null ? item -> "" : neigeItemIdResolver;
        this.overtureItemIdResolver = overtureItemIdResolver == null ? item -> "" : overtureItemIdResolver;
    }

    @Override
    public boolean matches(ItemMatcher matcher, ItemStack itemStack) {
        if (matcher == null || matcher.emptyMatcher() || itemStack == null || itemStack.getType().isAir()) {
            return false;
        }

        String materialId = ItemMatcherLoader.normalizeId(itemStack.getType().name());
        String mythicItemId = ItemMatcherLoader.normalizeId(mythicItemIdResolver.apply(itemStack));
        String neigeItemId = ItemMatcherLoader.normalizeId(neigeItemIdResolver.apply(itemStack));
        String overtureItemId = ItemMatcherLoader.normalizeId(overtureItemIdResolver.apply(itemStack));
        String displayName = displayName(itemStack);
        String normalizedName = normalizeToken(displayName);
        List<String> loreLines = normalizedLore(itemStack);
        Set<String> kinds = detectKinds(itemStack, mythicItemId, neigeItemId, overtureItemId);

        if (!matcher.materialIds().isEmpty() && !matcher.materialIds().contains(materialId)) {
            return false;
        }
        if (!matcher.mythicItemIds().isEmpty() && !matcher.mythicItemIds().contains(mythicItemId)) {
            return false;
        }
        if (!matcher.neigeItemIds().isEmpty() && !matcher.neigeItemIds().contains(neigeItemId)) {
            return false;
        }
        if (!matcher.overtureItemIds().isEmpty() && !matcher.overtureItemIds().contains(overtureItemId)) {
            return false;
        }
        if (!matcher.kinds().isEmpty() && matcher.kinds().stream().noneMatch(kinds::contains)) {
            return false;
        }
        if (!matcher.nameContains().isEmpty() && matcher.nameContains().stream().noneMatch(normalizedName::contains)) {
            return false;
        }
        if (!matcher.loreContains().isEmpty() && matcher.loreContains().stream().noneMatch(token -> loreLines.stream().anyMatch(line -> line.contains(token)))) {
            return false;
        }
        if (!matcher.namePatterns().isEmpty() && matcher.namePatterns().stream().noneMatch(pattern -> pattern.matcher(ChatColor.stripColor(displayName)).find())) {
            return false;
        }
        if (!matcher.lorePatterns().isEmpty() && matcher.lorePatterns().stream().noneMatch(pattern -> loreLines.stream().anyMatch(line -> pattern.matcher(ChatColor.stripColor(line)).find()))) {
            return false;
        }
        return true;
    }

    private static String displayName(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return itemStack.getType().name();
        }
        return meta.getDisplayName();
    }

    private static Set<String> detectKinds(ItemStack itemStack, String mythicItemId, String neigeItemId, String overtureItemId) {
        LinkedHashSet<String> kinds = new LinkedHashSet<>();
        Material material = itemStack.getType();
        String name = material.name();
        if (material.isBlock()) {
            kinds.add("block");
        }
        if (material.isEdible()) {
            kinds.add("food");
            kinds.add("consumable");
        }
        if (name.endsWith("_SWORD") || name.endsWith("_AXE")) {
            kinds.add("weapon");
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) {
            kinds.add("tool");
        }
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || name.equals("SHIELD")) {
            kinds.add("armor");
        }
        if (name.endsWith("_BOW") || name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT")) {
            kinds.add("ranged");
        }
        if (name.contains("POTION")) {
            kinds.add("potion");
            kinds.add("consumable");
        }
        if (!mythicItemId.isBlank()) {
            kinds.add("mythic");
        }
        if (!neigeItemId.isBlank()) {
            kinds.add("neige");
        }
        if (!overtureItemId.isBlank()) {
            kinds.add("overture");
        }
        if (name.contains("INGOT") || name.contains("GEM") || name.contains("SHARD") || name.contains("ORE")) {
            kinds.add("material");
        }
        return kinds;
    }

    private static List<String> normalizedLore(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String line : meta.getLore()) {
            result.add(normalizeToken(line));
        }
        return List.copyOf(result);
    }

    private static String normalizeToken(String value) {
        return ChatColor.stripColor(value == null ? "" : value).trim().toLowerCase();
    }
}
