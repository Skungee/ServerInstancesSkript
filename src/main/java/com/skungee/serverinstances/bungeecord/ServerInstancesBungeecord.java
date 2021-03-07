package com.skungee.serverinstances.bungeecord;

import java.io.IOException;
import java.net.InetAddress;
import java.sql.SQLException;
import java.util.Optional;

import com.sitrica.japson.gson.JsonArray;
import com.sitrica.japson.gson.JsonObject;
import com.sitrica.japson.shared.Executor;
import com.sitrica.japson.shared.Handler;
import com.skungee.bungeecord.BungeeSkungee;
import com.skungee.serverinstances.ServerInstances;
import com.skungee.serverinstances.objects.Template;
import com.skungee.shared.Packets;

import net.md_5.bungee.api.plugin.Plugin;

public class ServerInstancesBungeecord extends Plugin {

	private static ServerInstancesBungeecord instance;
	private ServerInstances serverInstances;

	@Override
	public void onEnable() {
		instance = this;
		try {
			serverInstances = new ServerInstances(this);
		} catch (ClassNotFoundException | SQLException | IOException e) {
			e.printStackTrace();
			return;
		}
		BungeeSkungee.getAPI().registerHandler(new Executor(Packets.API.getPacketId()) {
			@Override
			public void execute(InetAddress address, int port, JsonObject object) {
				if (!object.has("serverinstances") || !object.has("templates") || !object.has("type"))
					return;
				if (!object.get("type").getAsString().equals("create"))
					return;
				if (!object.get("serverinstances").getAsString().equals(getDescription().getVersion()))
					throw new IllegalStateException("The versions of ServerInstances from the incoming packet on Spigot did not match that of the ServerInstances version running on the Proxy.");
				object.get("templates").getAsJsonArray().forEach(element -> {
					String name = element.getAsString();
					Optional<Template> optional = serverInstances.getTemplates().stream().filter(template -> template.getName().equals(name)).findFirst();
					if (!optional.isPresent())
						return;
					try {
						serverInstances.createInstance(optional.get());
					} catch (IOException | IllegalAccessException e) {
						e.printStackTrace();
					}
				});
			}
		}, new Handler(Packets.API.getPacketId()) {
			@Override
			public JsonObject handle(InetAddress address, int port, JsonObject object) {
				if (!object.has("serverinstances") || !object.has("type"))
					return null;
				if (!object.get("type").getAsString().equals("templates"))
					return null;
				if (!object.get("serverinstances").getAsString().equals(getDescription().getVersion()))
					throw new IllegalStateException("The versions of ServerInstances from the incoming packet on Spigot did not match that of the ServerInstances version running on the Proxy.");
				JsonObject returning = new JsonObject();
				JsonArray templates = new JsonArray();
				serverInstances.getTemplates().forEach(template -> templates.add(template.getName()));
				returning.add("templates", templates);
				return returning;
			}
		});
	}

	public static ServerInstancesBungeecord getInstance() {
		return instance;
	}

	public ServerInstances getServerInstances() {
		return serverInstances;
	}

}
