package dev.wulnry.mossdemirci.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Custom InventoryHolder — Demirci GUI'sini diğer envanterlerden ayırt etmek için kullanılır.
 * UUID-based tracking yerine tip-güvenli bir kontrol sağlar.
 * InventoryClickEvent/DragEvent'te "bu envanter demirci menüsü mü?" sorusuna
 * instanceof kontrolüyle yanıt verir.
 */
public class DemirciHolder implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}
