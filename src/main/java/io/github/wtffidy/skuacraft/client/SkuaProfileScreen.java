package io.github.wtffidy.skuacraft.client;

import io.github.wtffidy.skuacraft.SkuaCraft;
import io.github.wtffidy.skuacraft.network.RenameConsolePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class SkuaProfileScreen extends Screen {
    private static final int PANEL = 0xFF131D29;
    private static final int EDGE = 0xFF324658;
    private static final int TEAL = 0xFF57E6C1;
    private static final int MUTED = 0xFF91A5B4;

    private final String accountId;
    private final InteractionHand hand;
    private final String initialName;
    private EditBox profileName;

    public SkuaProfileScreen(String accountId, InteractionHand hand, String initialName) {
        super(Component.literal("Rename Skua Profile"));
        this.accountId = accountId;
        this.hand = hand;
        this.initialName = initialName == null ? "" : initialName;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(340, width - 32);
        int left = (width - panelWidth) / 2;
        int top = height / 2 - 62;

        profileName = new EditBox(font, left + 18, top + 46, panelWidth - 36, 20,
                Component.literal("Profile name"));
        profileName.setMaxLength(24);
        profileName.setValue(initialName);
        profileName.setFocused(true);
        addRenderableWidget(profileName);

        addRenderableWidget(Button.builder(Component.literal("SAVE PROFILE"), button -> save())
                .bounds(left + 18, top + 82, 146, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("CANCEL"), button -> onClose())
                .bounds(left + panelWidth - 164, top + 82, 146, 20)
                .build());
    }

    private void save() {
        String name = profileName.getValue().strip();
        if (name.isBlank()) {
            return;
        }

        SkuaCraft.NETWORK.sendToServer(new RenameConsolePacket(hand, name));
        SkuaPlayerBrowserSession.setProfileName(accountId, name);
        minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelWidth = Math.min(340, width - 32);
        int left = (width - panelWidth) / 2;
        int top = height / 2 - 62;

        graphics.fill(left, top, left + panelWidth, top + 112, PANEL);
        graphics.fill(left, top, left + panelWidth, top + 2, TEAL);
        graphics.fill(left, top + 30, left + panelWidth, top + 31, EDGE);
        graphics.drawCenteredString(font, "RENAME SKUA PROFILE", width / 2, top + 12, TEAL);
        graphics.drawString(font, "24 characters maximum", left + 18, top + 34, MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
