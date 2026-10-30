package pl.minestar.nagrodysocial;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NagrodySocialPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, Long> playtime = new ConcurrentHashMap<>();
    private final Map<UUID, EnumSet<Social>> linked = new ConcurrentHashMap<>();
    private final Map<UUID, EnumSet<Social>> claimed = new ConcurrentHashMap<>();
    private final Map<String, UUID> codes = new ConcurrentHashMap<>();
    private JDA jda;

    private enum Social { DISCORD, YOUTUBE, TIKTOK }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        PluginCommand cmd = getCommand("nagroda");
        if (cmd != null) cmd.setExecutor(this::command);

        long everySecond = 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                playtime.merge(p.getUniqueId(), 1L, Long::sum);
            }
        }, everySecond, everySecond);

        startDiscord();
        getLogger().info("NagrodySocial wlaczony.");
    }

    @Override
    public void onDisable() {
        if (jda != null) jda.shutdown();
    }

    private boolean command(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        openGui(p);
        return true;
    }

    private void openGui(Player p) {
        int size = getConfig().getInt("gui.size", 27);
        String title = color(getConfig().getString("gui.title", "&8» &5&lNAGRODY SOCIAL &8«"));
        Inventory inv = Bukkit.createInventory(null, size, title);

        putSocial(inv, p, Social.DISCORD);
        putSocial(inv, p, Social.YOUTUBE);
        putSocial(inv, p, Social.TIKTOK);

        p.openInventory(inv);
    }

    private void putSocial(Inventory inv, Player p, Social social) {
        String key = social.name().toLowerCase(Locale.ROOT);
        int slot = getConfig().getInt("rewards." + key + ".slot");
        String name = getConfig().getString("rewards." + key + ".name", social.name());
        String reward = getConfig().getString("rewards." + key + ".reward-name", "&4&lFire&6&lbox &e&l1x");
        String link = getConfig().getString("rewards." + key + ".link", "");
        Material mat = switch (social) {
            case DISCORD -> Material.PAPER;
            case YOUTUBE -> Material.RED_DYE;
            case TIKTOK -> Material.NOTE_BLOCK;
        };

        boolean isLinked = linked.getOrDefault(p.getUniqueId(), EnumSet.noneOf(Social.class)).contains(social);
        boolean isClaimed = claimed.getOrDefault(p.getUniqueId(), EnumSet.noneOf(Social.class)).contains(social);

        List<String> lore = new ArrayList<>();
        lore.add(color("&7» &fPołączono: " + (isLinked ? "&a✔" : "&c✘")));
        lore.add("");
        lore.add(color("&7» &fNagroda:"));
        lore.add(color("&7- " + reward));
        lore.add("");
        if (!isLinked) {
            lore.add(color("&eKliknij &f🖱 &eaby połączyć!"));
            lore.add(color("&8" + link));
        } else if (isClaimed) {
            lore.add(color("&cNagroda została już odebrana."));
        } else if (getPlaytime(p) < required()) {
            lore.add(color("&eKliknij &f🖱 &eaby odebrać nagrodę!"));
        } else {
            lore.add(color("&aKliknij &f🖱 &aaby odebrać nagrodę!"));
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(lore);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!ChatColor.stripColor(e.getView().getTitle()).contains("NAGRODY SOCIAL")) return;
        e.setCancelled(true);

        for (Social s : Social.values()) {
            String key = s.name().toLowerCase(Locale.ROOT);
            if (e.getRawSlot() == getConfig().getInt("rewards." + key + ".slot")) {
                handleSocial(p, s);
                return;
            }
        }
    }

    private void handleSocial(Player p, Social s) {
        UUID id = p.getUniqueId();
        if (claimed.getOrDefault(id, EnumSet.noneOf(Social.class)).contains(s)) {
            msg(p, "already-taken");
            return;
        }

        if (!linked.getOrDefault(id, EnumSet.noneOf(Social.class)).contains(s)) {
            if (s == Social.DISCORD) {
                String code = generateCode();
                codes.put(code, id);
                p.sendMessage(color("&9Discord &8» &fTwój kod: &e" + code));
                p.sendMessage(color("&7Wpisz na Discordzie: &e/nagroda " + code));
            } else {
                String link = getConfig().getString("rewards." + s.name().toLowerCase(Locale.ROOT) + ".link", "");
                p.sendMessage(color("&eOtwórz link: &f" + link));
                p.sendMessage(color(getConfig().getString("messages.verification-pending")));
            }
            return;
        }

        long left = required() - getPlaytime(p);
        if (left > 0) {
            p.sendMessage(color(getConfig().getString("messages.time-left")
                    .replace("%time%", formatTime(left))));
            return;
        }

        String key = s.name().toLowerCase(Locale.ROOT);
        String cmd = getConfig().getString("rewards." + key + ".command");
        if (cmd != null && !cmd.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        }
        claimed.computeIfAbsent(id, x -> EnumSet.noneOf(Social.class)).add(s);
        p.sendMessage(color(getConfig().getString("messages.reward-taken")));
        openGui(p);
    }

    private long required() {
        return getConfig().getLong("playtime-required-seconds", 3600);
    }

    private long getPlaytime(Player p) {
        return playtime.getOrDefault(p.getUniqueId(), 0L);
    }

    private String generateCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        String code;
        do {
            StringBuilder b = new StringBuilder();
            Random r = new Random();
            for (int i = 0; i < 8; i++) b.append(chars.charAt(r.nextInt(chars.length())));
            code = b.toString();
        } while (codes.containsKey(code));
        return code;
    }

    private void startDiscord() {
        if (!getConfig().getBoolean("discord.enabled", false)) return;
        String token = getConfig().getString("discord.bot-token", "");
        if (token == null || token.isBlank() || token.startsWith("WSTAW_")) {
            getLogger().warning("Discord bot nie zostal uruchomiony - brak tokenu.");
            return;
        }
        try {
            jda = JDABuilder.createDefault(token).addEventListeners(new ListenerAdapter() {
                @Override
                public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
                    if (!e.getName().equalsIgnoreCase("nagroda")) return;
                    String code = e.getOption("kod") != null ? e.getOption("kod").getAsString() : "";
                    UUID uuid = codes.remove(code);
                    if (uuid == null) {
                        e.reply("❌ Kod jest nieprawidłowy albo wygasł.").setEphemeral(true).queue();
                        return;
                    }
                    linked.computeIfAbsent(uuid, x -> EnumSet.noneOf(Social.class)).add(Social.DISCORD);
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.sendMessage(color("&a✔ Discord został połączony!"));
                    }
                    e.reply("✅ Połączono Discord z Minecraft.").setEphemeral(true).queue();
                }
            }).build();

            jda.awaitReady();
            Guild guild = jda.getGuildById(getConfig().getString("discord.guild-id", ""));
            if (guild != null) {
                guild.upsertCommand("nagroda", "Połącz Discord z Minecraft")
                        .addOption(OptionType.STRING, "kod", "Kod z Minecraft", true)
                        .queue();
            }
        } catch (Exception ex) {
            getLogger().severe("Nie udało się uruchomić bota Discord: " + ex.getMessage());
        }
    }

    private String formatTime(long sec) {
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private void msg(Player p, String path) {
        p.sendMessage(color(getConfig().getString("messages." + path, "")));
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        playtime.putIfAbsent(e.getPlayer().getUniqueId(), 0L);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        // Wersja demonstracyjna trzyma dane w RAM.
        // W produkcyjnej wersji należy zapisać playtime/linked/claimed do SQLite/YAML.
    }
}
