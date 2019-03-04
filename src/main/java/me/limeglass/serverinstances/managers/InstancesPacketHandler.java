package me.limeglass.serverinstances.managers;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.limeglass.serverinstances.ServerInstances;
import me.limeglass.serverinstances.objects.WrappedServer;
import me.limeglass.skungee.objects.ServerInstancesPacket;

public class InstancesPacketHandler {
	
	public static Object handlePacket(ServerInstancesPacket packet, InetAddress address) {
		ServerInstances.debugMessage("Recieved " + packet.getPacketDebug());
		//Map<String, ServerInfo> servers = ProxyServer.getInstance().getServers();
		switch (packet.getType()) {
			case CREATESERVER:
				if (packet.getObject() != null || packet.getSetObject() != null) {
					final String[] servers = (String[]) packet.getObject();
					@SuppressWarnings("unchecked")
					List<Object> information = (List<Object>) packet.getSetObject();
					if (information.size() == 1) {
						for (String name : servers) {
							new WrappedServer(name, (String) information.get(0));
						}
					} else {
						for (String name : servers) {
							new WrappedServer(name, (String) information.get(0), information.get(1) + "M", information.get(2) + "M");
						}
					}
				}
				break;
			case SHUTDOWN:
				if (packet.getObject() != null) {
					for (WrappedServer server : ServerManager.getInstances().values()) {
						for (String input : (String[])packet.getObject()) {
							if (server.getName().equalsIgnoreCase(input)) {
								if (packet.getSetObject() != null) server.shutdown((boolean)packet.getSetObject());
								else server.shutdown();
							}
						}
					}
				}
				break;
			case SERVERINSTANCES:
				Set<String> servers = new HashSet<String>();
				for (String server : ServerManager.getInstances().keySet()) {
					servers.add(server);
				}
				return servers;
		}
		return null;
	}
}