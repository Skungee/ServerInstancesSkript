package me.limeglass.serverinstances.objects;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import me.limeglass.serverinstances.ServerInstances;
import me.limeglass.serverinstances.managers.ServerManager;
import me.limeglass.serverinstances.utils.ServerHelper;
import me.limeglass.skungee.bungeecord.Skungee;
import me.limeglass.skungee.bungeecord.sockets.BungeeSockets;
import me.limeglass.skungee.bungeecord.sockets.ServerTracker;
import me.limeglass.skungee.objects.packets.BungeePacket;
import me.limeglass.skungee.objects.packets.BungeePacketType;
import me.limeglass.skungee.spigot.utils.Utils;
import net.md_5.bungee.config.Configuration;

public class WrappedServer {

	private InputStream inputStream, errors;
	private ProcessBuilder processBuilder;
	private OutputStream outputStream;
	private final String template;
	private boolean isRunning;
	private String name, motd;
	private Process process;
	private File folder;
	private int port;

	private final List<String> commands = new ArrayList<>();
	private final Configuration configuration;
	private final ServerManager serverManager;
	private final ServerHelper serverHelper;
	private final ServerInstances instance;
	private String Xmx, Xms;

	public WrappedServer(ServerInstances instance, WrappedServer existing) {
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.serverHelper = instance.getServerHelper();
		this.commands.addAll(existing.getCommands());
		this.template = existing.getTemplate();
		this.folder = existing.getFolder();
		this.name = existing.getName();
		this.folder = setupFolder();
		this.instance = instance;
		this.Xmx = existing.Xmx;
		this.Xms = existing.Xms;
		if (this.folder == null)
			return;
		this.processBuilder = setupProcessBuilder();
		startup();
	}

	public WrappedServer(ServerInstances instance, String name, String template) {
		this.name = name;
		this.instance = instance;
		this.template = template;
		this.serverHelper = instance.getServerHelper();
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.Xms = configuration.getString("ServerInstances.default-Xms");
		this.Xmx = configuration.getString("ServerInstances.default-Xmx");
		this.folder = setupFolder();
		if (this.folder == null)
			return;
		prepare();
		startup();
	}

	//For starting a brand new server and checking if it exists.
	public WrappedServer(ServerInstances instance, String name, String template, String Xmx, String Xms) {
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.serverHelper = instance.getServerHelper();
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

	public WrappedServer(ServerInstances instance, String name, String template, List<String> commands, String path) {
		this.commands.addAll(commands);
		this.template = template;
		this.instance = instance;
		this.name = name;
		this.folder = setupFolder();
		this.serverHelper = instance.getServerHelper();
		this.serverManager = instance.getServerManager();
		this.configuration = instance.getConfiguration();
		this.Xms = configuration.getString("ServerInstances.default-Xms");
		this.Xmx = configuration.getString("ServerInstances.default-Xmx");
		if (this.folder == null)
			return;
		this.processBuilder = setupProcessBuilder();
		startup();
	}

	private ProcessBuilder setupProcessBuilder() {
		instance.debugMessage("Command for server " + name + " was created: " + commands.toString());
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
		restart();
	}

	public String getXms() {
		return validate(Xms, "default-Xms");
	}

	/*
	 * This will cause the server to restart.
	*/
	public void setXms(String xms) {
		Xms = xms;
		restart();
	}

	public Boolean isRunning() {
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
		if (Integer.parseInt(input.replaceAll("(M|G)", "")) < 50) input = configuration.getString("ServerInstances." + node, "250M");
		return "-" + input;
	}

	public File getJar() {
		File[] jars = folder.listFiles(file -> file.getName().equalsIgnoreCase(configuration.getString("ServerInstances.jar-name", "spigot.jar")));
		return (jars == null || jars.length <= 0) ? null : jars[0];
	}

	private File setupFolder() {
		int spot = 1;
		String nameCopy = name;
		while (serverHelper.getServerInfo(nameCopy).isPresent()) {
			nameCopy = name + "-" + spot;
			spot++;
		}
		this.name = nameCopy;
		File runningFolder = new File(serverManager.getRunningServerFolder() + File.separator + name);
		if (runningFolder.exists()) {
			instance.consoleMessage("There was already a server directory under the name: " + name);
			if (serverManager.getInstances().containsKey(name)) {
				instance.consoleMessage("There was already a server running under the name: " + name + ". Aborting creation.");
				//TODO maybe handle stopping the already running server?
				return null;
			}
			//TODO handle deletion if the user doesn't want it for some reason.
			runningFolder.delete();
		}
		File templateFolder = new File(serverManager.getTemplateFolder() + File.separator + template);
		if (!templateFolder.exists() || templateFolder.listFiles() == null || templateFolder.listFiles().length <= 0) {
			instance.consoleMessage("The template: \"" + template + "\" was empty or non existant.");
			return null;
		} else {
			this.folder = templateFolder;
			if (getJar() == null) {
				instance.consoleMessage("The jar file for template: \"" + template + "\" was not found. Make sure the name matches what is in the serverinstances.yml");
				return null;
			}
		}
		if (serverManager.getInstances().size() >= configuration.getInt("ServerInstances.max-servers", 25)) {
			instance.consoleMessage("The maximum amount of ServerInstances has been reached!");
			return null;
		}
		File[] files = folder.listFiles(file -> file.getName().endsWith(".properties"));
		if (files == null || files.length <= 0) {
			instance.consoleMessage("The template: \"" + template + "\" was missing a server.properties file.");
			return null;
		}
		File savedFolder = new File(serverManager.getSavedFolder() + File.separator + name);
		if (savedFolder.exists()) {
			instance.debugMessage("Found a saved server folder under the name '" + name + "' Using that now.");
			folder = savedFolder;
		}
		try {
			Utils.copyDirectory(folder, runningFolder);
		} catch (IOException exception) {
			Skungee.exception(exception, "Failed to copy the directory of template: " + template);
		}
		setupPort();
		findMotd();
		return folder;
	}

	public void findMotd() {
		if (folder == null) return;
		File[] files = folder.listFiles(file -> file.getName().endsWith(".properties"));
		Properties properties = new Properties();
		InputStream input = null;
		try {
			input = new FileInputStream(files[0]);
			properties.load(input);
			this.motd = properties.getProperty("motd").replaceAll(Pattern.quote("%server%"), name);
		} catch (IOException exception) {
			Skungee.exception(exception, "There was an error loading the properties of template: " + name);
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException exception) {
					Skungee.exception(exception, "There was an error closing the InputStream of the properties reader for template: " + name);
				}
			}
		}
	}

	public void setupPort() {
		if (folder == null) return;
		File[] files = folder.listFiles(file -> file.getName().endsWith(".properties"));
		Properties properties = new Properties();
		InputStream input = null;
		Set<Entry<Object, Object>> set = new HashSet<Entry<Object, Object>>();
		try {
			input = new FileInputStream(files[0]);
			properties.load(input);
			set = properties.entrySet();
		} catch (IOException exception) {
			Skungee.exception(exception, "There was an error loading the properties while setting up port of template: " + name);
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException exception) {
					Skungee.exception(exception, "There was an error closing the InputStream of the properties reader for template: " + name);
				}
			}
		}
		OutputStream output = null;
		try {
			output = new FileOutputStream(files[0]);
			this.port = Utils.findPort(configuration.getInt("ServerInstances.minimum-port", 25000), configuration.getInt("ServerInstances.maximum-port", 27000));
			for (Entry<Object, Object> entry : set) {
				if (entry.getKey().equals("server-port")) {
					properties.setProperty("server-port", this.port + "");
				} else properties.setProperty((String)entry.getKey(), (String) entry.getValue());
			}
			properties.store(output, null);
		} catch (IOException exception) {
			Skungee.exception(exception, "There was an error loading the properties of template: " + name);
		} finally {
			if (output != null) {
				try {
					output.close();
				} catch (IOException exception) {
					Skungee.exception(exception, "There was an error closing the OutputStream of the properties reader for template: " + name);
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
		Boolean isWindows = System.getProperty("os.name").matches("(?i)(.*)(windows)(.*)");
		if (isWindows) commands.add("-Djline.terminal=jline.UnsupportedTerminal");
		for (String command : configuration.getStringList("ServerInstances.command-arguments")) {
			commands.add(command);
		}
		commands.add("-jar");
		commands.add(getJar().getName());
		if (isWindows) commands.add("--nojline");
		processBuilder = setupProcessBuilder();
		/*linux support
		File screen = new File(ServerManager.getRunScriptsFolder(), "start-screen.sh");
		Object[] command = screen.exists() ? new String[]{"sh", ServerManager.getRunScriptsFolder() + "/start-screen.sh", name, ServerManager.getRunningServerFolder().getAbsolutePath(), getXmx(), getXms(), getJar().getName()} : (!getJar().getName().matches("^(?i)spigot.*\\.jar") ? new String[]{"screen", "-dmS", name, "java", getXmx(), getXms(), "-jar", getJar().getName()} : new String[]{"screen", "-dmS", name, "java", getXmx(), getXms(), "-Dcom.mojang.eula.agree=true", "-jar", getJar().getName()});
		ProcessBuilder processBuilder = new ProcessBuilder(new String[0]);
		processBuilder.command((String[])command);
		processBuilder.directory(ServerManager.getRunningServerFolder());
		getProcessRunner().queueProcess(this.getName(), processBuilder);
		*/
	}

	private void startup() {
		if (!isRunning) {
			setRunning(true);
			serverManager.addInstance(this);
			instance.consoleMessage("Starting up server " + name + "...");
			try {
				process = processBuilder.start();
				inputStream = process.getInputStream();
				errors = process.getErrorStream();
				outputStream = process.getOutputStream();
				serverManager.getProcesses().add(process);
				//TODO make a system to read the console of this process
			} catch (IOException exception) {
				Skungee.exception(exception, "Failed to start server: " + name);
			}
		}
	}

	public void shutdown() {
		if (isRunning) {
			this.isRunning = false;
			instance.debugMessage("Stopping server \"" + name + "\"...");
			BungeeSockets.send(ServerTracker.getLocalByPort(port), new BungeePacket(false, BungeePacketType.SHUTDOWN));
			try {
				outputStream.write("stop".getBytes());
				outputStream.flush();
			} catch (IOException exception) {
			} finally {
				try {
					inputStream.close();
					inputStream = null;
					outputStream.flush();
					outputStream.close();
					outputStream = null;
			        System.gc();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
			final File file = new File(serverManager.getRunningServerFolder(), name);
			try {
				Thread.sleep(1500);
			} catch (InterruptedException e) {}
			serverManager.removeInstance(this);
			kill();
			instance.consoleMessage("Stopped server \"" + name + "\"");
			file.delete();
		}
	}

	public void shutdown(Boolean saving) {
		if (saving) {
			try {
				File savedFolder = new File(serverManager.getSavedFolder() + File.separator + name);
				if (savedFolder.exists()) savedFolder.delete();
				Utils.copyDirectory(folder, savedFolder);
			} catch (IOException exception) {
				Skungee.exception(exception, "Failed to save the directory of server: " + template);
			}
		}
		shutdown();
	}

	public void restart() {
		shutdown();
		prepare();
		startup();
	}

	public void kill() {
		process.destroy();
	}

}
