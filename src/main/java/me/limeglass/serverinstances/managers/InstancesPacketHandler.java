package me.limeglass.serverinstances.managers;

import java.net.InetAddress;
import java.util.List;
import java.util.stream.Collectors;

import me.limeglass.serverinstances.ServerInstances;
import me.limeglass.serverinstances.objects.WrappedServer;
import me.limeglass.skungee.objects.SkungeePlayer;
import me.limeglass.skungee.objects.packets.ServerInstancesPacket;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerConnectRequest;
import net.md_5.bungee.api.event.ServerConnectEvent.Reason;

public class InstancesPacketHandler {

	private final ServerManager serverManager;
	private final ServerInstances instance;

	public InstancesPacketHandler(ServerInstances instance) {
		this.serverManager = instance.getServerManager();
		this.instance = instance;
	}

	public Object handlePacket(ServerInstancesPacket packet, InetAddress address) {
		instance.debugMessage("Recieved " + packet.getPacketDebug());
		switch (packet.getType()) {
			case CREATESERVER:
				if (packet.getObject() == null || packet.getSetObject() == null)
					break;
				String[] servers = (String[]) packet.getObject();
				@SuppressWarnings("unchecked")
				List<Object> information = (List<Object>) packet.getSetObject();
				if (information.size() == 1) {
					for (String name : servers) {
						new WrappedServer(instance, name, (String) information.get(0), true);
					}
				} else {
					for (String name : servers) {
						new WrappedServer(instance, name, (String) information.get(0), information.get(1) + "M", information.get(2) + "M", true);
					}
				}
				break;
			case SHUTDOWN:
				if (packet.getObject() == null)
					break;
				for (WrappedServer server : serverManager.getInstances()) {
					for (String input : (String[])packet.getObject()) {
						if (server.getName().equalsIgnoreCase(input)) {
							if (packet.getSetObject() != null) {
								server.shutdown((boolean)packet.getSetObject());
							} else {
								server.shutdown();
							}
						}
					}
				}
				break;
			case SERVERINSTANCES:
				return serverManager.getInstances().stream().map(server -> server.getName()).collect(Collectors.toSet());
			case CONNECT:
				if (packet.getObject() == null)
					break;
				if (packet.getSetObject() == null)
					break;
				SkungeePlayer[] players = (SkungeePlayer[]) packet.getSetObject();
				String name = (String) packet.getObject();
				serverManager.getInstances().stream()
						.filter(server -> server.getName().equalsIgnoreCase(name))
						.findFirst()
						.ifPresent(server -> {
							ServerConnectRequest connection = ServerConnectRequest.builder()
									.target(server.getServerInfo())
									.reason(Reason.PLUGIN)
									.retry(true)
									.build();
							for (SkungeePlayer player : players)
								ProxyServer.getInstance().getPlayer(player.getUUID()).connect(connection);
						});
				break;
		}
		return null;
	}

}
