package dev.wulnry.mossdemirci.gui;

import org.bukkit.Material;

/**
 * Config'den okunan üretim tariflerini temsil eden immutable veri sınıfı.
 * Her tarif bir slot, gereksinimler (eşya + para) ve sonuç eşyası içerir.
 * Config her okunduğunda bu sınıfın örnekleri oluşturulup Map'e cache'lenir,
 * böylece her tıklamada config parse edilmez.
 */
public final class CraftRecipe {

    private final int slot;
    private final Material requiredDiamondItem;
    private final Integer requiredDiamondCmd; // CustomModelData
    private final int requiredSmithingTemplate;
    private final int requiredNetheriteIngot;
    private final int requiredDiamond;
    private final double requiredMoney;
    private final Material resultItem;
    private final Integer resultCmd; // CustomModelData
    private final String displayName;

    public CraftRecipe(int slot, Material requiredDiamondItem, Integer requiredDiamondCmd, 
                       int requiredSmithingTemplate, int requiredNetheriteIngot, int requiredDiamond, double requiredMoney, 
                       Material resultItem, Integer resultCmd, String displayName) {
        this.slot = slot;
        this.requiredDiamondItem = requiredDiamondItem;
        this.requiredDiamondCmd = requiredDiamondCmd;
        this.requiredSmithingTemplate = requiredSmithingTemplate;
        this.requiredNetheriteIngot = requiredNetheriteIngot;
        this.requiredDiamond = requiredDiamond;
        this.requiredMoney = requiredMoney;
        this.resultItem = resultItem;
        this.resultCmd = resultCmd;
        this.displayName = displayName;
    }

    public int getSlot() {
        return slot;
    }

    public Material getRequiredDiamondItem() {
        return requiredDiamondItem;
    }

    public Integer getRequiredDiamondCmd() {
        return requiredDiamondCmd;
    }

    public int getRequiredSmithingTemplate() {
        return requiredSmithingTemplate;
    }

    public int getRequiredNetheriteIngot() {
        return requiredNetheriteIngot;
    }

    public int getRequiredDiamond() {
        return requiredDiamond;
    }

    public double getRequiredMoney() {
        return requiredMoney;
    }

    public Material getResultItem() {
        return resultItem;
    }

    public Integer getResultCmd() {
        return resultCmd;
    }

    public String getDisplayName() {
        return displayName;
    }
}
