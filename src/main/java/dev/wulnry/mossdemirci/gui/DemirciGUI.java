package dev.wulnry.mossdemirci.gui;

import dev.wulnry.mossdemirci.MossDemirci;
import dev.wulnry.mossdemirci.util.ColorUtil;
import dev.wulnry.mossdemirci.util.MaterialUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Demirci GUI Manager.
 * Config'den okunan ayarlarla dinamik menü oluşturur.
 * Süsleme slotları, eşya slotları ve lore bilgileri tamamen config'den yönetilir.
 * Recipe'ler cache'lenir, her tıklamada config parse edilmez.
 */
public class DemirciGUI {

    private final MossDemirci plugin;
    private final String title;
    private final int size;
    private final Set<Integer> decorationSlots;
    private final Material decorationMaterial;
    private final String decorationName;
    private final Map<Integer, CraftRecipe> recipes;

    public DemirciGUI(MossDemirci plugin) {
        this.plugin = plugin;
        this.title = ColorUtil.colorize(plugin.getConfig().getString("gui.title", "\u00260✦ Demirci Menüsü ✦"));

        // GUI boyutu doğrulama — 9'un katlarına yuvarla, 9-54 arasında sınırla
        int rawSize = plugin.getConfig().getInt("gui.size", 45);
        this.size = validateSize(rawSize);
        if (this.size != rawSize) {
            plugin.getLogger().warning("GUI boyutu " + rawSize + " geçersiz → " + this.size + " olarak düzeltildi (9'un katı, 9-54 arası).");
        }

        this.decorationMaterial = MaterialUtil.parseMaterial(
                plugin.getConfig().getString("gui.decoration.material", "GRAY_STAINED_GLASS_PANE"),
                Material.GRAY_STAINED_GLASS_PANE, plugin.getLogger());
        this.decorationName = ColorUtil.colorize(
                plugin.getConfig().getString("gui.decoration.name", " "));
        this.decorationSlots = parseDecorationSlots();
        this.recipes = parseRecipes();

        // Slot çakışma kontrolü
        checkSlotConflicts();
    }

    /**
     * Belirtilen oyuncu için demirci menüsünü açar.
     * DemirciHolder kullanarak tip-güvenli envanter oluşturur.
     */
    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new DemirciHolder(), size, title);

        // Süsleme cam panellerini yerleştir
        ItemStack glass = createDecoration();
        for (int slot : decorationSlots) {
            if (slot >= 0 && slot < size) {
                inv.setItem(slot, glass);
            }
        }

        // Config'den eşyaları yerleştir
        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemConfig = itemsSection.getConfigurationSection(key);
                if (itemConfig == null) continue;

                int slot = itemConfig.getInt("slot", 0);
                Material displayMat = MaterialUtil.parseMaterial(
                        itemConfig.getString("display-material", "BARRIER"),
                        Material.BARRIER, plugin.getLogger());
                String displayName = ColorUtil.colorize(
                        itemConfig.getString("display-name", "\u0026cBilinmeyen Eşya"));

                List<String> lore = itemConfig.getStringList("lore");
                List<String> coloredLore = new ArrayList<>(lore.size());
                for (String loreLine : lore) {
                    coloredLore.add(ColorUtil.colorize(loreLine));
                }

                ItemStack displayItem = new ItemStack(displayMat);
                ItemMeta meta = displayItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(displayName);
                    meta.setLore(coloredLore);
                    displayItem.setItemMeta(meta);
                }

                if (slot >= 0 && slot < size) {
                    inv.setItem(slot, displayItem);
                }
            }
        }

        player.openInventory(inv);
    }

    /**
     * Slot numarasına göre cache'lenmiş recipe döndürür.
     *
     * @param slot Tıklanan slot numarası
     * @return Eşleşen CraftRecipe veya null (süsleme/boş slot)
     */
    public CraftRecipe getRecipe(int slot) {
        return recipes.get(slot);
    }

    /**
     * Tüm cache'lenmiş recipe'leri döndürür (immutable).
     */
    public Map<Integer, CraftRecipe> getRecipes() {
        return Collections.unmodifiableMap(recipes);
    }

    /**
     * Menü başlığını döndürür (renk kodları çözümlenmiş hali).
     */
    public String getTitle() {
        return title;
    }

    /**
     * GUI boyutunu doğrular ve en yakın geçerli değere yuvarlar.
     * Bukkit sadece 9'un katlarını (9, 18, 27, 36, 45, 54) kabul eder.
     */
    private int validateSize(int rawSize) {
        int rounded = ((rawSize + 8) / 9) * 9;
        return Math.min(54, Math.max(9, rounded));
    }

    /**
     * Config'den süsleme slot numaralarını parse eder.
     * Her satır virgülle ayrılmış slot numaraları içerir.
     */
    private Set<Integer> parseDecorationSlots() {
        Set<Integer> slots = new HashSet<>();
        if (!plugin.getConfig().getBoolean("gui.decoration.enabled", true)) {
            return slots;
        }
        List<String> slotLines = plugin.getConfig().getStringList("gui.decoration.slots");
        for (String line : slotLines) {
            String[] parts = line.split(",");
            for (String part : parts) {
                try {
                    int parsed = Integer.parseInt(part.trim());
                    if (parsed >= 0 && parsed < size) {
                        slots.add(parsed);
                    } else {
                        plugin.getLogger().warning("Süsleme slotu " + parsed + " GUI boyutu dışında (0-" + (size - 1) + "), atlanıyor.");
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Geçersiz süsleme slot numarası: '" + part.trim() + "', atlanıyor.");
                }
            }
        }
        return slots;
    }

    /**
     * Config'den recipe'leri parse ederek slot → CraftRecipe map'ine cache'ler.
     */
    private Map<Integer, CraftRecipe> parseRecipes() {
        Map<Integer, CraftRecipe> recipeMap = new HashMap<>();
        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("items");
        if (itemsSection == null) return recipeMap;

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection itemConfig = itemsSection.getConfigurationSection(key);
            if (itemConfig == null) continue;

            int slot = itemConfig.getInt("slot", 0);
            Material reqDiamond = MaterialUtil.parseMaterial(
                    itemConfig.getString("required-diamond-item", "DIAMOND_HELMET"),
                    Material.BARRIER, plugin.getLogger());
            
            // CustomModelData (opsiyonel)
            Integer reqDiamondCmd = itemConfig.contains("required-diamond-cmd") 
                    ? itemConfig.getInt("required-diamond-cmd") : null;
                    
            int reqSmithingTemplate = itemConfig.getInt("required-smithing-template", 1);
            int reqNetherite = itemConfig.getInt("required-netherite-ingot", 32);
            int reqDiamondCount = itemConfig.getInt("required-diamond", 100);
            double reqMoney = itemConfig.getDouble("required-money", 500000);
            
            Material result = MaterialUtil.parseMaterial(
                    itemConfig.getString("result-item", "NETHERITE_HELMET"),
                    Material.BARRIER, plugin.getLogger());
                    
            Integer resultCmd = itemConfig.contains("result-cmd") 
                    ? itemConfig.getInt("result-cmd") : null;
                    
            String displayName = itemConfig.getString("display-name", "Netherite Eşya");

            CraftRecipe recipe = new CraftRecipe(slot, reqDiamond, reqDiamondCmd, reqSmithingTemplate, reqNetherite,
                    reqDiamondCount, reqMoney, result, resultCmd, displayName);
            recipeMap.put(slot, recipe);
        }

        return recipeMap;
    }

    /**
     * Süsleme slotları ve eşya slotları arasında çakışma olup olmadığını kontrol eder.
     * Çakışma varsa uyarı loglar (eşya öncelikli olacağı için süsleme ezilir).
     */
    private void checkSlotConflicts() {
        for (Map.Entry<Integer, CraftRecipe> entry : recipes.entrySet()) {
            int slot = entry.getKey();
            if (decorationSlots.contains(slot)) {
                plugin.getLogger().warning("Slot çakışması: Eşya slotu " + slot
                        + " aynı zamanda süsleme slotu olarak tanımlı. Eşya öncelikli olacak.");
            }
        }
    }

    /**
     * Süsleme ItemStack'i oluşturur (cam panel).
     */
    private ItemStack createDecoration() {
        ItemStack item = new ItemStack(decorationMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(decorationName);
            item.setItemMeta(meta);
        }
        return item;
    }
}
