package dev.heliosares.sync.daemon;

import dev.heliosares.sync.MySender;
import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.*;
import dev.heliosares.sync.utils.CommandParser;
import org.json.JSONObject;

import java.util.List;

public class SyncDaemon implements SyncCore {

    private static SyncDaemon instance;

    public SyncDaemon() {
        instance = this;
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            System.err.println("You must specify arguments");
            return;
        }
        int port = 8001;
        boolean portTerm = false;
        if (args[0].startsWith("-port:")) {
            try {
                port = Integer.parseInt(args[0].substring("-port:".length()));
                portTerm = true;
            } catch (NumberFormatException e) {
                System.err.println("Invalid parameter: " + args[0]);
                System.exit(1);
                return;
            }
        }
        String command = CommandParser.concat(portTerm ? 1 : 0, args);
        System.out.println("Sending: " + command);

        SyncClient sync = new SyncClient(new SyncDaemon());
        try {
            sync.start(port, -1);
            while (!sync.isConnected() || sync.getName() == null) {
                Thread.sleep(10);
            }
            sync.send(new Packet(null, Packets.COMMAND.id, new JSONObject().put("command", command)));
            sync.close();
        } catch (Exception e1) {
            System.err.println("Unable to connect");
            e1.printStackTrace();
            System.exit(2);
            return;
        }
        System.out.println("Command sent.");
        System.exit(0);
    }

    public static SyncCore getInstance() {
        return instance;
    }

    @Override
    public void newThread(Runnable run) {
        new Thread(run).start();
    }

    @Override
    public void runAsync(Runnable run) {
        newThread(run);
    }

    @Override
    public void warning(String msg) {
        System.err.println(msg);
    }

    @Override
    public void print(String msg) {
        System.out.println(msg);
    }

    @Override
    public void print(Throwable t) {
        t.printStackTrace();
    }

    @Override
    public void debug(String msg) {
        print(msg);
    }

    @Override
    public boolean debug() {
        return true;
    }

    @Override
    public MySender getSender(String name) {
        return null;
    }

    @Override
    public void dispatchCommand(MySender sender, String command) {
    }

    @Override
    public void setDebug(boolean debug) {
    }

    @Override
    public void scheduleAsync(Runnable run, long delay, long period) {
    }

    @Override
    public SyncNetCore getSync() {
        return null;
    }

    @Override
    public List<PlayerData> getPlayers() {
        return null;
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.DAEMON;
    }

    @Override
    public boolean isAsync() {
        return true;
    }
}
