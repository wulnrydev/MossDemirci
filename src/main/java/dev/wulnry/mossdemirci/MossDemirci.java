package dev.wulnry.mossdemirci;

import dev.wulnry.mossdemirci.command.DemirciCommand;
import dev.wulnry.mossdemirci.gui.DemirciGUI;
import dev.wulnry.mossdemirci.listener.GUIListener;
import dev.wulnry.mossdemirci.listener.SmithingListener;
import dev.wulnry.mossdemirci.util.ColorUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MossDemirci - Özel Netherite Üretim Sistemi
 * Vanilla netherite üretimini engelleyerek özel GUI tabanlı bir üretim sistemi sunar.
 *
 * @author wulnrydev
 */
public final class MossDemirci extends JavaPlugin {

    private Economy economy;
    private DemirciGUI demirciGUI;
    private GUIListener guiListener;

    @Override
    public void onEnable() {
        // Varsayılan config'i kaydet (yoksa oluştur)
        saveDefaultConfig();

        // Vault ekonomi sistemi kurulumu
        if (!setupEconomy()) {
            getLogger().severe("Vault ekonomi sistemi bulunamadı! Eklenti devre dışı bırakılıyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // GUI Manager oluştur (recipe'leri cache'ler)
        demirciGUI = new DemirciGUI(this);

        // Komut kayıtları (null-safe)
        PluginCommand demirciCmd = getCommand("demirci");
        if (demirciCmd != null) {
            DemirciCommand demirciCommand = new DemirciCommand(this);
            demirciCmd.setExecutor(demirciCommand);
            demirciCmd.setTabCompleter(demirciCommand);
        } else {
            getLogger().severe("'demirci' komutu plugin.yml'de bulunamadı! Komut sistemi devre dışı.");
        }

        // Event Listener kayıtları
        guiListener = new GUIListener(this);
        getServer().getPluginManager().registerEvents(guiListener, this);
        getServer().getPluginManager().registerEvents(new SmithingListener(this), this);

        getLogger().info("MossDemirci v" + getDescription().getVersion() + " başarıyla etkinleştirildi!");
    }

    @Override
    public void onDisable() {
        // Açık GUI'leri temizle
        if (guiListener != null) {
            guiListener.closeAllGUIs();
        }

        // Renk cache'ini temizle
        ColorUtil.clearCache();

        getLogger().info("MossDemirci devre dışı bırakıldı.");
    }

    /**
     * Vault ekonomi sistemini bağlar.
     *
     * @return Bağlantı başarılıysa true
     */
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return true;
    }

    /**
     * Config, GUI ve recipe cache'ini yeniden yükler.
     * Açık GUI'ler kapatılır çünkü eski layout ile tutarsızlık oluşabilir.
     */
    public void reloadPlugin() {
        // Önce açık GUI'leri kapat (eski layout ile tutarsızlık önlenir)
        if (guiListener != null) {
            guiListener.closeAllGUIs();
        }

        reloadConfig();
        ColorUtil.clearCache();
        demirciGUI = new DemirciGUI(this);
    }

    public Economy getEconomy() {
        return economy;
    }

    public DemirciGUI getDemirciGUI() {
        return demirciGUI;
    }
}
