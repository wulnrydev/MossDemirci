package dev.wulnry.mossdemirci.listener;

import dev.wulnry.mossdemirci.MossDemirci;
import dev.wulnry.mossdemirci.gui.CraftRecipe;
import dev.wulnry.mossdemirci.gui.DemirciHolder;
import dev.wulnry.mossdemirci.util.ColorUtil;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI Listener — Menü tıklama olayları ve dupe koruması.
 *
 * Güvenlik Katmanları:
 * 1. DemirciHolder instanceof kontrolü — sadece demirci menüsü işlenir (UUID tracking yerine)
 * 2. Tüm InventoryClickEvent'ler GUI içerisinde iptal edilir
 * 3. InventoryDragEvent sadece üst envanter (GUI) slotlarında engellenir
 * 4. Sadece üst envanterde LEFT_CLICK ile üretim tetiklenir
 * 5. Shift-click, number key, double-click, drop → hepsi cancelled
 * 6. Race condition koruması — processingPlayers kilidi ile çift tıklama dupe önlenir
 * 7. Üretim mantığı: kontrol → sil (rollback destekli, orijinal eşya korunur) → ver
 * 8. Enchantment/meta transfer — elmas eşya büyüleri netherite eşyaya aktarılır
 */
public class GUIListener implements Listener {

    private final MossDemirci plugin;

    /**
     * Üretim işlemi sırasında kilitlenen oyuncular.
     * Aynı tick'te çift tıklama dupe'unu önler.
     */
    private final Set<UUID> processingPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public GUIListener(MossDemirci plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════════════════════
    //  DUPE KORUMASI — Envanter Olayları
    // ═══════════════════════════════════════════════════════════

    /**
     * GUI içerisindeki TÜM tıklama olaylarını iptal eder.
     * Sadece üst envanterde LEFT_CLICK ile üretim tetiklenir.
     * Shift-click, number key (1-9), double-click, drop, control-drop,
     * move-to-other-inventory hepsi iptal edilir.
     *
     * DemirciHolder instanceof kontrolü ile sadece demirci menüsü işlenir.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Tip-güvenli kontrol: Bu envanter demirci menüsü mü?
        if (!(event.getInventory().getHolder() instanceof DemirciHolder)) return;

        // GUI içerisinde TÜM tıklamaları iptal et — dupe korumasının temeli
        event.setCancelled(true);

        // Sadece üst envanterde (GUI'nin kendisinde) yapılan tıklamaları işle
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        // Sadece sol tıklama ile üretim yapılabilir
        if (event.getClick() != ClickType.LEFT) return;

        handleCraft(player, event.getSlot());
    }

    /**
     * GUI içerisinde eşya sürükleme (drag) olaylarını engeller.
     * Sadece üst envanter (GUI) slotlarına sürükleme engellenir,
     * alt envanterdeki (oyuncu envanteri) sürüklemeye izin verilir.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        // Tip-güvenli kontrol
        if (!(event.getInventory().getHolder() instanceof DemirciHolder)) return;

        // Sadece üst envanter slotlarına sürükleme engellenir
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Oyuncu sunucudan çıktığında processing kilidini temizler.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        processingPlayers.remove(event.getPlayer().getUniqueId());
    }

    // ═══════════════════════════════════════════════════════════
    //  GUI Yönetimi
    // ═══════════════════════════════════════════════════════════

    /**
     * Plugin kapanırken veya reload sırasında tüm açık demirci GUI'lerini kapatır.
     * DemirciHolder kontrolü ile sadece demirci menüleri kapatılır.
     */
    public void closeAllGUIs() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof DemirciHolder) {
                player.closeInventory();
            }
        }
        processingPlayers.clear();
    }

    // ═══════════════════════════════════════════════════════════
    //  ÜRETİM MANTIĞI
    // ═══════════════════════════════════════════════════════════

    /**
     * Tıklanan slota göre üretim işlemini başlatır.
     * Race condition koruması: processingPlayers kilidi ile çift tıklama önlenir.
     * Kilit, bir sonraki tick'te otomatik olarak kaldırılır.
     */
    private void handleCraft(Player player, int slot) {
        // Çift tıklama / race condition koruması
        if (!processingPlayers.add(player.getUniqueId())) return;

        try {
            handleCraftInternal(player, slot);
        } finally {
            // Kilidi bir sonraki tick'te kaldır — aynı tick'te tekrar tetiklenmesini engeller
            Bukkit.getScheduler().runTask(plugin, () ->
                    processingPlayers.remove(player.getUniqueId()));
        }
    }

    /**
     * Üretim mantığının çekirdeği.
     * Sıra:
     * 1. Cache'den slot → recipe eşleşmesi
     * 2. Envanter boş slot kontrolü
     * 3. Elmas eşya durability kontrolü (FULL olmalı)
     * 4. Netherite külçe ve elmas miktarı kontrolü
     * 5. Vault bakiye kontrolü
     * 6. Eşyaları sırayla sil (rollback destekli, orijinal eşya korunur)
     * 7. Vault'tan para çek
     * 8. Enchantment/meta transfer ile netherite eşya oluştur ve ver
     */
    private void handleCraftInternal(Player player, int slot) {
        String prefix = ColorUtil.colorize(plugin.getConfig().getString("prefix", ""));

        // Cache'den recipe al (config her tıklamada parse edilmez)
        CraftRecipe recipe = plugin.getDemirciGUI().getRecipe(slot);
        if (recipe == null) return; // Süsleme veya boş slota tıklandı

        Material requiredDiamondItem = recipe.getRequiredDiamondItem();
        Integer requiredDiamondCmd = recipe.getRequiredDiamondCmd();
        int requiredSmithingTemplate = recipe.getRequiredSmithingTemplate();
        int requiredNetherite = recipe.getRequiredNetheriteIngot();
        int requiredDiamond = recipe.getRequiredDiamond();
        double requiredMoney = recipe.getRequiredMoney();
        Material resultItem = recipe.getResultItem();
        Integer resultCmd = recipe.getResultCmd();
        String displayName = recipe.getDisplayName();

        // ── 1. Envanter boş slot kontrolü ──
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(prefix + ColorUtil.colorize(
                    plugin.getConfig().getString("messages.inventory-full",
                            "\u0026cEnvanterinizde yeterli yer yok!")));
            return;
        }

        // ── 2. Elmas eşya durability kontrolü (FULL olmalı) ──
        if (!hasFullDurabilityItem(player, requiredDiamondItem, requiredDiamondCmd)) {
            if (hasAnyItem(player, requiredDiamondItem, requiredDiamondCmd)) {
                player.sendMessage(prefix + ColorUtil.colorize(
                        plugin.getConfig().getString("messages.diamond-not-full-durability",
                                "\u0026cElmas eşyanızın durability'si tam olmalıdır!")));
            } else {
                player.sendMessage(prefix + ColorUtil.colorize(
                        plugin.getConfig().getString("messages.not-enough-items",
                                "\u0026cGerekli eşyalar envanterinizde bulunamadı!")));
            }
            return;
        }

        // ── 3. Demirci Şablonu (Netherite Upgrade) kontrolü ──
        if (requiredSmithingTemplate > 0 && !hasEnoughItems(player, Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, requiredSmithingTemplate)) {
            player.sendMessage(prefix + ColorUtil.colorize(
                    plugin.getConfig().getString("messages.not-enough-items",
                            "\u0026cGerekli eşyalar envanterinizde bulunamadı!")));
            return;
        }

        // ── 4. Netherite külçe kontrolü ──
        if (!hasEnoughItems(player, Material.NETHERITE_INGOT, requiredNetherite)) {
            player.sendMessage(prefix + ColorUtil.colorize(
                    plugin.getConfig().getString("messages.not-enough-items",
                            "\u0026cGerekli eşyalar envanterinizde bulunamadı!")));
            return;
        }

        // ── 4. Elmas kontrolü ──
        if (!hasEnoughItems(player, Material.DIAMOND, requiredDiamond)) {
            player.sendMessage(prefix + ColorUtil.colorize(
                    plugin.getConfig().getString("messages.not-enough-items",
                            "\u0026cGerekli eşyalar envanterinizde bulunamadı!")));
            return;
        }

        // ── 5. Vault bakiye kontrolü ──
        Economy econ = plugin.getEconomy();
        if (econ == null) {
            player.sendMessage(prefix + ColorUtil.colorize(
                    plugin.getConfig().getString("messages.vault-not-found",
                            "\u0026cEkonomi sistemi bulunamadı!")));
            return;
        }
        if (!econ.has(player, requiredMoney)) {
            String moneyMsg = plugin.getConfig().getString("messages.not-enough-money",
                            "\u0026cYeterli bakiyeniz bulunmuyor! \u00267Gereken: \u0026#ffd500{cost} Dinar")
                    .replace("{cost}", String.format("%,.0f", requiredMoney));
            player.sendMessage(prefix + ColorUtil.colorize(moneyMsg));
            return;
        }

        // ══════════════════════════════════════════════════════
        //  TÜM KONTROLLER GEÇTİ — EŞYA SİLME (ROLLBACK DESTEKLİ)
        //  Orijinal eşya korunarak enchantment transferi sağlanır.
        // ══════════════════════════════════════════════════════

        // Adım 1: Elmas eşyayı sil ve orijinalini sakla (enchantment transfer + rollback için)
        ItemStack originalDiamondItem = removeAndReturnFullDurabilityItem(player, requiredDiamondItem, requiredDiamondCmd);
        if (originalDiamondItem == null) {
            player.sendMessage(prefix + ColorUtil.colorize(
                    plugin.getConfig().getString("messages.not-enough-items",
                            "\u0026cGerekli eşyalar bulunamadı!")));
            return;
        }

        // Adım 2: Demirci Şablonlarını sil
        if (requiredSmithingTemplate > 0 && !removeItems(player, Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, requiredSmithingTemplate)) {
            // Rollback: Orijinal elmas eşyayı geri ver
            giveItemSafely(player, originalDiamondItem);
            player.sendMessage(prefix + ColorUtil.colorize(
                    plugin.getConfig().getString("messages.not-enough-items",
                            "\u0026cGerekli eşyalar bulunamadı!")));
            return;
        }

        // Adım 3: Netherite külçeleri sil
        if (!removeItems(player, Material.NETHERITE_INGOT, requiredNetherite)) {
            // Rollback: Orijinal elmas eşyayı ve (varsa) şablonu geri ver
            giveItemSafely(player, originalDiamondItem);
            if (requiredSmithingTemplate > 0) {
                giveItemSafely(player, new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, requiredSmithingTemplate));
            }
            player.sendMessage(prefix + ColorUtil.colorize(
                    plugin.getConfig().getString("messages.not-enough-items",
                            "\u0026cGerekli eşyalar bulunamadı!")));
            return;
        }

        // Adım 4: Elmasları sil
        if (!removeItems(player, Material.DIAMOND, requiredDiamond)) {
            // Rollback: Orijinal elmas eşya + netherite külçeleri + şablon geri ver
            giveItemSafely(player, originalDiamondItem);
            giveItemSafely(player, new ItemStack(Material.NETHERITE_INGOT, requiredNetherite));
            if (requiredSmithingTemplate > 0) {
                giveItemSafely(player, new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, requiredSmithingTemplate));
            }
            player.sendMessage(prefix + ColorUtil.colorize(
                    plugin.getConfig().getString("messages.not-enough-items",
                            "\u0026cGerekli eşyalar bulunamadı!")));
            return;
        }

        // Adım 5: Vault'tan para çek
        EconomyResponse response = econ.withdrawPlayer(player, requiredMoney);
        if (!response.transactionSuccess()) {
            // Rollback: Tüm eşyaları orijinal halleriyle geri ver
            giveItemSafely(player, originalDiamondItem);
            giveItemSafely(player, new ItemStack(Material.NETHERITE_INGOT, requiredNetherite));
            if (requiredSmithingTemplate > 0) {
                giveItemSafely(player, new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, requiredSmithingTemplate));
            }
            giveItemSafely(player, new ItemStack(Material.DIAMOND, requiredDiamond));
            String moneyMsg = plugin.getConfig().getString("messages.not-enough-money",
                            "\u0026cYeterli bakiyeniz bulunmuyor! \u00267Gereken: \u0026#ffd500{cost} Dinar")
                    .replace("{cost}", String.format("%,.0f", requiredMoney));
            player.sendMessage(prefix + ColorUtil.colorize(moneyMsg));
            return;
        }

        // ══════════════════════════════════════════════════════
        //  BAŞARILI — Enchantment transfer ile netherite eşya oluştur ve ver
        // ══════════════════════════════════════════════════════

        ItemStack netheriteResult = createNetheriteResult(resultItem, resultCmd, originalDiamondItem);
        giveItemSafely(player, netheriteResult);

        String successMsg = plugin.getConfig().getString("messages.craft-success",
                        "\u0026#00D420{item} \u00267başarıyla üretildi!")
                .replace("{item}", ColorUtil.colorize(displayName));
        player.sendMessage(prefix + ColorUtil.colorize(successMsg));
    }

    // ═══════════════════════════════════════════════════════════
    //  ENCHANTMENT / META TRANSFER
    // ═══════════════════════════════════════════════════════════

    /**
     * Elmas eşyanın tüm metadata'sını (enchantment, custom name, lore, repair cost,
     * CustomModelData ve tüm NBT/PersistentDataContainer verilerini) koruyarak
     * yeni netherite eşyaya dönüştürür.
     *
     * Eşyayı klonlayıp türünü değiştirerek, Spigot'un tüm NBT'yi aktarmasını sağlıyoruz.
     *
     * @param resultMaterial  Sonuç netherite material (ör: NETHERITE_HELMET)
     * @param resultCmd       Sonuç CustomModelData (null olabilir)
     * @param sourceDiamondItem Orijinal elmas eşya (NBT kaynağı)
     * @return Büyülü ve NBT'li netherite eşya
     */
    private ItemStack createNetheriteResult(Material resultMaterial, Integer resultCmd, ItemStack sourceDiamondItem) {
        // Eşyayı klonla ve türünü değiştir. 
        // Bu işlem modern Spigot'ta (1.20+) uyumlu materiallar arası (kılıç->kılıç, zırh->zırh)
        // geçişlerde tüm ItemMeta'yı, CustomModelData'yı ve PDC (NBT) verilerini kusursuz korur.
        ItemStack result = sourceDiamondItem.clone();
        result.setType(resultMaterial);
        
        // Eğer Config'den gelen bir CustomModelData varsa, orijinali ez ve yenisini uygula
        if (resultCmd != null) {
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(resultCmd);
                result.setItemMeta(meta);
            }
        }
        
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    //  YARDIMCI METODLAR
    // ═══════════════════════════════════════════════════════════

    /**
     * Oyuncunun envanterinde belirtilen material'den tam durability'li
     * (hasarsız) ve (varsa) istenen CustomModelData'ya sahip eşya olup olmadığını kontrol eder.
     */
    private boolean hasFullDurabilityItem(Player player, Material material, Integer requiredCmd) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != material) continue;
            ItemMeta meta = item.getItemMeta();
            
            if (!matchesCustomModelData(meta, requiredCmd)) continue;

            if (meta instanceof Damageable damageable) {
                if (damageable.getDamage() == 0) return true;
            } else {
                return true; // Damageable olmayan eşya → her zaman "full"
            }
        }
        return false;
    }

    /**
     * Oyuncunun envanterinde belirtilen material'den herhangi bir eşya
     * (hasarlı dahil) ve (varsa) istenen CustomModelData'ya sahip eşya olup olmadığını kontrol eder.
     */
    private boolean hasAnyItem(Player player, Material material, Integer requiredCmd) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                if (matchesCustomModelData(item.getItemMeta(), requiredCmd)) return true;
            }
        }
        return false;
    }

    /**
     * Belirtilen material'den tam durability'li ilk eşyayı envanterden siler
     * ve orijinal eşyanın klonunu döndürür (enchantment transfer + rollback için).
     *
     * @param player   Hedef oyuncu
     * @param material Aranacak material
     * @param requiredCmd Aranacak CustomModelData (null olabilir)
     * @return Silinen eşyanın klonu veya null (bulunamadıysa)
     */
    private ItemStack removeAndReturnFullDurabilityItem(Player player, Material material, Integer requiredCmd) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) continue;
            ItemMeta meta = item.getItemMeta();
            
            if (!matchesCustomModelData(meta, requiredCmd)) continue;

            if (meta instanceof Damageable damageable) {
                if (damageable.getDamage() == 0) {
                    ItemStack removed = item.clone(); // Silmeden önce klonla
                    player.getInventory().setItem(i, null);
                    return removed;
                }
            } else {
                ItemStack removed = item.clone();
                player.getInventory().setItem(i, null);
                return removed;
            }
        }
        return null;
    }

    /**
     * ItemMeta'nın istenilen CustomModelData ile eşleşip eşleşmediğini kontrol eder.
     */
    private boolean matchesCustomModelData(ItemMeta meta, Integer requiredCmd) {
        if (requiredCmd == null) return true; // Şart yoksa eşleşir
        if (meta == null) return false;
        if (!meta.hasCustomModelData()) return false;
        return meta.getCustomModelData() == requiredCmd;
    }

    /**
     * Belirtilen material'den yeterli miktarda eşya olup olmadığını kontrol eder.
     */
    private boolean hasEnoughItems(Player player, Material material, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
                if (count >= amount) return true;
            }
        }
        return false;
    }

    /**
     * Belirtilen material'den belirtilen miktarda eşyayı envanterden siler.
     * Birden fazla slota dağılmış eşyaları da doğru şekilde siler.
     *
     * @return Silme başarılıysa true
     */
    private boolean removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) continue;

            int stackAmount = item.getAmount();
            if (stackAmount <= remaining) {
                player.getInventory().setItem(i, null);
                remaining -= stackAmount;
            } else {
                item.setAmount(stackAmount - remaining);
                remaining = 0;
            }
        }
        return remaining == 0;
    }

    /**
     * Eşyayı güvenli şekilde oyuncuya verir.
     * Envanter doluysa eşya yere düşürülür (kaybolmaz).
     * 64'ten büyük stackler otomatik olarak bölünür.
     */
    private void giveItemSafely(Player player, ItemStack item) {
        int maxStack = item.getMaxStackSize();
        int total = item.getAmount();

        while (total > 0) {
            int give = Math.min(total, maxStack);
            ItemStack stack = item.clone();
            stack.setAmount(give);
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);

            // Sığmayan eşyaları yere düşür (asla kaybolmaz)
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            total -= give;
        }
    }
}
