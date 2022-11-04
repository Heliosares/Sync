package dev.heliosares.sync.spigot;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.json.JSONObject;

import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.Packet;
import dev.heliosares.sync.net.Packets;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.FormulaParser;

public class SpigotCommandListener implements CommandExecutor, TabCompleter {
	private final SyncCore plugin;

	public SpigotCommandListener(SyncCore plugin) {
		this.plugin = plugin;
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

			if (args.length == 1) {
				if (args[0].equalsIgnoreCase("-debug")) {
					plugin.setDebug(!plugin.debug());
					if (plugin.debug())
						sender.sendMessage("�aDebug enabled");
					else
						sender.sendMessage("�cDebug disabled");
					return true;
				} else if (args[0].equalsIgnoreCase("-list")) {
					sender.sendMessage(plugin.getSync().getUserManager().toFormattedString());
					return true;
				}
			}

			try {
				plugin.getSync().send(new Packet(null, Packets.COMMAND.id,
						new JSONObject().put("command", CommandParser.concat(0, args))));
			} catch (Exception e) {
				sender.sendMessage("�cAn error occured");
				plugin.print(e);
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
			parser.setVariable("$server", () -> plugin.getSync().getName());

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
				plugin.getSync().send(new Packet(null, Packets.COMMAND.id,
						new JSONObject().put("command", CommandParser.concat(0, args))));
			} catch (Exception e) {
				sender.sendMessage("�cAn error occured");
				plugin.print(e);
			}
			return true;
		}
		return false;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		List<String> out = new ArrayList<>();

		if (args.length == 0) {
			return out;
		}
		if (sender.hasPermission("sync.psync")) {
			if (args.length == 1) {
				out.add("-list");
			}
			if (args.length > 1 && args[args.length - 2].equalsIgnoreCase("-s")) {
				plugin.getSync().getServers().forEach(c -> out.add(c));
			} else {
				out.add("-s");
				out.add("-p");
			}
		}
		return CommandParser.tab(out, args[args.length - 1]);
	}
}