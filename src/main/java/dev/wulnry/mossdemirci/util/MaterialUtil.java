package dev.wulnry.mossdemirci.util;

import org.bukkit.Material;

import java.util.logging.Logger;

/**
 * Material parse utility — String'den Material dönüşümü sağlar.
 * Null-safe ve hata toleranslıdır.
 * DemirciGUI ve GUIListener'daki duplike parseMaterial() metodlarını
 * tek bir noktada birleştirir.
 */
public final class MaterialUtil {

    private MaterialUtil() {
        // Utility class — örneklenemez
    }

    /**
     * String'den Material parse eder.
     * Null, boş string veya geçersiz material adı durumlarında
     * fallback değerini döndürür ve opsiyonel olarak uyarı loglar.
     *
     * @param name     Material adı (ör: "DIAMOND_HELMET")
     * @param fallback Geçersiz material durumunda döndürülecek varsayılan
     * @param logger   Uyarı mesajı için logger (null olabilir)
     * @return Parse edilmiş Material veya fallback
     */
    public static Material parseMaterial(String name, Material fallback, Logger logger) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            if (logger != null) {
                logger.warning("Geçersiz material: " + name + " → Varsayılan kullanılıyor: " + fallback);
            }
            return fallback;
        }
    }

    /**
     * Logger olmadan Material parse eder.
     */
    public static Material parseMaterial(String name, Material fallback) {
        return parseMaterial(name, fallback, null);
    }
}
