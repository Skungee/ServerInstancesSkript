package com.skungee.serverinstances.spigot.elements;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.bukkit.event.Event;
import org.eclipse.jdt.annotation.Nullable;

import com.sitrica.japson.gson.JsonArray;
import com.sitrica.japson.gson.JsonObject;
import com.sitrica.japson.shared.Packet;
import com.skungee.shared.Packets;
import com.skungee.spigot.SpigotSkungee;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Name;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;

@Name("ServerInstances - Start template server")
@Description("Creates a new server on the proxy based off your templates installed. Tutorial within the expansion.")
public class EffCreateServer extends Effect {

	static {
		Skript.registerEffect(EffCreateServer.class, "(start|create) [a] [new] [bungee[[ ]cord]] server[s] (with|from) [the] template[s] %strings%");
	}

	private Expression<String> templates;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		templates = (Expression<String>) exprs[0];
		return true;
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		if (debug)
			return "Start templates";
		return "Start templates " + templates.toString(event, debug);
	}

	@Override
	protected void execute(Event event) {
		SpigotSkungee instance = SpigotSkungee.getInstance();
		Packet packet = new Packet(Packets.API.getPacketId()) {
			@Override
			public JsonObject toJson() {
				JsonObject object = new JsonObject();
				JsonArray array = new JsonArray();
				for (String template : templates.getArray(event))
					array.add(template);
				object.add("templates", array);
				object.addProperty("serverinstances", instance.getDescription().getVersion());
				return object;
			}
		};
		try {
			instance.getJapsonClient().sendPacket(packet);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			instance.debugMessage("Failed to send start template packet");
		}
	}

}
