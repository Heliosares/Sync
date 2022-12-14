package dev.heliosares.sync.bungee;

import dev.heliosares.sync.BungeeSender;
import dev.heliosares.sync.MySender;
import dev.heliosares.sync.SyncCoreProxy;
import dev.heliosares.sync.net.*;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.CommandParser.Result;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

public class SyncBungee extends Plugin implements SyncCoreProxy {
    private static SyncBungee instance;
    protected Configuration config;
    SyncServer sync;
    boolean debug;

    public static SyncBungee getInstance() {
        return instance;
    }

    public static void tell(CommandSender sender, String msg) {
        sender.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', msg)));
    }

    @Override
    public void onEnable() {
        instance = this;
        loadConfig();

        print("Enabling");
        getProxy().getPluginManager().registerCommand(this, new MSyncCommand("msync", this));
        getProxy().getPluginManager().registerCommand(this, new MTellCommand("mtell", this));

        sync = new SyncServer(this);
        sync.start(config.getInt("port", 8001));

        sync.getEventHandler().registerListener(new NetListener(Packets.MESSAGE.id, null) {
            @Override
            public void execute(String server, Packet packet) {
                @Nullable String msg = packet.getPayload().optString("msg", null);
                @Nullable String json = packet.getPayload().optString("json", null);
                @Nullable String node = packet.getPayload().optString("node", null);
                @Nullable String to = packet.getPayload().optString("to", null);
                boolean others_only = packet.getPayload().optBoolean("others_only");
                if (to != null) {
                    ProxiedPlayer toPlayer = getProxy().getPlayer(packet.getPayload().getString("to"));
                    if (toPlayer == null) {
                        try {
                            toPlayer = getProxy().getPlayer(UUID.fromString(to));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    if (toPlayer != null && (node == null || toPlayer.hasPermission(node))) tell(toPlayer, msg);
                } else {
                    Consumer<ProxiedPlayer> send;
                    if (msg != null) send = p -> tell(p, msg);
                    else if (json != null) {
                        BaseComponent[] base = ComponentSerializer.parse(json);
                        send = p -> p.sendMessage(base);
                    } else return;
                    ServerInfo ignore = others_only ? getProxy().getServerInfo(server) : null;
                    getProxy().getPlayers().stream()
                            .filter(p -> !p.getServer().getInfo().equals(ignore))
                            .filter(p -> node == null || p.hasPermission(node))
                            .forEach(send);
                }
            }
        });
        sync.getEventHandler().registerListener(new NetListener(Packets.COMMAND.id, null) {
            @Override
            public void execute(String server, Packet packet) {
                try {
                    String message = packet.getPayload().getString("command");

                    print("Executing: " + message);

                    Result serverR = CommandParser.parse("-s", message);
                    if (serverR.value() != null) {
                        if (!sync.send(serverR.value(), new Packet(null, packet.getPacketId(),
                                new JSONObject().put("command", serverR.remaining())))) {
                            warning("No servers found matching this name: " + serverR.value());
                        }
                        return;
                    }

                    Result playerR = CommandParser.parse("-p", message);

                    CommandSender sender = null;
                    if (playerR.value() == null) {
                        sender = getProxy().getConsole();
                    } else {
                        sender = getProxy().getPlayer(playerR.value());
                        if (sender == null) {
                            print("Player not found");
                            return;
                        }
                    }
                    debug("out: " + playerR.remaining());
                    getProxy().getPluginManager().dispatchCommand(sender, playerR.remaining());
                } catch (Exception e) {
                    getLogger().warning("Error while parsing: ");
                    print(e);
                }
            }
        });

        getProxy().getScheduler().schedule(this, new Runnable() {
            @Override
            public void run() {
                sync.keepalive();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public void onDisable() {
        print("Closing");
        if (sync != null) {
            sync.close();
        }
    }

    public void loadConfig() {
        if (!getDataFolder().exists()) {
            boolean ignored = getDataFolder().mkdir();
        }

        File file = new File(getDataFolder(), "config.yml");

        if (!file.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                print(e);
            }
        }
        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class)
                    .load(new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            print(e);
        }
    }

    @Override
    public void print(String msg) {
        getLogger().info(msg);
    }

    @Override
    public void print(Throwable t) {
        getLogger().log(Level.WARNING, t.getMessage(), t);
    }

    @Override
    public void debug(String msg) {
        if (debug) {
            print(msg);
        }
    }

    @Override
    public void newThread(Runnable run) {
        new Thread(run).start();
    }

    @Override
    public void runAsync(Runnable run) {
        getProxy().getScheduler().runAsync(this, run);
    }

    @Override
    public void scheduleAsync(Runnable run, long delay, long period) {
        getProxy().getScheduler().schedule(this, run, delay, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public String getServerNameByPort(int port) {
        for (Entry<String, ServerInfo> info : getProxy().getServers().entrySet()) {
            if (info.getValue().getSocketAddress() instanceof InetSocketAddress) {
                if (port == ((InetSocketAddress) info.getValue().getSocketAddress()).getPort()) {
                    return info.getKey();
                }
            }
        }
        return null;
    }

    @Override
    public void warning(String msg) {
        getLogger().warning(msg);
    }

    @Override
    public boolean debug() {
        return debug;
    }

    @Override
    public MySender getSender(String name) {
        ProxiedPlayer player = getProxy().getPlayer(name);
        return player == null ? null : new BungeeSender(this, player);
    }

    @Override
    public void dispatchCommand(MySender sender, String command) {
        getProxy().getPluginManager().dispatchCommand((CommandSender) sender.getSender(), command);
    }

    @Override
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public SyncServer getSync() {
        return sync;
    }

    @Override
    public List<PlayerData> getPlayers() {
        return null;
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.BUNGEE;
    }
}
