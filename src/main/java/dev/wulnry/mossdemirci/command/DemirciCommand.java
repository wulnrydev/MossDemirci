package dev.wulnry.mossdemirci.command;

import dev.wulnry.mossdemirci.MossDemirci;
import dev.wulnry.mossdemirci.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /demirci komutu handler'ı.
 * /demirci       → GUI menüsünü açar (mossdemirci.use)
 * /demirci reload → Config'i yeniden yükler (mossdemirci.reload)
 */
public class DemirciCommand implements CommandExecutor, TabCompleter {

    private final MossDemirci plugin;

    public DemirciCommand(MossDemirci plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = ColorUtil.colorize(plugin.getConfig().getString("prefix", ""));

        // /demirci reload
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mossdemirci.reload")) {
                sender.sendMessage(prefix + ColorUtil.colorize(
                        plugin.getConfig().getString("messages.no-permission",
                                "&cBu komutu kullanma yetkiniz yok!")));
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(prefix + ColorUtil.colorize(
                    plugin.getConfig().getString("messages.reload-success",
                            "&#00D420Config başarıyla yeniden yüklendi!")));
            return true;
        }

        // /demirci → GUI aç (sadece oyuncular)
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix + ColorUtil.colorize(
                    plugin.getConfig().getString("messages.player-only",
                            "&cBu komut sadece oyuncular tarafından kullanılabilir!")));
            return true;
        }

        if (!player.hasPermission("mossdemirci.use")) {
            player.sendMessage(prefix + ColorUtil.colorize(
                    plugin.getConfig().getString("messages.no-permission",
                            "&cBu komutu kullanma yetkiniz yok!")));
            return true;
        }

        plugin.getDemirciGUI().openMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("mossdemirci.reload")) {
            List<String> completions = new ArrayList<>();
            if ("reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
