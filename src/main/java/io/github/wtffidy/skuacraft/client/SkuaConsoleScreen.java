package io.github.wtffidy.skuacraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class SkuaConsoleScreen extends Screen {
    private enum Page {
        DASHBOARD,
        SCRIPTS,
        CONTROLS,
        INVENTORY,
        FLEET
    }

    private static final int INK = 0xFF090E16;
    private static final int SHELL = 0xFF151D2B;
    private static final int PANEL = 0xFF1C2939;
    private static final int PANEL_DARK = 0xFF14202D;
    private static final int PANEL_ALT = 0xFF233447;
    private static final int TEAL = 0xFF57E6C1;
    private static final int TEAL_DARK = 0xFF1B766F;
    private static final int VIOLET = 0xFFB18AF5;
    private static final int AMBER = 0xFFFFC36A;
    private static final int TEXT = 0xFFE8F1F4;
    private static final int MUTED = 0xFF91A5B4;
    private static final int RED = 0xFFFF777D;

    private final List<JsonObject> scripts = new ArrayList<>();
    private JsonObject status = new JsonObject();
    private JsonObject inventory = new JsonObject();
    private JsonObject bank = new JsonObject();
    private JsonObject fleet = new JsonObject();

    private Page page = Page.DASHBOARD;
    private String selectedScriptPath = "";
    private String notice = "Initialising VibeSkua link...";
    private String connection = "CHECKING";

    private boolean statusPending;
    private boolean scriptsPending;
    private int scriptOffset;
    private int ticks;

    private int frameX;
    private int frameY;
    private int frameWidth;
    private int frameHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;

    public SkuaConsoleScreen() {
        super(Component.literal("Skua Console"));
    }

    @Override
    protected void init() {
        super.init();

        frameWidth = Math.min(720, Math.max(310, width - 24));
        frameHeight = Math.min(460, Math.max(260, height - 24));
        frameX = (width - frameWidth) / 2;
        frameY = (height - frameHeight) / 2;
        contentX = frameX + 18;
        contentY = frameY + 88;
        contentWidth = frameWidth - 36;
        contentHeight = frameHeight - 132;

        refreshStatus();

        if (page == Page.SCRIPTS) {
            refreshScripts();
        }
    }

    @Override
    public void tick() {
        super.tick();
        chatInput.tick();
        ticks++;

        if (ticks % 40 == 0 && !statusPending) {
            refreshStatus();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void refreshStatus() {
        if (statusPending) {
            return;
        }

        statusPending = true;
        SkuaApiClient.get("/api/status", result -> {
            statusPending = false;

            if (result != null && result.isJsonObject()) {
                JsonObject response = result.getAsJsonObject();

                if (response.has("success") && !readBoolean(response, "success", false)) {
                    connection = "OFFLINE";
                    notice = readString(response, "message", "VibeSkua connection failed.");
                    return;
                }

                status = response;
                connection = "ONLINE";
                notice = "Live link  /  " + readString(status, "app", "VibeSkua")
                        + " " + readString(status, "version", "");
                return;
            }

            connection = "OFFLINE";
            notice = "VibeSkua returned an empty status response.";
        });
    }

    private void refreshScripts() {
        if (scriptsPending) {
            return;
        }

        scriptsPending = true;
        SkuaApiClient.get("/api/scripts", result -> {
            scriptsPending = false;
            scripts.clear();
            scriptOffset = 0;

            if (result != null && result.isJsonArray()) {
                appendScripts(result.getAsJsonArray());
            } else if (result != null && result.isJsonObject()) {
                JsonElement list = result.getAsJsonObject().get("scripts");

                if (list != null && list.isJsonArray()) {
                    appendScripts(list.getAsJsonArray());
                }
            }

            notice = scripts.isEmpty()
                    ? "No scripts returned. Check the desktop Skua script catalogue."
                    : "Loaded " + scripts.size() + " scripts from VibeSkua.";
        });
    }

    private void appendScripts(JsonArray array) {
        for (JsonElement element : array) {
            if (element != null && element.isJsonObject()) {
                scripts.add(element.getAsJsonObject());
            }
        }
    }

    private void post(String path, JsonObject body) {
        notice = "Sending command...";

        SkuaApiClient.post(path, body, result -> {
            boolean success = result != null
                    && result.isJsonObject()
                    && (!result.getAsJsonObject().has("success")
                    || readBoolean(result.getAsJsonObject(), "success", false));

            if (result != null && result.isJsonObject()) {
                notice = readString(result.getAsJsonObject(), "message",
                        success ? "Command accepted." : "Command failed.");
            } else {
                notice = success ? "Command accepted." : "Command failed.";
            }

            if (success) {
                refreshStatus();
            }
        });
    }

    private JsonObject emptyBody() {
        return new JsonObject();
    }

    private void selectPage(Page nextPage) {
        if (page == nextPage) {
            return;
        }

        page = nextPage;

        switch (page) {
            case SCRIPTS -> refreshScripts();
            case INVENTORY -> refreshInventory();
            case FLEET -> refreshFleet();
            default -> refreshStatus();
        }
    }

    private void refreshInventory() {
        SkuaApiClient.get("/api/inventory", result -> {
            inventory = result != null && result.isJsonObject()
                    ? result.getAsJsonObject()
                    : new JsonObject();
            notice = "Inventory snapshot refreshed.";
        });
    }

    private void refreshBank() {
        SkuaApiClient.get("/api/bank", result -> {
            bank = result != null && result.isJsonObject()
                    ? result.getAsJsonObject()
                    : new JsonObject();
            notice = "Bank snapshot refreshed.";
        });
    }

    private void refreshFleet() {
        SkuaApiClient.get("/api/army/queue", result -> {
            fleet = result != null && result.isJsonObject()
                    ? result.getAsJsonObject()
                    : new JsonObject();
            notice = "Army Queue snapshot refreshed.";
        });
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + h, color);
        graphics.fill(x, y, x + w, y + 1, 0xFF35475B);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h,
                            String label, int accent, boolean active) {
        graphics.fill(x, y, x + w, y + h, active ? TEAL_DARK : PANEL_ALT);
        graphics.fill(x, y, x + 3, y + h, accent);
        graphics.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, TEXT);
    }

    private void drawLabel(GuiGraphics graphics, String label, int x, int y) {
        graphics.drawString(font, label, x, y, MUTED, false);
    }

    private void drawValue(GuiGraphics graphics, String value, int x, int y) {
        graphics.drawString(font, value, x, y, TEXT, false);
    }

    private String readString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }

        try {
            return object.get(key).getAsString();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private int readNumber(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return 0;
        }

        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private boolean readBoolean(JsonObject object, String key, boolean fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }

        try {
            return object.get(key).getAsBoolean();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private String clipped(String value, int maxWidth) {
        if (value == null || value.isBlank()) {
            return "";
        }

        if (font.width(value) <= maxWidth) {
            return value;
        }

        String result = value;

        while (result.length() > 1 && font.width(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }

        return result + "...";
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0x88040A11);

        graphics.fill(frameX - 5, frameY - 5, frameX + frameWidth + 5,
                frameY + frameHeight + 5, 0xB8000000);
        graphics.fill(frameX, frameY, frameX + frameWidth, frameY + frameHeight, SHELL);
        graphics.fill(frameX + 4, frameY + 4, frameX + frameWidth - 4,
                frameY + frameHeight - 4, INK);
        graphics.fill(frameX + 11, frameY + 11, frameX + 14,
                frameY + frameHeight - 11, TEAL_DARK);

        graphics.drawString(font, "SKUA SYSTEMS  /  FIELD CONSOLE MK-I",
                frameX + 23, frameY + 14, TEAL, false);
        graphics.drawString(font, "VIBE LINK  " + connection,
                frameX + frameWidth - 125, frameY + 14,
                "ONLINE".equals(connection) ? TEAL : RED, false);
        graphics.drawString(font, "POCKET COMMAND DECK     •     PROJECT INFINITY EDITION",
                frameX + 23, frameY + 29, MUTED, false);

        renderNavigation(graphics, mouseX, mouseY);
        drawPanel(graphics, contentX, contentY, contentWidth, contentHeight, PANEL);

        switch (page) {
            case DASHBOARD -> renderDashboard(graphics);
            case SCRIPTS -> renderScripts(graphics);
            case CONTROLS -> renderControls(graphics);
            case INVENTORY -> renderInventory(graphics);
            case FLEET -> renderFleet(graphics);
        }

        graphics.fill(frameX + 18, frameY + frameHeight - 29,
                frameX + frameWidth - 18, frameY + frameHeight - 28, 0xFF2B3949);
        int noticeColor = notice.toLowerCase().contains("failed")
                || notice.toLowerCase().contains("cannot")
                || notice.toLowerCase().contains("offline") ? RED : MUTED;
        graphics.drawString(font, clipped(notice, frameWidth - 125),
                frameX + 22, frameY + frameHeight - 19, noticeColor, false);
        graphics.drawString(font, "ESC  CLOSE",
                frameX + frameWidth - 82, frameY + frameHeight - 19, VIOLET, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderNavigation(GuiGraphics graphics, int mouseX, int mouseY) {
        String[] labels = {"DASHBOARD", "SCRIPTS", "CONTROLS", "INVENTORY", "FLEET"};
        Page[] pages = Page.values();
        int gap = 4;
        int navY = frameY + 49;
        int navWidth = (frameWidth - 36 - gap * 4) / 5;

        for (int i = 0; i < labels.length; i++) {
            int x = frameX + 18 + i * (navWidth + gap);
            boolean selected = page == pages[i];
            boolean hovered = inside(mouseX, mouseY, x, navY, navWidth, 25);
            int color = selected ? TEAL_DARK : hovered ? PANEL_ALT : PANEL_DARK;

            graphics.fill(x, navY, x + navWidth, navY + 25, color);
            graphics.fill(x, navY, x + navWidth, navY + 2, selected ? TEAL : 0xFF35475B);
            graphics.drawCenteredString(font, labels[i], x + navWidth / 2, navY + 9,
                    selected ? TEXT : MUTED);
        }
    }

    private void renderDashboard(GuiGraphics graphics) {
        int left = contentX + 15;
        int top = contentY + 14;
        int gap = 8;
        int cardWidth = (contentWidth - 30 - gap * 2) / 3;

        graphics.drawString(font, "PLAYER SNAPSHOT", left, top, TEAL, false);
        graphics.drawString(font,
                readString(status, "username", "Waiting for Skua...") + "  •  Lv " + readNumber(status, "level"),
                left, top + 17, TEXT, false);

        String[][] cards = {
                {"HEALTH", readNumber(status, "hp") + " / " + readNumber(status, "max_hp")},
                {"MANA", readNumber(status, "mp") + " / " + readNumber(status, "max_mp")},
                {"GOLD", Integer.toString(readNumber(status, "gold"))}
        };

        int cardY = top + 42;

        for (int i = 0; i < cards.length; i++) {
            int x = left + i * (cardWidth + gap);
            drawPanel(graphics, x, cardY, cardWidth, 43, PANEL_DARK);
            drawLabel(graphics, cards[i][0], x + 9, cardY + 7);
            drawValue(graphics, cards[i][1], x + 9, cardY + 23);
        }

        int detailY = cardY + 59;
        int right = contentX + contentWidth / 2 + 6;

        drawLabel(graphics, "CLASS", left, detailY);
        drawValue(graphics, clipped(readString(status, "current_class", "Unknown"), cardWidth + 35),
                left, detailY + 14);
        drawLabel(graphics, "LOCATION", left, detailY + 38);
        drawValue(graphics, clipped(readString(status, "map", "Unknown") + " / "
                        + readString(status, "server", "Unknown"), cardWidth + 35),
                left, detailY + 52);

        drawLabel(graphics, "ACTIVE SCRIPT", right, detailY);
        drawValue(graphics, clipped(readString(status, "loaded_script", "None"), cardWidth + 35),
                right, detailY + 14);
        drawLabel(graphics, "SCRIPT STATE", right, detailY + 38);
        drawValue(graphics, readString(status, "script_status", "STOPPED")
                        + (readBoolean(status, "script_paused", false) ? " / PAUSED" : ""),
                right, detailY + 52);
        drawLabel(graphics, "TARGET", right, detailY + 76);
        drawValue(graphics, readBoolean(status, "has_target", false)
                        ? clipped(readString(status, "target_name", "Unknown"), cardWidth + 35)
                        : "No target",
                right, detailY + 90);

        int buttonY = contentY + contentHeight - 43;
        int buttonWidth = (contentWidth - 42) / 3;
        drawButton(graphics, left, buttonY, buttonWidth, 27, "START / RESUME", TEAL, false);
        drawButton(graphics, left + buttonWidth + 6, buttonY, buttonWidth, 27, "PAUSE", AMBER, false);
        drawButton(graphics, left + (buttonWidth + 6) * 2, buttonY, buttonWidth, 27, "STOP", RED, false);
    }

    private void renderScripts(GuiGraphics graphics) {
        int left = contentX + 15;
        int top = contentY + 13;

        graphics.drawString(font, "SCRIPT LIBRARY", left, top, TEAL, false);
        graphics.drawString(font, scripts.size() + " entries from the active Skua catalogue",
                left, top + 15, MUTED, false);

        int rowTop = top + 34;
        int rowHeight = 25;
        int visibleRows = Math.max(1, (contentHeight - 115) / 28);
        int end = Math.min(scripts.size(), scriptOffset + visibleRows);

        for (int i = scriptOffset; i < end; i++) {
            JsonObject script = scripts.get(i);
            int y = rowTop + (i - scriptOffset) * 28;
            String path = readString(script, "path", "");
            boolean selected = path.equals(selectedScriptPath);
            graphics.fill(left, y, contentX + contentWidth - 15, y + rowHeight,
                    selected ? TEAL_DARK : PANEL_DARK);

            String name = readString(script, "name", readString(script, "fileName", "Unnamed script"));
            String category = readString(script, "category", "Other");
            String local = readBoolean(script, "downloaded", false) ? "LOCAL" : "REMOTE";

            graphics.drawString(font, clipped(name, contentWidth - 170),
                    left + 8, y + 8, TEXT, false);
            graphics.drawString(font, clipped(category + "  •  " + local, 112),
                    contentX + contentWidth - 126, y + 8, selected ? TEAL : MUTED, false);
        }

        if (scripts.isEmpty()) {
            drawValue(graphics, scriptsPending ? "Syncing the Skua script catalogue..."
                    : "No script entries. Check that VibeSkua has loaded its scripts.",
                    left, rowTop + 12);
        }

        int footerY = contentY + contentHeight - 43;
        int buttonWidth = (contentWidth - 42) / 3;
        drawButton(graphics, left, footerY, buttonWidth, 27, "REFRESH", VIOLET, false);
        drawButton(graphics, left + buttonWidth + 6, footerY, buttonWidth, 27, "LOAD SELECTED", TEAL, false);
        drawButton(graphics, left + (buttonWidth + 6) * 2, footerY,
                buttonWidth, 27, "START SELECTED", AMBER, false);

        graphics.drawString(font, "Mouse wheel: browse  •  Select a row before loading",
                left, contentY + contentHeight - 58, MUTED, false);
    }

    private void renderControls(GuiGraphics graphics) {
        int left = contentX + 15;
        int top = contentY + 15;

        graphics.drawString(font, "LIVE CHARACTER CONTROLS", left, top, TEAL, false);
        graphics.drawString(font, "Commands route to the active VibeSkua tab.",
                left, top + 17, MUTED, false);

        int buttonWidth = (contentWidth - 42) / 3;
        int rowY = top + 49;
        drawButton(graphics, left, rowY, buttonWidth, 34, "ATTACK TARGET", RED, false);
        drawButton(graphics, left + buttonWidth + 6, rowY, buttonWidth, 34, "REST", VIOLET, false);
        drawButton(graphics, left + (buttonWidth + 6) * 2, rowY, buttonWidth, 34, "JUMP SPAWN", TEAL, false);

        int skillWidth = (contentWidth - 30 - 24) / 5;
        int skillY = rowY + 52;

        for (int i = 0; i < 5; i++) {
            int x = left + i * (skillWidth + 6);
            drawButton(graphics, x, skillY, skillWidth, 37,
                    "SKILL " + (i + 1), i == 4 ? VIOLET : TEAL, false);
        }

        int detailY = skillY + 64;
        drawLabel(graphics, "TARGET", left, detailY);
        drawValue(graphics, readBoolean(status, "has_target", false)
                        ? clipped(readString(status, "target_name", "Unknown"), contentWidth / 2 - 25)
                        : "No target",
                left, detailY + 15);
        drawLabel(graphics, "COMBAT", contentX + contentWidth / 2, detailY);
        drawValue(graphics, readBoolean(status, "in_combat", false) ? "IN COMBAT" : "IDLE",
                contentX + contentWidth / 2, detailY + 15);
    }

    private void renderInventory(GuiGraphics graphics) {
        int left = contentX + 15;
        int top = contentY + 15;

        graphics.drawString(font, "INVENTORY & BANK", left, top, TEAL, false);
        int right = contentX + contentWidth / 2 + 6;

        drawLabel(graphics, "BAG SLOTS", left, top + 47);
        drawValue(graphics, readNumber(status, "inventory_used") + " / "
                + readNumber(status, "inventory_max"), left, top + 64);
        drawLabel(graphics, "BANK SLOTS", right, top + 47);
        drawValue(graphics, readNumber(status, "bank_used") + " / "
                + readNumber(status, "bank_max"), right, top + 64);

        drawLabel(graphics, "BANK CACHE", left, top + 96);
        drawValue(graphics, readBoolean(status, "bank_loaded", false) ? "LOADED" : "NOT LOADED",
                left, top + 113);

        int bagCount = countArray(inventory, "items", "inventory");
        int bankCount = countArray(bank, "items", "bank");
        drawLabel(graphics, "LAST BAG SNAPSHOT", left, top + 144);
        drawValue(graphics, bagCount > 0 ? bagCount + " item entries" : "Not loaded yet",
                left, top + 159);
        drawLabel(graphics, "LAST BANK SNAPSHOT", right, top + 144);
        drawValue(graphics, bankCount > 0 ? bankCount + " item entries" : "Not loaded yet",
                right, top + 159);

        int y = contentY + contentHeight - 43;
        int buttonWidth = (contentWidth - 42) / 3;
        drawButton(graphics, left, y, buttonWidth, 27, "REFRESH BAG", TEAL, false);
        drawButton(graphics, left + buttonWidth + 6, y, buttonWidth, 27, "OPEN BANK", VIOLET, false);
        drawButton(graphics, left + (buttonWidth + 6) * 2, y, buttonWidth, 27, "LOAD BANK", AMBER, false);
    }

    private void renderFleet(GuiGraphics graphics) {
        int left = contentX + 15;
        int top = contentY + 15;

        graphics.drawString(font, "FLEET COMMAND", left, top, TEAL, false);
        graphics.drawString(font, "Multi-tab actions are handled by the VibeSkua manager gateway.",
                left, top + 17, MUTED, false);

        drawLabel(graphics, "ACTIVE ACCOUNT", left, top + 56);
        drawValue(graphics, clipped(readString(status, "username", "Unknown"), contentWidth / 2 - 25),
                left, top + 72);
        drawLabel(graphics, "SCRIPT STATE", contentX + contentWidth / 2, top + 56);
        drawValue(graphics, readString(status, "script_status", "STOPPED"),
                contentX + contentWidth / 2, top + 72);

        drawLabel(graphics, "QUEUE STATUS", left, top + 111);
        drawValue(graphics, clipped(readString(fleet, "status",
                        readString(fleet, "message", "Refresh to query Army Queue")), contentWidth - 45),
                left, top + 127);

        int y = contentY + contentHeight - 43;
        int buttonWidth = (contentWidth - 42) / 3;
        drawButton(graphics, left, y, buttonWidth, 27, "REFRESH QUEUE", TEAL, false);
        drawButton(graphics, left + buttonWidth + 6, y, buttonWidth, 27, "START QUEUE", VIOLET, false);
        drawButton(graphics, left + (buttonWidth + 6) * 2, y, buttonWidth, 27, "STOP QUEUE", RED, false);
    }

    private int countArray(JsonObject object, String... keys) {
        if (object == null) {
            return 0;
        }

        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && element.isJsonArray()) {
                return element.getAsJsonArray().size();
            }
        }

        return 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int gap = 4;
        int navY = frameY + 49;
        int navWidth = (frameWidth - 36 - gap * 4) / 5;
        Page[] pages = Page.values();

        for (int i = 0; i < pages.length; i++) {
            int x = frameX + 18 + i * (navWidth + gap);

            if (inside(mouseX, mouseY, x, navY, navWidth, 25)) {
                selectPage(pages[i]);
                return true;
            }
        }

        if (!inside(mouseX, mouseY, contentX, contentY, contentWidth, contentHeight)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        switch (page) {
            case DASHBOARD -> clickDashboard(mouseX, mouseY);
            case SCRIPTS -> clickScripts(mouseX, mouseY);
            case CONTROLS -> clickControls(mouseX, mouseY);
            case INVENTORY -> clickInventory(mouseX, mouseY);
            case FLEET -> clickFleet(mouseX, mouseY);
        }

        return true;
    }

    private void clickDashboard(double mouseX, double mouseY) {
        int left = contentX + 15;
        int y = contentY + contentHeight - 43;
        int buttonWidth = (contentWidth - 42) / 3;
        JsonObject request = emptyBody();

        if (inside(mouseX, mouseY, left, y, buttonWidth, 27)) {
            if (!selectedScriptPath.isBlank()) {
                request.addProperty("path", selectedScriptPath);
            }
            post("/api/scripts/start", request);
        } else if (inside(mouseX, mouseY, left + buttonWidth + 6, y, buttonWidth, 27)) {
            post("/api/scripts/pause", request);
        } else if (inside(mouseX, mouseY, left + (buttonWidth + 6) * 2, y, buttonWidth, 27)) {
            post("/api/scripts/stop", request);
        }
    }

    private void clickScripts(double mouseX, double mouseY) {
        int left = contentX + 15;
        int rowTop = contentY + 15 + 34;
        int rowHeight = 25;
        int visibleRows = Math.max(1, (contentHeight - 115) / 28);
        int end = Math.min(scripts.size(), scriptOffset + visibleRows);

        for (int i = scriptOffset; i < end; i++) {
            int y = rowTop + (i - scriptOffset) * 28;

            if (inside(mouseX, mouseY, left, y, contentWidth - 30, rowHeight)) {
                JsonObject selected = scripts.get(i);
                selectedScriptPath = readString(selected, "path", "");
                notice = "Selected: " + readString(selected, "name", selectedScriptPath);
                return;
            }
        }

        int y = contentY + contentHeight - 43;
        int buttonWidth = (contentWidth - 42) / 3;

        if (inside(mouseX, mouseY, left, y, buttonWidth, 27)) {
            refreshScripts();
        } else if (inside(mouseX, mouseY, left + buttonWidth + 6, y, buttonWidth, 27)) {
            loadSelectedScript(false);
        } else if (inside(mouseX, mouseY, left + (buttonWidth + 6) * 2, y, buttonWidth, 27)) {
            loadSelectedScript(true);
        }
    }

    private void loadSelectedScript(boolean start) {
        if (selectedScriptPath.isBlank()) {
            notice = "Select a script first.";
            return;
        }

        JsonObject request = emptyBody();
        request.addProperty("path", selectedScriptPath);
        post(start ? "/api/scripts/start" : "/api/scripts/load", request);
    }

    private void clickControls(double mouseX, double mouseY) {
        int left = contentX + 15;
        int buttonWidth = (contentWidth - 42) / 3;
        int rowY = contentY + 15 + 49;

        if (inside(mouseX, mouseY, left, rowY, buttonWidth, 34)) {
            post("/api/combat/attack", emptyBody());
            return;
        }

        if (inside(mouseX, mouseY, left + buttonWidth + 6, rowY, buttonWidth, 34)) {
            post("/api/player/rest", emptyBody());
            return;
        }

        if (inside(mouseX, mouseY, left + (buttonWidth + 6) * 2, rowY, buttonWidth, 34)) {
            JsonObject request = emptyBody();
            request.addProperty("cell", "Enter");
            request.addProperty("pad", "Spawn");
            post("/api/player/jump", request);
            return;
        }

        int skillWidth = (contentWidth - 30 - 24) / 5;
        int skillY = rowY + 52;

        for (int i = 0; i < 5; i++) {
            int x = left + i * (skillWidth + 6);

            if (inside(mouseX, mouseY, x, skillY, skillWidth, 37)) {
                post("/api/combat/skill/" + (i + 1), emptyBody());
                return;
            }
        }
    }

    private void clickInventory(double mouseX, double mouseY) {
        int left = contentX + 15;
        int y = contentY + contentHeight - 43;
        int buttonWidth = (contentWidth - 42) / 3;

        if (inside(mouseX, mouseY, left, y, buttonWidth, 27)) {
            refreshInventory();
        } else if (inside(mouseX, mouseY, left + buttonWidth + 6, y, buttonWidth, 27)) {
            post("/api/bank/open", emptyBody());
        } else if (inside(mouseX, mouseY, left + (buttonWidth + 6) * 2, y, buttonWidth, 27)) {
            post("/api/bank/load", emptyBody());
            refreshBank();
        }
    }

    private void clickFleet(double mouseX, double mouseY) {
        int left = contentX + 15;
        int y = contentY + contentHeight - 43;
        int buttonWidth = (contentWidth - 42) / 3;

        if (inside(mouseX, mouseY, left, y, buttonWidth, 27)) {
            refreshFleet();
            SkuaApiClient.get("/api/tabs", result -> notice = "Tab list response received.");
        } else if (inside(mouseX, mouseY, left + buttonWidth + 6, y, buttonWidth, 27)) {
            post("/api/army/queue/start", emptyBody());
        } else if (inside(mouseX, mouseY, left + (buttonWidth + 6) * 2, y, buttonWidth, 27)) {
            post("/api/army/queue/stop", emptyBody());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (page != Page.SCRIPTS) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        int visibleRows = Math.max(1, (contentHeight - 115) / 28);
        int maxOffset = Math.max(0, scripts.size() - visibleRows);
        scriptOffset = Math.max(0, Math.min(maxOffset, scriptOffset - (int) Math.signum(delta) * 2));
        return true;
    }
}
