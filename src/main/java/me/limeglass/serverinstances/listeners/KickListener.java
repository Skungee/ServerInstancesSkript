package me.limeglass.serverinstances.listeners;

import me.limeglass.serverinstances.ServerInstances;
import me.limeglass.serverinstances.managers.ServerManager;
import me.limeglass.skungee.bungeecord.Skungee;
import net.md_5.bungee.api.AbstractReconnectHandler;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class KickListener implements Listener {

	@EventHandler
	public void onServerKickEvent(ServerKickEvent event) {
		ServerInfo kickedFrom = null;
		if (event.getPlayer().getServer() != null) {
			kickedFrom = event.getPlayer().getServer().getInfo();
		} else if (ServerInstances.getInstance().getProxy().getReconnectHandler() != null) {
			kickedFrom = ServerInstances.getInstance().getProxy().getReconnectHandler().getServer(event.getPlayer());
		} else {
			kickedFrom = AbstractReconnectHandler.getForcedHost(event.getPlayer().getPendingConnection());
			if (kickedFrom == null) {
				kickedFrom = ProxyServer.getInstance().getServerInfo(event.getPlayer().getPendingConnection().getListener().getServerPriority().get(0));
			}
		}
		ServerInfo fallback = ServerInstances.getInstance().getProxy().getServerInfo(ServerInstances.getConfiguration().getString("ServerInstances.fallback-server", "Hub"));
		if (fallback == null) return;
		if (kickedFrom != null && kickedFrom.equals(fallback)) return;
		String reason = BaseComponent.toLegacyText(event.getKickReasonComponent());
		String[] message = ServerInstances.getConfiguration().getString("ServerInstances.move-message").replace("%kickmsg%", reason).replace("%previous%", kickedFrom.getName()).split("\n");
		if (ServerInstances.getConfiguration().getBoolean("ServerInstances.fallback-instances", true)) {
			if (!ServerManager.getInstances().containsKey(kickedFrom.getName())) {
				return;
			}
		}
		event.setCancelled(true);
		event.setCancelServer(fallback);
		if (!(message.length == 1 && message[0].equals(""))) {
			for (String line : message) {
				event.getPlayer().sendMessage(new TextComponent(Skungee.cc(line)));
			}
		}
	}
}