package io.github.wtffidy.skuacraft.client;

import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class SkuaPlayerScreen extends Screen {
    private static final int BACKGROUND = 0xFF080D14;
    private static final int SHELL = 0xFF131D29;
    private static final int SCREEN_EDGE = 0xFF324658;
    private static final int SCREEN_FILL = 0xFF020508;
    private static final int TEAL = 0xFF57E6C1;
    private static final int VIOLET = 0xFFB18AF5;
    private static final int TEXT = 0xFFE8F1F4;
    private static final int MUTED = 0xFF91A5B4;
    private static final int RED = 0xFFFF777D;

    private final SkuaToolsPanel toolsPanel = new SkuaToolsPanel();
    private RinkuBrowser browser;
    private String pageUrl = "";
    private String status = "Starting Skua player...";
    private boolean rinkuReady;
    private boolean hostReady;
    private boolean closing;

    private int browserX;
    private int browserY;
    private int browserWidth;
    private int browserHeight;

    public SkuaPlayerScreen() {
        super(Component.literal("Skua Player"));
    }

    @Override
    protected void init() {
        super.init();
        layoutBrowser();

        browser = SkuaPlayerBrowserSession.getBrowser();
        if (browser != null) {
            rinkuReady = true;
        } else {
            rinkuReady = Rinku.isInitialized();
            if (!rinkuReady) {
                status = "WAITING FOR RINKU BROWSER ENGINE";
                Rinku.scheduleForInit(successful -> Minecraft.getInstance().execute(() -> {
                    if (closing) {
                        return;
                    }

                    rinkuReady = successful;
                    if (!successful) {
                        status = "RINKU FAILED TO INITIALIZE  /  CHECK MINECRAFT LOG";
                        return;
                    }

                    status = hostReady ? "PLAYER CORE READY  /  OPENING GAME" : "RINKU READY  /  STARTING PLAYER CORE";
                    tryOpenPlayer();
                }));
            }
        }

        PlayerHostLauncher.ensureRunning(result -> {
            if (closing) {
                return;
            }

            hostReady = result.success();
            if (!hostReady) {
                PlayerControlClient.disconnect();
                status = result.message();
                return;
            }

            PlayerControlClient.connect(result.controlUrl(), result.controlToken());
            toolsPanel.refresh();
            pageUrl = result.pageUrl();
            status = rinkuReady ? "PLAYER CORE READY  /  OPENING GAME" : "PLAYER CORE READY  /  WAITING FOR RINKU";
            tryOpenPlayer();
        });

        if (browser != null) {
            browser.setFocus(true);
            resizeBrowser();
            status = "SKUA PLAYER ONLINE  /  RETURNING TO LIVE GAME";
        }
    }

    private void layoutBrowser() {
        browserX = 18;
        browserY = 48;
        browserWidth = Math.max(160, width - 36);
        browserHeight = Math.max(100, height - 78);
    }

    private void resizeBrowser() {
        if (browser == null || browserWidth <= 0 || browserHeight <= 0) {
            return;
        }

        double scale = minecraft.getWindow().getGuiScale();
        browser.resize(
                Math.max(1, (int) (browserWidth * scale)),
                Math.max(1, (int) (browserHeight * scale))
        );
    }

    private void tryOpenPlayer() {
        if (closing || browser != null || !hostReady || !rinkuReady || pageUrl.isBlank()) {
            return;
        }

        try {
            double scale = minecraft.getWindow().getGuiScale();
            int pixelWidth = Math.max(1, (int) (browserWidth * scale));
            int pixelHeight = Math.max(1, (int) (browserHeight * scale));
            browser = SkuaPlayerBrowserSession.openOrReuse(pageUrl, pixelWidth, pixelHeight);
            status = "SKUA PLAYER ONLINE  /  LOADING ADVENTUREQUEST WORLDS";
        } catch (RuntimeException exception) {
            status = "BROWSER ERROR  /  " + exception.getMessage();
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        layoutBrowser();
        resizeBrowser();
    }

    @Override
    public void tick() {
        super.tick();

        if (browser != null && browser.isTextureReady()
                && status.startsWith("SKUA PLAYER ONLINE")) {
            status = "SKUA PLAYER ONLINE  /  LIVE GAME VIEW";
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);

        graphics.fill(8, 8, width - 8, height - 8, SHELL);
        graphics.fill(12, 12, width - 12, 38, 0xFF0E1722);
        graphics.fill(12, 38, width - 12, 39, SCREEN_EDGE);
        graphics.fill(browserX - 3, browserY - 3,
                browserX + browserWidth + 3, browserY + browserHeight + 3, SCREEN_EDGE);
        graphics.fill(browserX, browserY,
                browserX + browserWidth, browserY + browserHeight, SCREEN_FILL);

        graphics.drawString(font, "SKUA SYSTEMS  /  HANDHELD PLAYER", 21, 20, TEAL, false);
        graphics.drawString(font, "RINKU  " + (rinkuReady ? "READY" : "STARTING"),
                width - 170, 20, rinkuReady ? TEAL : MUTED, false);
        graphics.fill(width - 83, 12, width - 12, 34, toolsPanel.isOpen() ? TEAL_DARK : 0xFF233447);
        graphics.drawCenteredString(font, toolsPanel.isOpen() ? "HIDE TOOLS" : "TOOLS  F6",
                width - 47, 20, TEXT);

        if (browser != null && browser.isTextureReady()) {
            ResourceLocation texture = browser.getTextureIdentifier();
            if (texture != null) {
                graphics.blit(
                        texture,
                        browserX,
                        browserY,
                        0.0F,
                        0.0F,
                        browserWidth,
                        browserHeight,
                        browser.getRenderer().getTextureWidth(),
                        browser.getRenderer().getTextureHeight()
                );
            }
        } else {
            renderPlayerStatus(graphics);
        }

        graphics.fill(12, height - 35, width - 12, height - 34, SCREEN_EDGE);
        graphics.drawString(font, clip(status, width - 125), 21, height - 25,
                status.contains("ERROR") || status.contains("FAILED") || status.contains("missing") ? RED : MUTED, false);
        graphics.drawString(font, "ESC  HIDE", width - 88, height - 25, VIOLET, false);

        toolsPanel.render(graphics, font, width, height, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPlayerStatus(GuiGraphics graphics) {
        String title = hostReady ? "PREPARING THE GAME VIEW" : "PLAYER CORE NOT READY";
        graphics.drawCenteredString(font, title, width / 2, browserY + browserHeight / 2 - 20,
                hostReady ? TEAL : RED);
        graphics.drawCenteredString(font, clip(status, browserWidth - 40),
                width / 2, browserY + browserHeight / 2, TEXT);
        graphics.drawCenteredString(font, "THE GAME WILL APPEAR HERE  /  NO DESKTOP WINDOW REQUIRED",
                width / 2, browserY + browserHeight / 2 + 22, MUTED);
    }

    private String clip(String value, int maxWidth) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String clipped = value;
        while (clipped.length() > 1 && font.width(clipped) > maxWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }

        return clipped.equals(value) ? value : clipped + "...";
    }

    private boolean isInsideBrowser(double mouseX, double mouseY) {
        return mouseX >= browserX && mouseX <= browserX + browserWidth
                && mouseY >= browserY && mouseY <= browserY + browserHeight;
    }

    private int browserMouseX(double mouseX) {
        return (int) ((mouseX - browserX) * minecraft.getWindow().getGuiScale());
    }

    private int browserMouseY(double mouseY) {
        return (int) ((mouseY - browserY) * minecraft.getWindow().getGuiScale());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= 12 && mouseY <= 34 && mouseX >= width - 83 && mouseX <= width - 12) {
            toolsPanel.toggle(width, height);
            return true;
        }

        if (toolsPanel.mouseClicked(mouseX, mouseY, button, width, height)) {
            return true;
        }

        if (browser == null || !browser.isTextureReady() || !isInsideBrowser(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        browser.sendMousePress(browserMouseX(mouseX), browserMouseY(mouseY), button);
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (toolsPanel.isPointerOver(mouseX, mouseY, width, height)
                || mouseY >= 12 && mouseY <= 34 && mouseX >= width - 83 && mouseX <= width - 12) {
            return true;
        }

        if (browser != null) {
            browser.sendMouseRelease(browserMouseX(mouseX), browserMouseY(mouseY), button);
            browser.setFocus(true);
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (toolsPanel.isPointerOver(mouseX, mouseY, width, height)
                || mouseY >= 12 && mouseY <= 34 && mouseX >= width - 83 && mouseX <= width - 12) {
            super.mouseMoved(mouseX, mouseY);
            return;
        }

        if (browser != null && isInsideBrowser(mouseX, mouseY)) {
            browser.sendMouseMove(browserMouseX(mouseX), browserMouseY(mouseY));
        }

        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (toolsPanel.isPointerOver(mouseX, mouseY, width, height)) {
            return true;
        }

        if (browser != null) {
            browser.sendMouseMove(browserMouseX(mouseX), browserMouseY(mouseY));
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (toolsPanel.mouseScrolled(mouseX, mouseY, delta, width, height)) {
            return true;
        }

        if (browser != null && isInsideBrowser(mouseX, mouseY)) {
            browser.sendMouseWheel(browserMouseX(mouseX), browserMouseY(mouseY), delta, 0);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_F6) {
            toolsPanel.toggle(width, height);
            return true;
        }

        if (toolsPanel.isOpen()) {
            return true;
        }

        if (browser != null) {
            browser.sendKeyPress(keyCode, scanCode, modifiers);
            browser.setFocus(true);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (toolsPanel.isOpen()) {
            return true;
        }

        if (browser != null) {
            browser.sendKeyRelease(keyCode, scanCode, modifiers);
            browser.setFocus(true);
            return true;
        }

        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (toolsPanel.isOpen()) {
            return true;
        }

        if (browser != null && codePoint != 0) {
            browser.sendKeyTyped(codePoint, modifiers);
            browser.setFocus(true);
            return true;
        }

        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        closing = true;
        SkuaPlayerBrowserSession.releaseFocus();
        super.onClose();
    }
}
