package io.github.wtffidy.skuacraft.client;

import io.github.wtffidy.skuacraft.SkuaCraft;
import io.github.wtffidy.skuacraft.block.entity.SkuaRackBlockEntity;
import io.github.wtffidy.skuacraft.network.RackEjectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class SkuaRackScreen extends Screen {
    private static final int BACKGROUND = 0xFF080D14;
    private static final int PANEL = 0xFF131D29;
    private static final int EDGE = 0xFF324658;
    private static final int TEAL = 0xFF57E6C1;
    private static final int MUTED = 0xFF91A5B4;
    private static final int TEXT = 0xFFE8F1F4;

    private final BlockPos rackPosition;
    private final List<SkuaRackBlockEntity.AccountSlot> slots;

    public SkuaRackScreen(BlockPos rackPosition, List<SkuaRackBlockEntity.AccountSlot> slots) {
        super(Component.literal("Skua Server Rack"));
        this.rackPosition = rackPosition.immutable();
        this.slots = new ArrayList<>(slots);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);

        int panelWidth = Math.min(440, width - 32);
        int panelHeight = Math.min(240, height - 32);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        graphics.fill(left, top, left + panelWidth, top + 2, TEAL);
        graphics.fill(left, top + 32, left + panelWidth, top + 33, EDGE);

        graphics.drawString(font, "SKUA SERVER RACK", left + 14, top + 12, TEAL, false);
        graphics.drawString(font, "NODE  " + rackPosition.toShortString(),
                left + panelWidth - 118, top + 12, MUTED, false);

        graphics.drawString(font, "ACCOUNT BAYS  /  SIX SLOTS", left + 14, top + 40, MUTED, false);
        List<String> running = PlayerHostLauncher.getRunningAccountIds();

        for (int index = 0; index < slots.size(); index++) {
            SkuaRackBlockEntity.AccountSlot slot = slots.get(index);
            int rowTop = top + 54 + index * 22;
            boolean hovered = mouseX >= left + 12 && mouseX <= left + panelWidth - 12
                    && mouseY >= rowTop && mouseY <= rowTop + 19;
            graphics.fill(left + 12, rowTop, left + panelWidth - 12, rowTop + 19,
                    hovered ? 0xFF1F3947 : 0xFF1B2936);

            String label = slot.isEmpty()
                    ? "BAY " + (slot.slot() + 1) + "    EMPTY"
                    : "BAY " + (slot.slot() + 1) + "    "
                    + (slot.accountId().equals(PlayerHostLauncher.DEFAULT_ACCOUNT_ID)
                    ? "DEFAULT ACCOUNT"
                    : "ACCOUNT " + slot.accountId().substring(0, Math.min(8, slot.accountId().length())).toUpperCase());
            boolean isRunning = !slot.isEmpty() && running.contains(slot.accountId());
            graphics.drawString(font, label, left + 20, rowTop + 6, slot.isEmpty() ? MUTED : TEXT, false);
            graphics.drawString(font, slot.isEmpty() ? "EMPTY" : isRunning ? "RUNNING" : "STARTING",
                    left + panelWidth - 83, rowTop + 6, isRunning ? TEAL : MUTED, false);
        }

        graphics.fill(left, top + panelHeight - 30, left + panelWidth, top + panelHeight - 29, EDGE);
        graphics.drawString(font, "LEFT CLICK  DISPLAY",
                left + 14, top + panelHeight - 20, MUTED, false);
        graphics.drawString(font, "RIGHT CLICK  EJECT  |  CTRL+RIGHT CLICK  STOP + EJECT",
                left + 160, top + panelHeight - 20, MUTED, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button != 0 && button != 1) || slots.isEmpty()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int panelWidth = Math.min(440, width - 32);
        int panelHeight = Math.min(240, height - 32);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;

        for (int index = 0; index < slots.size(); index++) {
            SkuaRackBlockEntity.AccountSlot slot = slots.get(index);
            int rowTop = top + 54 + index * 22;
            if (mouseX < left + 12 || mouseX > left + panelWidth - 12
                    || mouseY < rowTop || mouseY > rowTop + 19 || slot.isEmpty()) {
                continue;
            }

            if (button == 0) {
                Minecraft.getInstance().setScreen(new SkuaPlayerScreen(slot.accountId()));
                return true;
            }

            if (Screen.hasControlDown()) {
                PlayerHostLauncher.stop(slot.accountId());
            }

            SkuaCraft.NETWORK.sendToServer(new RackEjectPacket(rackPosition, slot.slot()));
            slots.set(index, new SkuaRackBlockEntity.AccountSlot(slot.slot(), ""));
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
