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
import me.limeglass.skungee.objects.BungeePacket;
import me.limeglass.skungee.objects.BungeePacketType;
import me.limeglass.skungee.spigot.utils.Utils;

public class WrappedServer {

	private String Xmx = ServerInstances.getConfiguration().getString("ServerInstances.default-Xmx");
	private String Xms = ServerInstances.getConfiguration().getString("ServerInstances.default-Xms");
	private InputStream inputStream, errors;
	private ProcessBuilder processBuilder;
	private Boolean isRunning = false;
	private OutputStream outputStream;
	private List<String> commands;
	private final String template;
	private String name, motd;
	private Process process;
	private File folder;
	private int port;
	
	public WrappedServer(String name, String template) {
		this.template = template;
		this.name = name;
		this.folder = setupFolder();
		if (this.folder == null) return;
		prepare();
		startup();
	}
	
	//For starting a brand new server and checking if it exists.
	public WrappedServer(String name, String template, String Xmx, String Xms) {
		this.template = template;
		this.name = template;
		this.Xmx = Xmx;
		this.Xms = Xms;
		this.folder = setupFolder();
		if (this.folder == null) return;
		prepare();
		startup();
	}
	
	public WrappedServer(WrappedServer existing) {
		this.commands = existing.getCommands();
		this.template = existing.getTemplate();
		this.folder = existing.getFolder();
		this.name = existing.getName();
		this.folder = setupFolder();
		this.Xmx = existing.Xmx;
		this.Xms = existing.Xms;
		if (this.folder == null) return;
		this.processBuilder = setupProcessBuilder();
		startup();
	}
	
	public WrappedServer(String name, String template, List<String> commands, String path) {
		this.template = template;
		this.commands = commands;
		this.name = name;
		this.folder = setupFolder();
		if (this.folder == null) return;
		this.processBuilder = setupProcessBuilder();
		startup();
	}
	
	private ProcessBuilder setupProcessBuilder() {
		ServerInstances.debugMessage("Command for server " + name + " was created: " + commands.toString());
		return new ProcessBuilder(commands).directory(folder);
	}
	
	public int getPort() {
		return port;
	}
	
	public Process getProcess() {
		return process;
	}
	
	public InputStream getInputStream() {
		return inputStream;
	}
	
	public OutputStream getOutputStream() {
		return outputStream;
	}
	
	public InputStream getErrorStream() {
		return errors;
	}
	
	public File getFolder() {
		return folder;
	}
	
	public List<String> getCommands() {
		return commands;
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

	public void setRunning(Boolean running) {
		if (this.isRunning && !running) shutdown();
		this.isRunning = running;
	}
	
	//Check that the output is logical.
	private String validate(String input, String node) {
		input = input.replaceAll("( |-)", "");
		if (!input.contains("M") || !input.contains("G")) input = Integer.parseInt(input) + "M";
		if (Integer.parseInt(input.replaceAll("(M|G)", "")) < 50) input = ServerInstances.getConfiguration().getString("ServerInstances." + node, "250M");
		return "-" + input;
	}
	
	public File getJar() {
		File[] jars = folder.listFiles(file -> file.getName().equalsIgnoreCase(ServerInstances.getConfiguration().getString("ServerInstances.jar-name", "spigot.jar")));
		return (jars == null || jars.length <= 0) ? null : jars[0];
	}
	
	private File setupFolder() {
		int spot = 1;
		String nameCopy = name;
		while (ServerHelper.serverExists(nameCopy)) {
			nameCopy = name + "-" + spot;
			spot++;
		}
		this.name = nameCopy;
		File runningFolder = new File(ServerManager.getRunningServerFolder() + File.separator + name);
		if (runningFolder.exists()) {
			ServerInstances.consoleMessage("There was already a server directory under the name: " + name);
			if (ServerManager.getInstances().containsKey(name)) {
				ServerInstances.consoleMessage("There was already a server running under the name: " + name + ". Aborting creation.");
				//TODO maybe handle stopping the already running server?
				return null;
			}
			//TODO handle deletion if the user doesn't want it for some reason.
			runningFolder.delete();
		}
		File templateFolder = new File(ServerManager.getTemplateFolder() + File.separator + template);
		if (!templateFolder.exists() || templateFolder.listFiles() == null || templateFolder.listFiles().length <= 0) {
			ServerInstances.consoleMessage("The template: \"" + template + "\" was empty or non existant.");
			return null;
		} else {
			this.folder = templateFolder;
			if (getJar() == null) {
				ServerInstances.consoleMessage("The jar file for template: \"" + template + "\" was not found. Make sure the name matches what is in the serverinstances.yml");
				return null;
			}
		}
		if (ServerManager.getInstances().size() >= ServerInstances.getConfiguration().getInt("ServerInstances.max-servers", 25)) {
			ServerInstances.consoleMessage("The maximum amount of ServerInstances has been reached!");
			return null;
		}
		File[] files = folder.listFiles(file -> file.getName().endsWith(".properties"));
		if (files == null || files.length <= 0) {
			ServerInstances.consoleMessage("The template: \"" + template + "\" was missing a server.properties file.");
			return null;
		}
		File savedFolder = new File(ServerManager.getSavedFolder() + File.separator + name);
		if (savedFolder.exists()) {
			ServerInstances.debugMessage("Found a saved server folder under the name '" + name + "' Using that now.");
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
			this.port = Utils.findPort(ServerInstances.getConfiguration().getInt("ServerInstances.minimum-port", 25000), ServerInstances.getConfiguration().getInt("ServerInstances.maximum-port", 27000));
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
		commands = new ArrayList<String>();
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
		for (String command : ServerInstances.getConfiguration().getStringList("ServerInstances.command-arguments")) {
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
			ServerManager.addInstance(this);
			ServerInstances.consoleMessage("Starting up server " + name + "...");
			try {
				process = processBuilder.start();
				inputStream = process.getInputStream();
				errors = process.getErrorStream();
				outputStream = process.getOutputStream();
				ServerManager.getProcesses().add(process);
				//TODO make a system to read the console of this process
			} catch (IOException exception) {
				Skungee.exception(exception, "Failed to start server: " + name);
			}
		}
	}
	
	public void shutdown() {
		if (isRunning) {
			this.isRunning = false;
			ServerInstances.debugMessage("Stopping server \"" + name + "\"...");
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
			final File file = new File(ServerManager.getRunningServerFolder(), name);
			try {
				Thread.sleep(1500);
			} catch (InterruptedException e) {}
			ServerManager.removeInstance(this);
			kill();
			ServerInstances.consoleMessage("Stopped server \"" + name + "\"");
			file.delete();
		}
	}
	
	public void shutdown(Boolean saving) {
		if (saving) {
			try {
				File savedFolder = new File(ServerManager.getSavedFolder() + File.separator + name);
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