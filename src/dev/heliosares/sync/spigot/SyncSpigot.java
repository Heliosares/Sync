package dev.heliosares.sync.spigot;

import java.io.IOException;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONObject;

import dev.heliosares.sync.MySender;
import dev.heliosares.sync.SpigotSender;
import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.EncryptionManager;
import dev.heliosares.sync.net.NetListener;
import dev.heliosares.sync.net.Packet;
import dev.heliosares.sync.net.Packets;
import dev.heliosares.sync.net.SyncClient;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.CommandParser.Result;
import dev.heliosares.sync.utils.FormulaParser;

public class SyncSpigot extends JavaPlugin implements CommandExecutor, SyncCore {
	private SyncClient sync;
	private boolean debug;
	private static SyncSpigot instance;

	public static SyncSpigot getInstance() {
		return instance;
	}

	@Override
	public void onEnable() {
		instance = this;
		this.getConfig().options().copyDefaults(true);
		this.saveDefaultConfig();

		try {
			EncryptionManager.setKey(getConfig().getString("publickey"), false);
		} catch (Throwable t) {
			warning("Invalid key. Disabling.");
			if (debug) {
				print(t);
			}
			this.setEnabled(false);
			return;
		}

		this.getCommand("psync").setExecutor(this);
		this.getCommand("if").setExecutor(this);

		sync = new SyncClient(this);
		try {
			sync.start("127.0.0.1", getConfig().getInt("port", 8001), this.getServer().getPort());
		} catch (IOException e1) {
			warning("Error while enabling.");
			print(e1);
			this.setEnabled(false);
			return;
		}

		sync.getEventHandler().registerListener(new NetListener(Packets.COMMAND.id, null) {
			@Override
			public void execute(String server, Packet packet) {
				try {
					String message = packet.getPayload().getString("command");

					if (message.equals("-kill")) {
						print("Killing");
						System.exit(0);
						return;
					}
					if (message.equals("-halt")) {
						print("Halting");
						Runtime.getRuntime().halt(0);
					}

					print("Executing: " + message);

					Result playerR = CommandParser.parse("-p", message);
					CommandSender sender = null;
					if (playerR.value() == null) {
						sender = getServer().getConsoleSender();
					} else {
						sender = getServer().getPlayer(playerR.value());
						message = playerR.remaining();
						if (sender == null) {
							print("Player not found: " + playerR.value());
							return;
						}
					}
					dispatchCommand(sender, message);
				} catch (Exception e) {
					getLogger().warning("Error while parsing: ");
					print(e);
				}
			}
		});

		new BukkitRunnable() {

			@Override
			public void run() {
				if (!sync.isConnected()) {
					return;
				}
				try {
					sync.keepalive();
				} catch (Exception e) {
					warning("Error while sending keepalive:");
					print(e);
				}
			}
		}.runTaskTimerAsynchronously(this, 20, 20);
	}

	private void dispatchCommand(CommandSender sender, String command) {
		new BukkitRunnable() {
			@Override
			public void run() {
				getServer().dispatchCommand(sender, command);
			}
		}.runTask(this);
	}

	@Override
	public void onDisable() {
		if (sync != null) {
			sync.close();
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		if (cmd.getLabel().equalsIgnoreCase("psync")) {
			if (!sender.hasPermission("sync.psync")) {
				sender.sendMessage("�cNo permission");
				return true;
			}
			if (args.length == 0) {
				sender.sendMessage("�cInvalid syntax");
				return true;
			}

			if (args.length == 1 && args[0].equalsIgnoreCase("-debug")) {
				debug = !debug;
				if (debug)
					sender.sendMessage("�aDebug enabled");
				else
					sender.sendMessage("�cDebug disabled");
				return true;
			}

			try {
				sync.send(new Packet(null, Packets.COMMAND.id,
						new JSONObject().put("command", CommandParser.concat(0, args))));
			} catch (Exception e) {
				sender.sendMessage("�cAn error occured");
				print(e);
				return true;
			}
			sender.sendMessage("�aCommand sent.");
			return true;
		} else if (cmd.getLabel().equalsIgnoreCase("if")) {
			if (!sender.hasPermission("sync.if")) {
				sender.sendMessage("�cNo permission");
				return true;
			}
			String condition = "";
			String commandIf = "";
			String commandElse = "";
			boolean then = false;
			boolean el = false;
			for (int i = 0; i < args.length; i++) {
				String part = args[i];
				if (part.equalsIgnoreCase("then")) {
					if (condition.length() == 0) {
						sender.sendMessage("�cNo condition provided");
						return true;
					}
					then = true;
					continue;
				}
				part += " ";
				if (then) {
					if (part.equalsIgnoreCase("else ")) {
						el = true;
						continue;
					}
					if (el) {
						commandElse += part;
					} else {
						commandIf += part;
					}
				} else {
					condition += part;
				}
			}
			if (!then) {
				sender.sendMessage("�cNo 'then' provided.");
				return true;
			}
			if (commandIf.length() == 0) {
				sender.sendMessage("�cNo command provided.");
				return true;
			}
			FormulaParser parser = new FormulaParser(condition);

			parser.setVariable("$online-players", () -> Bukkit.getOnlinePlayers().size());
			parser.setVariable("$sender", () -> sender.getName());
			parser.setVariable("$server", () -> sync.getName());

			String command;
			boolean state;
			try {
				state = parser.solve() == 1;
			} catch (RuntimeException e) {
				sender.sendMessage("�c" + e.getMessage());
				return true;
			}
			if (state) {
				command = commandIf;
			} else {
				command = commandElse;
			}
			if (command.length() > 0) {
				try {
					Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parser.replaceVariables(command.trim()));
				} catch (RuntimeException e) {
					sender.sendMessage("�c" + e.getMessage());
				}
			}
			try {
				sync.send(new Packet(null, Packets.COMMAND.id,
						new JSONObject().put("command", CommandParser.concat(0, args))));
			} catch (Exception e) {
				sender.sendMessage("�cAn error occured");
				print(e);
			}
			return true;
		}
		return false;
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
	public void runAsync(Runnable run) {
		new Thread(run).start();
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
		Player player = getServer().getPlayer(name);
		return player == null ? null : new SpigotSender(player);
	}

	@Override
	public void dispatchCommand(MySender sender, String command) {
		getServer().getScheduler().runTask(this, () -> {
			sender.execute(command);
		});
	}

	@Override
	public SyncClient getSync() {
		return sync;
	}
}
