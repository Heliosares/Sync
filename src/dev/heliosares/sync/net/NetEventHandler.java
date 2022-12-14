package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;

import java.util.ArrayList;
import java.util.Objects;

public final class NetEventHandler {

    private final ArrayList<NetListener> listeners = new ArrayList<>();
    private final SyncCore plugin;

    public NetEventHandler(SyncCore plugin) {
        this.plugin = plugin;
    }

    public void registerListener(NetListener listen) {
        synchronized (listeners) {
            listeners.add(listen);
        }
    }

    public void unregisterListener(NetListener listen) {
        synchronized (listeners) {
            listeners.remove(listen);
        }
    }

    public void unregisterChannel(String channel) {
        synchronized (listeners) {
            listeners.removeIf(netListener -> Objects.equals(channel, netListener.getChannel()));
        }
    }

    void execute(String server, Packet packet) {
        packet = packet.unmodifiable();
        synchronized (listeners) {
            for (NetListener listen : listeners) {
                if (packet.getChannel() == null) {
                    if (listen.getChannel() != null) {
                        continue;
                    }
                } else {
                    if (!packet.getChannel().equalsIgnoreCase(listen.getChannel())) {
                        continue;
                    }
                }
                if (packet.getPacketId() == listen.getPacketId()) {
                    try {
                        listen.execute(server, packet);
                    } catch (Throwable t) {
                        plugin.warning("Failed to pass " + packet + " to " + listen.getChannel());
                        plugin.print(t);
                    }
                }
            }
        }
    }
}