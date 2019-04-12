package me.limeglass.serverinstances.listeners;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import me.limeglass.serverinstances.ServerInstances;
import me.limeglass.skungee.bungeecord.Skungee;
import me.limeglass.skungee.objects.packets.ServerInstancesPacket;
import net.md_5.bungee.config.Configuration;

public class SyntaxListener implements Runnable {

	private final Configuration configuration;
	private final ServerInstances instance;
	private final InetAddress address;
	private final Skungee skungee;
	private final Socket socket;

	public SyntaxListener(ServerInstances instance, Socket socket) {
		this.socket = socket;
		this.instance = instance;
		this.address = socket.getInetAddress();
		this.configuration = Skungee.getConfig();
		this.skungee = instance.getSkungeeInstance();
	}

	@Override
	public void run() {
		if (configuration.getBoolean("security.breaches.enabled", false)) {
			List<String> addresses = configuration.getStringList("security.breaches.blacklisted");
			if (addresses.contains(address.getHostName()))
				return;
		}
		try {
			ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
			Object object = objectInputStream.readObject();
			if (object != null) {
				ServerInstancesPacket packet;
				try {
					if (configuration.getBoolean("security.encryption.enabled", false)) {
						byte[] decoded = Base64.getDecoder().decode((byte[]) object);
						Thread.currentThread().setContextClassLoader(skungee.getClass().getClassLoader());
						packet = (ServerInstancesPacket) Skungee.getEncrypter().deserialize(decoded);
					} else {
						packet = (ServerInstancesPacket) object;
					}
				} catch (ClassCastException e) {
					Skungee.exception(e, "Some security settings didn't match for the incoming packet.", "Make sure all your security options on the Spigot servers match the same as in the Bungeecord Skungee config.yml", "The packet could not be read, thus being cancelled.", "");
					return;
				}
				if (packet.getPassword() != null) {
					if (configuration.getBoolean("security.password.hash", true)) {
						if (configuration.getBoolean("security.password.hashFile", false) && Skungee.getEncrypter().isFileHashed()) {
							if (!Arrays.equals(Skungee.getEncrypter().getHashFromFile(), packet.getPassword())) {
								incorrectPassword(packet);
								return;
							}
						} else if (!Arrays.equals(Skungee.getEncrypter().hash(), packet.getPassword())) {
							incorrectPassword(packet);
							return;
						}
					} else {
						String password = (String) Skungee.getEncrypter().deserialize(packet.getPassword());
						if (!password.equals(configuration.getString("security.password.password"))){
							incorrectPassword(packet);
							return;
						}
					}
				} else if (configuration.getBoolean("security.password.enabled", false)) {
					incorrectPassword(packet);
					return;
				}
				Object packetData = instance.getHandler().handlePacket(packet, socket.getInetAddress());
				if (packetData != null) {
					if (configuration.getBoolean("security.encryption.enabled", false)) {
						byte[] serialized = Skungee.getEncrypter().serialize(packetData);
						objectOutputStream.writeObject(Base64.getEncoder().encode(serialized));
					} else {
						objectOutputStream.writeObject(packetData);
					}
				}
			}
			objectInputStream.close();
			objectOutputStream.close();
			socket.close();
		} catch (IOException | ClassNotFoundException e) {}
	}

	private void incorrectPassword(ServerInstancesPacket packet) {
		instance.consoleMessage("&cA ServerInstancesPacket with an incorrect password has just been recieved and blocked!");
		instance.consoleMessage("&cThe packet came from: " + socket.getInetAddress());
		instance.consoleMessage("&cThe packet type was: " + packet.getType());
	}

}
