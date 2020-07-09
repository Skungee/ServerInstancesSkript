package me.limeglass.serverinstances.managers;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.google.common.collect.Sets;

import me.limeglass.serverinstances.ServerInstances;
import me.limeglass.serverinstances.listeners.SyntaxListener;
import me.limeglass.serverinstances.objects.WrappedServer;
import me.limeglass.serverinstances.utils.ServerHelper;
import me.limeglass.skungee.bungeecord.Skungee;
import me.limeglass.skungee.bungeecord.sockets.ServerInstancesSockets;
import me.limeglass.skungee.spigot.utils.Utils;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.config.Configuration;

public class ServerManager {

	private final File INSTANCES_FOLDER, SAVED_FOLDER, TEMPLATE_FOLDER, RUNNING_SERVERS_FOLDER, RUN_SCRIPTS;
	private final Set<WrappedServer> instances = new HashSet<>();
	private final Set<Process> processes = new HashSet<>();
	private final Configuration configuration;
	private final ServerHelper serverHelper;
	private final ServerInstances instance;
	private ServerSocket socket;

	public ServerManager(ServerInstances instance) {
		this.instance = instance;
		this.serverHelper = instance.getServerHelper();
		this.configuration = instance.getConfiguration();
		INSTANCES_FOLDER = new File(instance.getSkungeeInstance().getDataFolder(), "ServerInstances");
		if (!INSTANCES_FOLDER.exists())
			INSTANCES_FOLDER.mkdir();
		TEMPLATE_FOLDER = new File(INSTANCES_FOLDER, "templates");
		if (!TEMPLATE_FOLDER.exists())
			TEMPLATE_FOLDER.mkdir();
		RUNNING_SERVERS_FOLDER = new File(INSTANCES_FOLDER, "running-servers");
		if (!RUNNING_SERVERS_FOLDER.exists())
			RUNNING_SERVERS_FOLDER.mkdir();
		RUN_SCRIPTS = new File(INSTANCES_FOLDER, "run-scripts");
		if (!RUN_SCRIPTS.exists())
			RUN_SCRIPTS.mkdir();
		SAVED_FOLDER = new File(INSTANCES_FOLDER, "saved-servers");
		if (!SAVED_FOLDER.exists())
			SAVED_FOLDER.mkdir();
		try {
			if (configuration.getBoolean("Listener.automatic")) {
				socket = new ServerSocket(Utils.findPort(1000, 65534), 69);
			} else {
				socket = new ServerSocket(configuration.getInt("Listener.port", 7331), 69);
			}
			instance.debugMessage("Listener established on port " + socket.getLocalPort());
			ServerInstancesSockets.setInstancesPort(socket.getLocalPort());
			instance.getProxy().getScheduler().runAsync(instance, new Runnable() {
				@Override
				public void run() {
					while (!socket.isClosed()) {
						try {
							new Thread(new SyntaxListener(instance, socket.accept())).start();
						} catch (IOException e) {
							Skungee.exception(e, "ServerInstances listener couldn't be accepted.");
						}
					}
				}
			});
		} catch (IOException e) {
			Skungee.exception(e, "ServerInstances listner couldn't be created on port: " + socket.getLocalPort());
		}
		//If the bungeecord gets forced closed.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				if (instances.isEmpty())
					return;
				for (WrappedServer server : instances) {
					server.shutdown();
					try {
						Thread.sleep(2000L);
					}
					catch (InterruptedException e) {}
				}
			}
		});
	}

	public boolean containsName(String name) {
		return instances.stream().anyMatch(server -> server.getName().equalsIgnoreCase(name));
	}

	public void addInstance(WrappedServer server) {
		String name = server.getName();
		if (!containsName(name))
			instances.add(server);
		try {
			InetSocketAddress address = new InetSocketAddress(configuration.getString("address-bind", InetAddress.getLocalHost().getHostAddress()), server.getPort());
			ServerInfo serverInfo = instance.getProxy().constructServerInfo(name, address, server.getMotd(), false);
			serverHelper.addServer(serverInfo);
			instance.consoleMessage("Started server " + name + " on port " + server.getPort());
		} catch (UnknownHostException exception) {
			Skungee.exception(exception, "Could not find the system's local host. Due to this the server will not be added to the Bungeecord, but still run.");
		}
	}

	public void shutdownAll() {
		for (Iterator<WrappedServer> iterator = Sets.newConcurrentHashSet(instances).iterator(); iterator.hasNext();) {
			WrappedServer server = iterator.next();
			server.shutdown(server.isAutoSave());
		}
		instances.clear();
	}

	public void removeInstance(WrappedServer server) {
		instances.remove(server);
		serverHelper.removeServer(server.getName());
	}

	public Set<WrappedServer> getInstances() {
		return Collections.unmodifiableSet(instances);
	}

	public File getServerInstancesFolder() {
		return INSTANCES_FOLDER;
	}

	public File getRunningServerFolder() {
		return RUNNING_SERVERS_FOLDER;
	}

	public Set<Process> getProcesses() {
		return processes;
	}

	public File getRunScriptsFolder() {
		return RUN_SCRIPTS;
	}

	public File getTemplateFolder() {
		return TEMPLATE_FOLDER;
	}

	public File getSavedFolder() {
		return SAVED_FOLDER;
	}

}
