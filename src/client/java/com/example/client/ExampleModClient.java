package com.example.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class ExampleModClient implements ClientModInitializer {
	public static boolean ESP_ENABLED = false;
	private static KeyMapping toggleKey;
	private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath("playerheadesp", "main"));

	@Override
	public void onInitializeClient() {
		HeadEspRenderer.initialize();
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.playerheadesp.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H,
			KEY_CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				ESP_ENABLED = !ESP_ENABLED;
				if (client.player != null) {
					client.player.displayClientMessage(Component.literal("Player Head ESP: " + (ESP_ENABLED ? "AN" : "AUS")), true);
				}
			}
		});
	}
}
