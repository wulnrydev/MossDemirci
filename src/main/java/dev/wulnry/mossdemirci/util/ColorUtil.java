package dev.wulnry.mossdemirci.util;

import net.md_5.bungee.api.ChatColor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hex renk kodu (&#RRGGBB) ve standart renk kodu (&X) dönüştürücü.
 * Dönüştürülen string'ler ConcurrentHashMap ile cache'lenir,
 * aynı mesajın tekrar tekrar işlenmesi önlenir.
 */
public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final ConcurrentHashMap<String, String> COLOR_CACHE = new ConcurrentHashMap<>();

    private ColorUtil() {
        // Utility class - örneklenemez
    }

    /**
     * Verilen metindeki tüm renk kodlarını (hem &#RRGGBB hem &X) dönüştürür.
     * Sonuç cache'lenir, tekrar çağrılarda doğrudan cache'den döner.
     *
     * @param text Renk kodları içeren metin
     * @return Dönüştürülmüş metin
     */
    public static String colorize(String text) {
        if (text == null || text.isEmpty()) return text;
        return COLOR_CACHE.computeIfAbsent(text, ColorUtil::translateColors);
    }

    /**
     * Hem hex renk kodlarını hem standart & kodlarını dönüştürür.
     * &#RRGGBB → §x§R§R§G§G§B§B formatına çevrilir.
     * &X → §X formatına çevrilir (ChatColor.translateAlternateColorCodes).
     */
    private static String translateColors(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder buffer = new StringBuilder(text.length() + 32);

        while (matcher.find()) {
            String hexColor = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hexColor.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Renk cache'ini temizler.
     * Config yeniden yüklendiğinde veya plugin kapatıldığında çağrılmalıdır.
     */
    public static void clearCache() {
        COLOR_CACHE.clear();
    }
}
