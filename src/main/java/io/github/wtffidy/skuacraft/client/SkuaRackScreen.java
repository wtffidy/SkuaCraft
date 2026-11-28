package io.github.wtffidy.skuacraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

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
    private final List<String> accountIds;

    public SkuaRackScreen(BlockPos rackPosition, List<String> accountIds) {
        super(Component.literal("Skua Server Rack"));
        this.rackPosition = rackPosition.immutable();
        this.accountIds = List.copyOf(accountIds);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);

        int panelWidth = Math.min(420, width - 32);
        int panelHeight = Math.min(280, height - 32);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        graphics.fill(left, top, left + panelWidth, top + 2, TEAL);
        graphics.fill(left, top + 32, left + panelWidth, top + 33, EDGE);

        graphics.drawString(font, "SKUA SERVER RACK", left + 14, top + 12, TEAL, false);
        graphics.drawString(font, "NODE  " + rackPosition.toShortString(), left + panelWidth - 118, top + 12, MUTED, false);

        if (accountIds.isEmpty()) {
            graphics.drawCenteredString(font, "NO CONTROLLERS INSTALLED", width / 2, top + 88, TEXT);
            graphics.drawCenteredString(font, "Hold a Skua handheld and right-click the rack to insert it.",
                    width / 2, top + 108, MUTED);
        } else {
            graphics.drawString(font, "ACCOUNT SESSIONS  /  " + accountIds.size() + " OF 6",
                    left + 14, top + 44, MUTED, false);

            List<String> running = PlayerHostLauncher.getRunningAccountIds();
            for (int index = 0; index < accountIds.size(); index++) {
                String accountId = accountIds.get(index);
                int rowTop = top + 64 + index * 30;
                boolean hovered = mouseX >= left + 12 && mouseX <= left + panelWidth - 12
                        && mouseY >= rowTop && mouseY <= rowTop + 25;
                graphics.fill(left + 12, rowTop, left + panelWidth - 12, rowTop + 25,
                        hovered ? 0xFF1F3947 : 0xFF1B2936);

                String label = accountId.equals(PlayerHostLauncher.DEFAULT_ACCOUNT_ID)
                        ? "DEFAULT ACCOUNT"
                        : "ACCOUNT " + accountId.substring(0, Math.min(8, accountId.length())).toUpperCase();
                boolean isRunning = running.contains(accountId);
                graphics.drawString(font, label, left + 22, rowTop + 9, TEXT, false);
                graphics.drawString(font, isRunning ? "RUNNING" : "STARTING",
                        left + panelWidth - 86, rowTop + 9, isRunning ? TEAL : MUTED, false);
            }
        }

        graphics.fill(left, top + panelHeight - 35, left + panelWidth, top + panelHeight - 34, EDGE);
        graphics.drawString(font, "CLICK AN ACCOUNT TO DISPLAY IT", left + 14, top + panelHeight - 23, MUTED, false);
        graphics.drawString(font, "SHIFT + RIGHT-CLICK THE RACK TO EJECT",
                left + 14, top + panelHeight - 10, MUTED, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || accountIds.isEmpty()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int panelWidth = Math.min(420, width - 32);
        int panelHeight = Math.min(280, height - 32);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;

        for (int index = 0; index < accountIds.size(); index++) {
            int rowTop = top + 64 + index * 30;
            if (mouseX >= left + 12 && mouseX <= left + panelWidth - 12
                    && mouseY >= rowTop && mouseY <= rowTop + 25) {
                Minecraft.getInstance().setScreen(new SkuaPlayerScreen(accountIds.get(index)));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
