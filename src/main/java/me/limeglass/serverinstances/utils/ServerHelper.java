package me.limeglass.serverinstances.utils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import me.limeglass.serverinstances.ServerInstances;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.YamlConfiguration;

public class ServerHelper {

	private final ServerInstances instance;
	private Configuration configuration;
	private final ProxyServer proxy;
	private File file;

	public ServerHelper(ServerInstances instance) {
		this.proxy = instance.getProxy();
		this.instance = instance;
		setupConfig();
		if (configuration != null)
			return;
		proxy.getScheduler().schedule(instance, () -> setupConfig(), 5L, TimeUnit.SECONDS);
	}

	private void setupConfig() {
		try {
			file = new File(ProxyServer.getInstance().getPluginsFolder().getParentFile(), "config.yml");
			configuration = YamlConfiguration.getProvider(YamlConfiguration.class).load(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void saveConfig() {
		if (configuration == null)
			return;
		try {
			YamlConfiguration.getProvider(YamlConfiguration.class).save(configuration, file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Optional<ServerInfo> getServerInfo(String name) {
		return Optional.ofNullable(getServers().get(name));
	}

	public void addServer(ServerInfo serverInfo) {
		String name = serverInfo.getName();
		if (!getServerInfo(name).isPresent())
			return;
		getServers().put(name, serverInfo);
		if (configuration == null)
			return;
		configuration.set("servers." + name + ".motd", serverInfo.getMotd().replace(ChatColor.COLOR_CHAR, '&'));
		configuration.set("servers." + name + ".address", "localhost:" + serverInfo.getAddress().getPort());
		configuration.set("servers." + name + ".restricted", false);
		saveConfig();
	}

	public void removeServer(String name) {
		Optional<ServerInfo> optional = getServerInfo(name);
		if (!optional.isPresent())
			return;
		ServerInfo info = optional.get();
		for (ProxiedPlayer player : info.getPlayers()) {
			player.connect(getServers().get(instance.getConfiguration().getString("ServerInstances.fallback-server", "Hub")));
		}
		getServers().remove(name);
		if (configuration == null)
			return;
		configuration.set("servers." + name, null);
		saveConfig();
	}

	public Map<String, ServerInfo> getServers() {
		return ProxyServer.getInstance().getServers();
	}

}
