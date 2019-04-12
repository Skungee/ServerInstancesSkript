package me.limeglass.serverinstances.listeners;

import me.limeglass.serverinstances.ServerInstances;
import me.limeglass.serverinstances.managers.ServerManager;
import me.limeglass.skungee.bungeecord.Skungee;
import net.md_5.bungee.api.AbstractReconnectHandler;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.event.EventHandler;

public class KickListener implements Listener {

	private final ServerManager serverManager;
	private final Configuration configuration;
	private final ProxyServer proxy;

	public KickListener(ServerInstances instance) {
		this.configuration = instance.getConfiguration();
		this.serverManager = instance.getServerManager();
		this.proxy = instance.getProxy();
	}

	@EventHandler
	public void onServerKickEvent(ServerKickEvent event) {
		ProxiedPlayer player = event.getPlayer();
		ServerInfo kickedFrom;
		if (player.getServer() != null) {
			kickedFrom = player.getServer().getInfo();
		} else if (proxy.getReconnectHandler() != null) {
			kickedFrom = proxy.getReconnectHandler().getServer(player);
		} else {
			kickedFrom = AbstractReconnectHandler.getForcedHost(player.getPendingConnection());
			if (kickedFrom == null)
				kickedFrom = proxy.getServerInfo(player.getPendingConnection().getListener().getServerPriority().get(0));
		}
		ServerInfo fallback = proxy.getServerInfo(configuration.getString("ServerInstances.fallback-server", "Hub"));
		if (fallback == null || kickedFrom == null)
			return;
		if (kickedFrom.equals(fallback))
			return;
		String reason = BaseComponent.toLegacyText(event.getKickReasonComponent());
		String[] messages = configuration.getString("ServerInstances.move-message")
				.replace("%previous%", kickedFrom.getName())
				.replace("%kickmsg%", reason)
				.split("\n");
		if (configuration.getBoolean("ServerInstances.fallback-instances", true)) {
			if (!serverManager.getInstances().containsKey(kickedFrom.getName()))
				return;
		}
		event.setCancelled(true);
		event.setCancelServer(fallback);
		if (messages.length > 0 && !messages[0].equals("")) {
			for (String line : messages)
				player.sendMessage(new TextComponent(Skungee.cc(line)));
		}
	}

}
