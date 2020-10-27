package com.skungee.serverinstances.spigot;

import java.io.IOException;

import org.bukkit.plugin.java.JavaPlugin;

import com.skungee.shared.Skungee;
import com.skungee.spigot.SpigotSkungee;

import ch.njol.skript.Skript;

public class ServerInstancesSpigot extends JavaPlugin {

	private static ServerInstancesSpigot instance;
	private SpigotSkungee skungee;

	@Override
	public void onEnable() {
		instance = this;
		skungee = (SpigotSkungee) Skungee.getPlatform();
		try {
			Skript.registerAddon(this).loadClasses("com.skungee.serverinstances.spigot", "elements");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static ServerInstancesSpigot getInstance() {
		return instance;
	}

	public SpigotSkungee getSkungee() {
		return skungee;
	}

}
