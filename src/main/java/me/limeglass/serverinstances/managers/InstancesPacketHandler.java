package me.limeglass.serverinstances.managers;

import java.net.InetAddress;
import java.util.List;
import me.limeglass.serverinstances.ServerInstances;
import me.limeglass.serverinstances.objects.WrappedServer;
import me.limeglass.skungee.objects.packets.ServerInstancesPacket;

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
						new WrappedServer(instance, name, (String) information.get(0));
					}
				} else {
					for (String name : servers) {
						new WrappedServer(instance, name, (String) information.get(0), information.get(1) + "M", information.get(2) + "M");
					}
				}
				break;
			case SHUTDOWN:
				if (packet.getObject() == null)
					break;
				for (WrappedServer server : serverManager.getInstances().values()) {
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
				return serverManager.getInstances().keySet();
		}
		return null;
	}

}
