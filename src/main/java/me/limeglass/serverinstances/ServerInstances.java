package me.limeglass.serverinstances;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import me.limeglass.serverinstances.listeners.KickListener;
import me.limeglass.serverinstances.managers.ServerManager;
import me.limeglass.skungee.bungeecord.BungecordMetrics;
import me.limeglass.skungee.bungeecord.Skungee;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class ServerInstances extends Plugin {

	private static Configuration configuration;
	private static ServerInstances instance;
	private int CONFIG_VERSION = 1;
	
	public void onEnable(){
		instance = this;
		loadConfiguration();
		ServerManager.setup();
		getProxy().getPluginManager().registerListener(this, new KickListener());
		Skungee.getMetrics().addCustomChart(new BungecordMetrics.SimplePie("serverinstances") {
			@Override
			public String getValue() {
				return getInstance().getDescription().getVersion();
			}
		});
		if (!configuration.getBoolean("DisableRegisteredInfo", false)) consoleMessage("Successfully hooked into Skungee!");
	}
	
	public void onDisable() {
		ServerManager.shutdownAll();
	}
	
	private void loadConfiguration() {
		File file = new File(Skungee.getInstance().getDataFolder(), "serverinstances.yml");
		try (InputStream in = getResourceAsStream("serverinstances.yml")) {
			if (!file.exists()) Files.copy(in, file.toPath());
			configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
			if (configuration.getInt("configuration-version", 0) < CONFIG_VERSION) {
				consoleMessage("&eThere is a new ServerInstances configuration. Generating new serverinstances.yml...");
				Files.delete(file.toPath());
				loadConfiguration();
				return;
			}
			Skungee.addConfiguration("serverinstances", configuration);
		} catch (IOException e) {
			Skungee.exception(e, "Could not create and save serverinstances due to new configuration.");
		}
	}
	
	public static ServerInstances getInstance() {
		return instance;
	}
	
	public static Configuration getConfiguration() {
		return configuration;
	}
	
	public static void debugMessage(String... strings) {
		if (configuration.getBoolean("ServerInstances.debug", false)) for (String string : strings) Skungee.consoleMessage("&a[ServerInstances] &2" + string);
	}

	public static void consoleMessage(String... strings) {
		for (String string : strings) Skungee.consoleMessage("&a[ServerInstances] " + string);
	}
}

/*

TODO:
- Saved Player instances.
- Global Max Ram
- Syntax
- Add option to use a wrapper jar instead.
- 

*/