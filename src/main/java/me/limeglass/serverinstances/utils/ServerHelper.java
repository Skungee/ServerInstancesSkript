package me.limeglass.serverinstances.utils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import me.limeglass.serverinstances.ServerInstances;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.YamlConfiguration;

public class ServerHelper {
	
	private static Configuration config;
	private static boolean locked;
	private static File file;
	
	static {
		setupConfig();
		if (locked) {
			ProxyServer.getInstance().getScheduler().schedule(ServerInstances.getInstance(), new Runnable() {
				@Override
				public void run() {
					setupConfig();
				}
			}, 5L, TimeUnit.SECONDS);
		}
	}
	
	private static void setupConfig() {
		try {
			file = new File(ProxyServer.getInstance().getPluginsFolder().getParentFile(), "config.yml");
			config = YamlConfiguration.getProvider(YamlConfiguration.class).load(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
		locked = config == null;
	}
	
	private static void saveConfig() {
		if (locked) return;
		try {
			YamlConfiguration.getProvider(YamlConfiguration.class).save(config, file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static boolean serverExists(String name) {
		return getServerInfo(name) != null;
	}

	public static ServerInfo getServerInfo(String name) {
		return getServers().get(name);
	}

	public static void addServer(ServerInfo serverInfo) {
		if (serverExists(serverInfo.getName())) return;
		getServers().put(serverInfo.getName(), serverInfo);
		if (locked) return;
		config.set("servers." + serverInfo.getName() + ".motd", serverInfo.getMotd().replace(ChatColor.COLOR_CHAR, '&'));
		config.set("servers." + serverInfo.getName() + ".address", "localhost:" + serverInfo.getAddress().getPort());
		config.set("servers." + serverInfo.getName() + ".restricted", false);
		saveConfig();
	}

	public static void removeServer(String name) {
		if (!serverExists(name)) return;
		ServerInfo info = getServerInfo(name);
		for (ProxiedPlayer player : info.getPlayers()) {
			player.connect(getServers().get(ServerInstances.getConfiguration().getString("ServerInstances.fallback-server", "Hub")));
		}
		getServers().remove(name);
		if (locked) return;
		config.set("servers." + name, null);
		saveConfig();
	}

	public static Map<String, ServerInfo> getServers() {
		return ProxyServer.getInstance().getServers();
	}
}
