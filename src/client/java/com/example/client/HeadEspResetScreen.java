package com.example.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HeadEspResetScreen extends Screen {
	private boolean reset;

	public HeadEspResetScreen() {
		super(Component.translatable("screen.playerheadesp.manage_heads"));
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int centerY = this.height / 2;
		this.addRenderableWidget(Button.builder(Component.translatable("button.playerheadesp.reset"), button -> {
			HeadEspRenderer.resetHiddenHeads();
			this.reset = true;
		}).bounds(centerX - 100, centerY - 10, 200, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
				.bounds(centerX - 100, centerY + 20, 200, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);
		Component status = this.reset
				? Component.translatable("message.playerheadesp.reset_done")
				: Component.translatable("message.playerheadesp.hidden_count", HeadEspRenderer.hiddenHeadCount());
		graphics.drawCenteredString(this.font, status, this.width / 2, this.height / 2 - 35, 0xFFFFFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
