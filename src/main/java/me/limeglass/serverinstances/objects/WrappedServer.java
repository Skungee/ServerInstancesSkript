package me.limeglass.serverinstances.objects;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.config.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import me.limeglass.serverinstances.ServerInstances;
import me.limeglass.serverinstances.managers.ServerManager;
import me.limeglass.skungee.spigot.utils.Utils;

public class WrappedServer {

	private final long started = System.currentTimeMillis();
	private InputStream inputStream, errors;
	private ProcessBuilder processBuilder;
	private boolean isRunning, useSaved;
	private OutputStream outputStream;
	private final String template;
	private String name, motd;
	private Process process;
	private File folder;
	private int port;

	private final List<String> commands = new ArrayList<>();
	private final Configuration configuration;
	private final ServerManager serverManager;
	private final ServerInstances instance;
	private String Xmx, Xms;

	/**
	 * Create and start a wrapped server
	 * 
	 * @param instance The Plugin running the server.
	 * @param existing if an existing server is to be cloned.
	 * @param useSaved if a saved folder was found, should it be used.
	 */
	public WrappedServer(ServerInstances instance, WrappedServer existing, boolean useSaved) {
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.commands.addAll(existing.getCommands());
		this.template = existing.getTemplate();
		this.folder = existing.getFolder();
		this.name = existing.getName();
		this.folder = setupFolder();
		this.useSaved = useSaved;
		this.instance = instance;
		this.Xmx = existing.Xmx;
		this.Xms = existing.Xms;
		if (this.folder == null)
			return;
		this.processBuilder = setupProcessBuilder();
		startup();
	}

	public WrappedServer(ServerInstances instance, String name, String template, String Xmx, boolean useSaved, int port) {
		this.port = port;
		this.name = name;
		this.instance = instance;
		this.template = template;
		this.useSaved = useSaved;
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.Xms = configuration.getString("instances.default-Xms");
		this.Xmx = Xmx;
		this.folder = setupFolder();
		if (this.folder == null)
			return;
		prepare();
		startup();
	}

	public WrappedServer(ServerInstances instance, String name, String template, boolean useSaved, int port) {
		this.port = port;
		this.name = name;
		this.instance = instance;
		this.template = template;
		this.useSaved = useSaved;
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.Xms = configuration.getString("instances.default-Xms");
		this.Xmx = configuration.getString("instances.default-Xmx");
		this.folder = setupFolder();
		if (this.folder == null)
			return;
		prepare();
		startup();
	}

	public WrappedServer(ServerInstances instance, String name, String template, boolean useSaved) {
		this.name = name;
		this.instance = instance;
		this.template = template;
		this.useSaved = useSaved;
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.Xms = configuration.getString("instances.default-Xms");
		this.Xmx = configuration.getString("instances.default-Xmx");
		this.folder = setupFolder();
		if (this.folder == null)
			return;
		prepare();
		startup();
	}

	//For starting a brand new server and checking if it exists.
	public WrappedServer(ServerInstances instance, String name, String template, String Xmx, String Xms, boolean useSaved) {
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.useSaved = useSaved;
		this.template = template;
		this.instance = instance;
		this.name = template;
		this.Xmx = Xmx;
		this.Xms = Xms;
		this.folder = setupFolder();
		if (this.folder == null)
			return;
		prepare();
		startup();
	}

	public WrappedServer(ServerInstances instance, String name, String template, List<String> commands, boolean useSaved) {
		this.commands.addAll(commands);
		this.useSaved = useSaved;
		this.template = template;
		this.instance = instance;
		this.name = name;
		this.folder = setupFolder();
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.Xms = configuration.getString("instances.default-Xms");
		this.Xmx = configuration.getString("instances.default-Xmx");
		if (this.folder == null)
			return;
		this.processBuilder = setupProcessBuilder();
		startup();
	}

	private ProcessBuilder setupProcessBuilder() {
		return new ProcessBuilder(commands).directory(folder);
	}

	public OutputStream getOutputStream() {
		return outputStream;
	}

	public InputStream getInputStream() {
		return inputStream;
	}

	public InputStream getErrorStream() {
		return errors;
	}

	public List<String> getCommands() {
		return commands;
	}

	public long getStartedTime() {
		return started;
	}

	public Process getProcess() {
		return process;
	}


	public File getFolder() {
		return folder;
	}

	public String getName() {
		return name;
	}

	public String getXmx() {
		return validate(Xmx, "default-Xmx");
	}

	/*
	 * This will cause the server to restart.
	*/
	public void setXmx(String xmx) {
		Xmx = xmx;
		restart(false);
	}

	public String getXms() {
		return validate(Xms, "default-Xms");
	}

	/*
	 * This will cause the server to restart.
	*/
	public void setXms(String xms) {
		Xms = xms;
		restart(false);
	}

	public boolean canUseSaved() {
		return useSaved;
	}

	public boolean isRunning() {
		return isRunning;
	}

	public String getTemplate() {
		return template;
	}

	public String getMotd() {
		return motd;
	}

	public int getPort() {
		return port;
	}

	public void setRunning(Boolean running) {
		if (this.isRunning && !running) shutdown();
		this.isRunning = running;
	}

	//Check that the output is logical.
	private String validate(String input, String node) {
		input = input.replaceAll("( |-)", "");
		if (!input.contains("M") || !input.contains("G")) input = Integer.parseInt(input) + "M";
		if (Integer.parseInt(input.replaceAll("(M|G)", "")) < 50) input = configuration.getString("instances." + node, "250M");
		return "-" + input;
	}

	public File getJar() {
		File[] jars = folder.listFiles(file -> file.getName().equalsIgnoreCase(configuration.getString("instances.jar-name", "spigot.jar")));
		return (jars == null || jars.length <= 0) ? null : jars[0];
	}

	@SuppressWarnings("deprecation")
	public ServerInfo getServerInfo() {
		InetSocketAddress address = new InetSocketAddress("0.0.0.0", getPort());
		ProxyServer proxy = instance.getProxy();
		return proxy.getServers().values().stream()
				.filter(info -> info.getAddress().equals(address))
				.findFirst()
				.orElse(proxy.constructServerInfo(name, address, getMotd(), false));
	}

	private File setupFolder() {
		int spot = 1;
		String nameCopy = name;
		while (serverManager.containsName(nameCopy)) {
			nameCopy = name + "-" + spot;
			spot++;
		}
		this.name = nameCopy;
		File runningFolder = new File(serverManager.getRunningServerFolder(), name);
		if (runningFolder.exists()) {
			instance.consoleMessage("There was already a server directory under the name: " + name);
			if (serverManager.getInstances().stream().anyMatch(instance -> instance.getName().equalsIgnoreCase(name))) {
				instance.consoleMessage("There was already a server running under the name: " + name + ". Aborting creation.");
				//TODO maybe handle stopping the already running server?
				return null;
			}
			//TODO handle deletion if the user doesn't want it for some reason.
			runningFolder.delete();
			try {
				Files.delete(runningFolder.toPath());
			} catch (IOException e) {}
		}
		File templateFolder = new File(serverManager.getTemplateFolder() + File.separator + template);
		if (!templateFolder.exists() || templateFolder.listFiles() == null || templateFolder.listFiles().length <= 0) {
			instance.consoleMessage("The template: \"" + template + "\" was empty or non existant.");
			return null;
		} else {
			this.folder = templateFolder;
			if (getJar() == null) {
				instance.consoleMessage("The jar file for template: \"" + template + "\" was not found. Make sure the name matches what is in the config.yml");
				return null;
			}
		}
		if (serverManager.getInstances().size() >= configuration.getInt("instances.max-servers", 20)) {
			instance.consoleMessage("The maximum amount of server instances has been reached!");
			return null;
		}
		File[] files = folder.listFiles(file -> file.getName().endsWith(".properties"));
		if (files == null || files.length <= 0) {
			instance.consoleMessage("The template: \"" + template + "\" was missing a server.properties file.");
			return null;
		}
		File savedFolder = new File(serverManager.getSavedFolder() + File.separator + name);
		if (savedFolder.exists() && useSaved)
			folder = savedFolder;
		try {
			Utils.copyDirectory(folder, runningFolder);
		} catch (IOException exception) {
			instance.consoleMessage("Failed to copy the directory of template: " + template);
			exception.printStackTrace();
			return null;
		}
		setupPort();
		findMotd();
		return folder;
	}

	public void findMotd() {
		if (folder == null)
			return;
		File[] files = folder.listFiles(file -> file.getName().endsWith(".properties"));
		Properties properties = new Properties();
		InputStream input = null;
		try {
			input = new FileInputStream(files[0]);
			properties.load(input);
			this.motd = properties.getProperty("motd").replaceAll(Pattern.quote("%server%"), name);
		} catch (IOException exception) {
			instance.consoleMessage("There was an error loading the properties of template: " + name);
			exception.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException exception) {
					instance.consoleMessage("There was an error closing the InputStream of the properties reader for template: " + name);
					exception.printStackTrace();
				}
			}
		}
	}

	public void setupPort() {
		if (folder == null)
			return;
		if (port > 0)
			return;
		File[] files = folder.listFiles(file -> file.getName().endsWith(".properties"));
		Properties properties = new Properties();
		InputStream input = null;
		Set<Entry<Object, Object>> set = new HashSet<Entry<Object, Object>>();
		try {
			input = new FileInputStream(files[0]);
			properties.load(input);
			set = properties.entrySet();
		} catch (IOException exception) {
			instance.consoleMessage("There was an error loading the properties while setting up port of template: " + name);
			exception.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException exception) {
					instance.consoleMessage("There was an error closing the InputStream of the properties reader for template: " + name);
					exception.printStackTrace();
				}
			}
		}
		OutputStream output = null;
		try {
			output = new FileOutputStream(files[0]);
			this.port = Utils.findPort(configuration.getInt("instances.minimum-port", 25000), configuration.getInt("instances.maximum-port", 27000));
			for (Entry<Object, Object> entry : set) {
				if (entry.getKey().equals("server-port")) {
					properties.setProperty("server-port", this.port + "");
				} else properties.setProperty((String)entry.getKey(), (String) entry.getValue());
			}
			properties.store(output, null);
		} catch (IOException exception) {
			instance.consoleMessage("There was an error loading the properties of template: " + name);
			exception.printStackTrace();
		} finally {
			if (output != null) {
				try {
					output.close();
				} catch (IOException exception) {
					instance.consoleMessage("There was an error closing the OutputStream of the properties reader for template: " + name);
					exception.printStackTrace();
				}
			}
		}
	}

	private void prepare() {
		commands.add("java");
		if (Xmx.matches("(\\-Xmx)(.*)")) {
			commands.add(getXmx());
		} else {
			commands.add("-Xmx" + Xmx);
		}
		if (Xms.matches("(\\-Xms)(.*)")) {
			commands.add(getXms());
		} else {
			commands.add("-Xms" + Xms);
		}
		boolean isWindows = System.getProperty("os.name").matches("(?i)(.*)(windows)(.*)");
		if (isWindows) commands.add("-Djline.terminal=jline.UnsupportedTerminal");
		for (String command : configuration.getStringList("instances.command-arguments")) {
			commands.add(command);
		}
		commands.add("-jar");
		commands.add(getJar().getName());
		if (isWindows) commands.add("--nojline");
		processBuilder = setupProcessBuilder();
		/*linux screen support
		File screen = new File(ServerManager.getRunScriptsFolder(), "start-screen.sh");
		Object[] command = screen.exists() ? new String[]{"sh", ServerManager.getRunScriptsFolder() + "/start-screen.sh", name, ServerManager.getRunningServerFolder().getAbsolutePath(), getXmx(), getXms(), getJar().getName()} : (!getJar().getName().matches("^(?i)spigot.*\\.jar") ? new String[]{"screen", "-dmS", name, "java", getXmx(), getXms(), "-jar", getJar().getName()} : new String[]{"screen", "-dmS", name, "java", getXmx(), getXms(), "-Dcom.mojang.eula.agree=true", "-jar", getJar().getName()});
		ProcessBuilder processBuilder = new ProcessBuilder(new String[0]);
		processBuilder.command((String[])command);
		processBuilder.directory(ServerManager.getRunningServerFolder());
		getProcessRunner().queueProcess(this.getName(), processBuilder);
		*/
	}

	private void startup() {
		if (isRunning)
			return;
		setRunning(true);
		instance.consoleMessage("Starting up server " + name + "...");
		try {
			process = processBuilder.start();
			inputStream = process.getInputStream();
			errors = process.getErrorStream();
			outputStream = process.getOutputStream();
			serverManager.getProcesses().add(process);
			serverManager.addInstance(this);
			//TODO make a system to read the console of this process
		} catch (IOException exception) {
			instance.consoleMessage("Failed to start server: " + name);
			exception.printStackTrace();
		}
	}

	public void shutdown() {
		if (!isRunning)
			return;
		this.isRunning = false;
		instance.consoleMessage("Stopping server \"" + name + "\"...");
		try {
			outputStream.write("stop".getBytes());
			outputStream.flush();
		} catch (IOException exception) {
		} finally {
			try {
				inputStream.close();
				outputStream.flush();
				outputStream.close();
			} catch (IOException e) {
				//e.printStackTrace();
			}
		}
		inputStream = null;
		outputStream = null;
		System.gc();
		serverManager.removeInstance(this);
		kill();
		instance.getProxy().getScheduler().schedule(instance, () -> {
			File file = new File(serverManager.getRunningServerFolder(), name);
			instance.consoleMessage("Stopped server \"" + name + "\"");
			file.delete();
			try {
				Files.delete(file.toPath());
			} catch (IOException e) {}
		}, 5, TimeUnit.SECONDS);
	}

	public int getPlayerCount() {
		return getServerInfo().getPlayers().size();
	}

	public void join(ProxiedPlayer player) {
		Map<String, ServerInfo> servers = ProxyServer.getInstance().getServers();
		if (servers.containsKey(name)) {
			player.connect(servers.get(name), ServerConnectEvent.Reason.COMMAND);
			return;
		}
		InetSocketAddress address = new InetSocketAddress("0.0.0.0", getPort());
		ServerInfo serverInfo = instance.getProxy().constructServerInfo(name, address, getMotd(), false);
		player.connect(serverInfo);
	}

	private boolean autoSave = false;

	public void setAutoSave(boolean autoSave) {
		this.autoSave = autoSave;
	}

	public boolean isAutoSave() {
		return autoSave;
	}

	public void shutdown(boolean saving) {
		if (saving) {
			try {
				File savedFolder = new File(serverManager.getSavedFolder() + File.separator + name);
				if (savedFolder.exists())
					savedFolder.delete();
				Utils.copyDirectory(folder, savedFolder);
			} catch (IOException exception) {
				instance.consoleMessage("Failed to save the directory of server: " + template);
				exception.printStackTrace();
			}
		}
		shutdown();
	}

	public void restart(boolean save) {
		shutdown(save);
		prepare();
		startup();
	}

	public void kill() {
		process.destroy();
	}

}
