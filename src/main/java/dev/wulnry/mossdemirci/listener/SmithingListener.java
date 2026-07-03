package dev.wulnry.mossdemirci.listener;

import dev.wulnry.mossdemirci.MossDemirci;
import dev.wulnry.mossdemirci.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vanilla Smithing Table üzerinden netherite üretimini engelleyen listener.
 * İki katmanlı koruma:
 * 1. PrepareSmithingEvent — Sonuç önizlemesini kaldırır
 * 2. InventoryClickEvent — Result slot'undan alma girişimini engeller (hacked client koruması)
 *
 * Mesaj spam koruması: Aynı oyuncuya 3 saniye içinde tekrar mesaj gönderilmez.
 */
public class SmithingListener implements Listener {

    private final MossDemirci plugin;

    /** Mesaj spam koruması — oyuncu UUID → son mesaj gönderim zamanı (ms) */
    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<>();

    /** Mesajlar arası minimum süre (milisaniye) */
    private static final long MESSAGE_COOLDOWN_MS = 3000L;

    public SmithingListener(MossDemirci plugin) {
        this.plugin = plugin;
    }

    /**
     * Katman 1: PrepareSmithingEvent — Sonuç netherite ise önizlemeyi kaldırır.
     * Bu, normal oyuncuların Smithing Table'da netherite görmesini engeller.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (!plugin.getConfig().getBoolean("block-vanilla-netherite", true)) {
            return;
        }

        ItemStack result = event.getResult();
        if (result == null) return;

        if (isNetheriteEquipment(result.getType())) {
            // Sonucu null yaparak üretimi engelle
            event.setResult(null);

            // Oyuncuya bilgi mesajı gönder (cooldown kontrolü ile)
            if (event.getView().getPlayer() instanceof Player player) {
                sendCooldownMessage(player);
            }
        }
    }

    /**
     * Katman 2: InventoryClickEvent — Smithing Table result slot'undan
     * netherite eşya alınmasını engeller.
     * Hacked client'lar PrepareSmithingEvent'i bypass edebilir,
     * bu katman ek güvenlik sağlar.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithingClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof SmithingInventory)) return;
        if (!plugin.getConfig().getBoolean("block-vanilla-netherite", true)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Result slot'undan alma girişimi
        if (event.getSlotType() == InventoryType.SlotType.RESULT) {
            ItemStack current = event.getCurrentItem();
            if (current != null && isNetheriteEquipment(current.getType())) {
                event.setCancelled(true);
                sendCooldownMessage(player);
            }
        }

        // Shift-click ile result slot'undan alma girişimi (herhangi bir slottan)
        if (event.isShiftClick()) {
            ItemStack result = event.getView().getTopInventory().getItem(
                    event.getView().getTopInventory().getSize() - 1);
            if (result != null && isNetheriteEquipment(result.getType())) {
                // Shift-click sonuçta netherite varsa engelle
                if (event.getSlotType() == InventoryType.SlotType.RESULT) {
                    event.setCancelled(true);
                    sendCooldownMessage(player);
                }
            }
        }
    }

    /**
     * Oyuncuya cooldown kontrolü ile smithing engel mesajı gönderir.
     * Aynı oyuncuya MESSAGE_COOLDOWN_MS ms içinde tekrar mesaj gönderilmez.
     */
    private void sendCooldownMessage(Player player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        Long lastTime = messageCooldowns.get(uuid);
        if (lastTime != null && now - lastTime < MESSAGE_COOLDOWN_MS) {
            return; // Cooldown aktif, mesaj gönderme
        }
        messageCooldowns.put(uuid, now);

        String prefix = ColorUtil.colorize(
                plugin.getConfig().getString("prefix", ""));
        String message = ColorUtil.colorize(
                plugin.getConfig().getString("messages.smithing-blocked",
                        "\u0026cVanilla yöntemle netherite eşya üretemezsiniz! \u00267/demirci \u0026ckomutunu kullanın."));
        player.sendMessage(prefix + message);
    }

    /**
     * Verilen material'in netherite ekipman/alet olup olmadığını kontrol eder.
     */
    private boolean isNetheriteEquipment(Material material) {
        return switch (material) {
            case NETHERITE_HELMET,
                 NETHERITE_CHESTPLATE,
                 NETHERITE_LEGGINGS,
                 NETHERITE_BOOTS,
                 NETHERITE_SWORD,
                 NETHERITE_PICKAXE,
                 NETHERITE_AXE,
                 NETHERITE_SHOVEL,
                 NETHERITE_HOE -> true;
            default -> false;
        };
    }
}
