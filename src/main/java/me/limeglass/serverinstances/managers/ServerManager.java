package me.limeglass.serverinstances.managers;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import me.limeglass.serverinstances.objects.WrappedServer;
import me.limeglass.serverinstances.utils.ServerHelper;
import me.limeglass.skungee.bungeecord.Skungee;
import me.limeglass.skungee.bungeecord.sockets.ServerInstancesSockets;
import me.limeglass.skungee.spigot.utils.Utils;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import me.limeglass.serverinstances.ServerInstances;
import me.limeglass.serverinstances.listeners.SyntaxListener;

public class ServerManager {

	private static File SERVER_INSTANCES_FOLDER, SAVED_FOLDER, TEMPLATE_FOLDER, RUNNING_SERVERS_FOLDER, RUN_SCRIPTS;
	private final static Map<String, WrappedServer> instances = new HashMap<String, WrappedServer>();
	private final static Set<Process> processes = new HashSet<Process>();
	private static ServerSocket socket;
	
	public static void setup() {
		SERVER_INSTANCES_FOLDER = new File(Skungee.getInstance().getDataFolder(), File.separator + "ServerInstances");
		if (!SERVER_INSTANCES_FOLDER.exists()) SERVER_INSTANCES_FOLDER.mkdir();
		TEMPLATE_FOLDER = new File(SERVER_INSTANCES_FOLDER, "templates");
		if (!TEMPLATE_FOLDER.exists()) TEMPLATE_FOLDER.mkdir();
		RUNNING_SERVERS_FOLDER = new File(SERVER_INSTANCES_FOLDER, "running-servers");
		if (!RUNNING_SERVERS_FOLDER.exists()) RUNNING_SERVERS_FOLDER.mkdir();
		RUN_SCRIPTS = new File(SERVER_INSTANCES_FOLDER, "run-scripts");
		if (!RUN_SCRIPTS.exists()) RUN_SCRIPTS.mkdir();
		SAVED_FOLDER = new File(SERVER_INSTANCES_FOLDER, "saved-servers");
		if (!SAVED_FOLDER.exists()) SAVED_FOLDER.mkdir();
		try {
			if (ServerInstances.getConfiguration().getBoolean("Listener.automatic")) {
				socket = new ServerSocket(Utils.findPort(1000, 65534), 69);
			} else {
				socket = new ServerSocket(ServerInstances.getConfiguration().getInt("Listener.port", 7331), 69);
			}
			ServerInstances.debugMessage("Listener established on port " + socket.getLocalPort());
			ServerInstancesSockets.setInstancesPort(socket.getLocalPort());
			ProxyServer.getInstance().getScheduler().runAsync(ServerInstances.getInstance(), new Runnable() {
				@Override
				public void run() {
					while (!socket.isClosed()) {
						try {
							new Thread(new SyntaxListener(socket.accept())).start();
						} catch (IOException e) {
							Skungee.exception(e, "ServerInstances listener couldn't be accepted.");
						}
					}
				}
			});
		} catch (IOException e) {
			Skungee.exception(e, "ServerInstances listner couldn't be created on port: " + socket.getLocalPort());
		}
		//Incase the bungee gets forced closed.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				if (!ServerManager.getInstances().isEmpty()) {
					for (WrappedServer server : ServerManager.getInstances().values()) {
						server.shutdown();
						try {
							Thread.sleep(3000L);
						}
						catch (InterruptedException e) {}
					}
				}
			}
		});
	}
	
	public static void addInstance(WrappedServer instance) {
		if (!instances.containsKey(instance.getName())) instances.put(instance.getName(), instance);
		try {
			ServerInfo serverInfo = ProxyServer.getInstance().constructServerInfo(instance.getName(), new InetSocketAddress(ServerInstances.getConfiguration().getString("address-bind", InetAddress.getLocalHost().getHostAddress()), instance.getPort()), instance.getMotd(), false);
			ServerHelper.addServer(serverInfo);
		} catch (UnknownHostException exception) {
			Skungee.exception(exception, "Could not find the system's local host. Due to this the server will not be added to the Bungeecord, but still run.");
		}
	}
	
	public static void shutdownAll() {
		Iterator<WrappedServer> iterator = instances.values().iterator();
		while (iterator.hasNext()) {
			iterator.next().shutdown();
		}
	}
	
	public static void removeInstance(WrappedServer instance) {
		instances.remove(instance.getName());
		ServerHelper.removeServer(instance.getName());
	}
	
	public static Map<String, WrappedServer> getInstances() {
		return instances;
	}
	
	public static Set<Process> getProcesses() {
		return processes;
	}
	
	public static File getServerInstancesFolder() {
		return SERVER_INSTANCES_FOLDER;
	}
	
	public static File getSavedFolder() {
		return SAVED_FOLDER;
	}
	
	public static File getRunningServerFolder() {
		return RUNNING_SERVERS_FOLDER;
	}

	public static File getTemplateFolder() {
		return TEMPLATE_FOLDER;
	}
	
	public static File getRunScriptsFolder() {
		return RUN_SCRIPTS;
	}
}
