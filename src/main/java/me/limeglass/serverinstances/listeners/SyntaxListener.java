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
import me.limeglass.serverinstances.managers.InstancesPacketHandler;
import me.limeglass.skungee.bungeecord.Skungee;
import me.limeglass.skungee.objects.ServerInstancesPacket;

public class SyntaxListener implements Runnable {

	private InetAddress address;
	private Socket socket;

	public SyntaxListener(Socket socket) {
		this.socket = socket;
		this.address = socket.getInetAddress();
	}

	@Override
	public void run() {
		if (Skungee.getConfig().getBoolean("security.breaches.enabled", false)) {
			List<String> addresses = Skungee.getConfig().getStringList("security.breaches.blacklisted");
			if (addresses.contains(address.getHostName())) return;
		}
		try {
			ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
			Object object = objectInputStream.readObject();
			if (object != null) {
				ServerInstancesPacket packet;
				try {
					if (Skungee.getConfig().getBoolean("security.encryption.enabled", false)) {
						byte[] decoded = Base64.getDecoder().decode((byte[]) object);
						Thread.currentThread().setContextClassLoader(Skungee.getInstance().getClass().getClassLoader());
						packet = (ServerInstancesPacket) Skungee.getEncrypter().deserialize(decoded);
					} else {
						packet = (ServerInstancesPacket) object;
					}
				} catch (ClassCastException e) {
					Skungee.exception(e, "Some security settings didn't match for the incoming packet.", "Make sure all your security options on the Spigot servers match the same as in the Bungeecord Skungee config.yml", "The packet could not be read, thus being cancelled.", "");
					return;
				}
				if (packet.getPassword() != null) {
					if (Skungee.getConfig().getBoolean("security.password.hash", true)) {
						if (Skungee.getConfig().getBoolean("security.password.hashFile", false) && Skungee.getEncrypter().isFileHashed()) {
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
						if (!password.equals(Skungee.getConfig().getString("security.password.password"))){
							incorrectPassword(packet);
							return;
						}
					}
				} else if (Skungee.getConfig().getBoolean("security.password.enabled", false)) {
					incorrectPassword(packet);
					return;
				}
				Object packetData = InstancesPacketHandler.handlePacket(packet, socket.getInetAddress());
				if (packetData != null) {
					if (Skungee.getConfig().getBoolean("security.encryption.enabled", false)) {
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
		ServerInstances.consoleMessage("&cA ServerInstancesPacket with an incorrect password has just been recieved and blocked!");
		ServerInstances.consoleMessage("&cThe packet came from: " + socket.getInetAddress());
		ServerInstances.consoleMessage("&cThe packet type was: " + packet.getType());
	}
}