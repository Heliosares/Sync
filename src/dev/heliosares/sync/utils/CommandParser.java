package dev.heliosares.sync.utils;

public class CommandParser {
	public static record Result(String remaining, String value) {
	};

	public static Result parse(String key, String cmd) {
		String args[] = cmd.split(" ");
		String value = null;
		String out = "";
		boolean escape = false;
		int i = 0;
		for (; i < args.length; i++) {
			if (i > 0 && (args[i].equalsIgnoreCase("psync") || args[i].equalsIgnoreCase("msync"))) {
				escape = true; // Prevents parsing out parts of the command which are parts of a sub-command
			}
			if (!escape && value == null && args[i].equalsIgnoreCase(key) && i < args.length - 1) {
				value = args[++i];
				continue;
			}
			out += args[i];
			if (i < args.length - 1) {
				out += " ";
			}
		}

		return new Result(out, value);
	}

	public static String concat(String... args) {
		String out = "";
		for (int i = 0; i < args.length; i++) {
			if (out.length() > 0) {
				out += " ";
			}
			out += args[i];
		}
		return out;
	}
}
