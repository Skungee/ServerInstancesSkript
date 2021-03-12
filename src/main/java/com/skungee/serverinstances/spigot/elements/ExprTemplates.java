package com.skungee.serverinstances.spigot.elements;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.bukkit.event.Event;
import org.eclipse.jdt.annotation.Nullable;

import com.skungee.japson.gson.JsonObject;
import com.skungee.spigot.SpigotSkungee;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Name;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;

@Name("ServerInstances - Templates")
@Description("Returns a list of all the templates registered on the ServerInstances.")
public class ExprTemplates extends SimpleExpression<String> {

	static {
		Skript.registerExpression(ExprTemplates.class, String.class, ExpressionType.SIMPLE, "[(all [[of] the]|the)] templates [(on|for) server instances]");
	}

	@Override
	public Class<? extends String> getReturnType() {
		return String.class;
	}

	@Override
	public boolean isSingle() {
		return false;
	}

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		return true;
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return "templates";
	}

	@Override
	@Nullable
	protected String[] get(Event event) {
		SpigotSkungee instance = SpigotSkungee.getInstance();
		JsonObject object = new JsonObject();
		object.addProperty("serverinstances", instance.getDescription().getVersion());
		object.addProperty("type", "templates");
		try {
			return instance.getAPI().sendPacket(object, json -> {
				Set<String> templates = new HashSet<>();
				json.get("templates").getAsJsonArray().forEach(element -> {
					String template = element.getAsString();
					templates.add(template);
				});
				return templates.toArray(new String[templates.size()]);
			});
		} catch (TimeoutException | InterruptedException | ExecutionException e) {
			instance.debugMessage("Failed to send template expression packet");
			return null;
		}
	}

}
