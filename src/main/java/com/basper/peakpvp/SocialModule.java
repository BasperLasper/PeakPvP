package com.basper.peakpvp;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SocialModule implements Listener, PluginMessageListener {
    private final PeakPvPPlugin plugin;
    private final ColorModule colors;
    private volatile int networkPlayers;
    private int announcementIndex;

    SocialModule(PeakPvPPlugin plugin, ColorModule colors) {
        this.plugin = plugin;
        this.colors = colors;
    }

    void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", this);
        long tabTicks = Math.max(20, plugin.getConfig().getLong("chat.update-ticks", 100));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            requestNetworkCount();
            refreshTab();
        }, 1, tabTicks);
        long announcementTicks = Math.max(200, plugin.getConfig().getLong("auto-messages.interval-seconds", 180) * 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::announce, announcementTicks, announcementTicks);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void chat(AsyncChatEvent event) {
        Player source = event.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message()).replace("&", "");
        pingMentions(source, plain);
        String message = highlightMentions(plain);
        String format = plugin.getConfig().getString("chat.format", "<prefix>&f<name><suffix> &8» &f<message>");
        event.renderer((ignored, displayName, originalMessage, viewer) -> Messages.legacy(format
                .replace("<prefix>", luckPermsMeta(source, true))
                .replace("<name>", colors.nameCode(source) + source.getName())
                .replace("<suffix>", luckPermsMeta(source, false))
                .replace("<message>", colors.chatCode(source) + message)));
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        event.joinMessage(Messages.legacy("&7[&a+&7] " + colors.nameCode(event.getPlayer()) + event.getPlayer().getName()));
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshTab, 1);
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        event.quitMessage(Messages.legacy("&7[&c-&7] " + colors.nameCode(event.getPlayer()) + event.getPlayer().getName()));
    }

    void refreshTab() {
        int online = Bukkit.getOnlinePlayers().size();
        String tps = String.format(Locale.ROOT, "%.1f", Math.min(20, Bukkit.getTPS()[0]));
        String header = replaceTabValues(plugin.getConfig().getString("chat.tab-header", ""), online, tps);
        String footer = replaceTabValues(plugin.getConfig().getString("chat.tab-footer", ""), online, tps);
        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = plugin.getConfig().getString("chat.tab-name", "<prefix>&f<name><suffix> &7<ping>ms")
                    .replace("<prefix>", luckPermsMeta(player, true))
                    .replace("<name>", colors.nameCode(player) + player.getName())
                    .replace("<suffix>", luckPermsMeta(player, false))
                    .replace("<ping>", Integer.toString(player.getPing()));
            player.playerListName(Messages.legacy(name));
            player.sendPlayerListHeaderAndFooter(Messages.legacy(header), Messages.legacy(footer));
        }
    }

    private String luckPermsMeta(Player player, boolean prefix) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return "";
        try {
            String value = prefix
                    ? LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(player).getPrefix()
                    : LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(player).getSuffix();
            return value == null ? "" : value;
        } catch (IllegalStateException | NoClassDefFoundError exception) {
            return "";
        }
    }

    private String highlightMentions(String message) {
        String result = message;
        String colour = plugin.getConfig().getString("chat.mentions.highlight-color", "&e");
        for (Player target : Bukkit.getOnlinePlayers()) {
            Pattern pattern = playerPattern(target);
            result = pattern.matcher(result).replaceAll(Matcher.quoteReplacement(colour + "@" + target.getName() + "&f"));
        }
        return result;
    }

    private void pingMentions(Player sender, String message) {
        if (!plugin.getConfig().getBoolean("chat.mentions.enabled", true)) return;
        List<UUID> targets = Bukkit.getOnlinePlayers().stream()
                .filter(target -> !target.equals(sender) && playerPattern(target).matcher(message).find())
                .map(Player::getUniqueId).toList();
        if (targets.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Sound sound;
            try { sound = Sound.valueOf(plugin.getConfig().getString("chat.mentions.sound", "BLOCK_NOTE_BLOCK_PLING")); }
            catch (IllegalArgumentException exception) { sound = Sound.BLOCK_NOTE_BLOCK_PLING; }
            float volume = (float) plugin.getConfig().getDouble("chat.mentions.volume", 0.8);
            float pitch = (float) plugin.getConfig().getDouble("chat.mentions.pitch", 1.4);
            for (UUID id : targets) {
                Player target = Bukkit.getPlayer(id);
                if (target != null) {
                    target.playSound(target.getLocation(), sound, volume, pitch);
                    target.sendActionBar(Messages.legacy("&c" + sender.getName() + " &fmentioned you in chat"));
                }
            }
        });
    }

    private Pattern playerPattern(Player player) {
        return Pattern.compile("(?i)(?<![A-Z0-9_@])@?" + Pattern.quote(player.getName()) + "(?![A-Z0-9_])");
    }

    private void announce() {
        if (!plugin.getConfig().getBoolean("auto-messages.enabled", true)) return;
        List<String> messages = plugin.getConfig().getStringList("auto-messages.messages");
        if (messages.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) return;
        Bukkit.broadcast(Messages.legacy(messages.get(announcementIndex++ % messages.size()).replace("\\n", "\n")));
    }

    private String replaceTabValues(String value, int online, String tps) {
        return value.replace("\\n", "\n")
                .replace("<online>", Integer.toString(online))
                .replace("<max-online>", Integer.toString(Bukkit.getMaxPlayers()))
                .replace("<network-online>", Integer.toString(networkPlayers > 0 ? networkPlayers : online))
                .replace("<tps>", tps);
    }

    int networkPlayers() {
        int online = Bukkit.getOnlinePlayers().size();
        return networkPlayers > 0 ? networkPlayers : online;
    }

    private void requestNetworkCount() {
        if (!plugin.getConfig().getBoolean("chat.network-player-count", true) || Bukkit.getOnlinePlayers().isEmpty()) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("PlayerCount");
            output.writeUTF("ALL");
            Bukkit.getOnlinePlayers().iterator().next().sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
        } catch (IOException ignored) { }
    }

    @Override public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!channel.equals("BungeeCord")) return;
        ByteArrayDataInput input = ByteStreams.newDataInput(message);
        if (!input.readUTF().equals("PlayerCount")) return;
        input.readUTF();
        networkPlayers = Math.max(0, input.readInt());
    }
}
