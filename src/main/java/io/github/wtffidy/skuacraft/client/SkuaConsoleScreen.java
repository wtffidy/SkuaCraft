package io.github.wtffidy.skuacraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class SkuaConsoleScreen extends Screen {
    private enum Page {
        DASHBOARD, SCRIPTS, CONTROLS, INVENTORY, QUESTS,
        DROPS, CHAT, LOGS, OPTIONS, FLEET
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

    private static final String[][] NAVIGATION = {
            {"DASHBOARD", "SCRIPTS", "CONTROLS", "INVENTORY", "QUESTS"},
            {"DROPS", "CHAT", "LOGS", "OPTIONS", "FLEET"}
    };

    private static final String[][] OPTION_FIELDS = {
            {"LagKiller", "lag_killer", "LAG KILLER"},
            {"AutoRelogin", "auto_relogin", "AUTO RELOGIN"},
            {"InfiniteRange", "infinite_range", "INFINITE RANGE"},
            {"SkipCutscenes", "skip_cutscenes", "SKIP CUTSCENES"},
            {"HeadlessMode", "headless_mode", "HEADLESS MODE"},
            {"StreamerMode", "streamer_mode", "STREAMER MODE"},
            {"DisableFX", "disable_fx", "DISABLE FX"},
            {"AttackWithoutTarget", "attack_without_target", "ATTACK WITHOUT TARGET"},
            {"DisableCollisions", "disable_collisions", "DISABLE COLLISIONS"},
            {"HidePlayers", "hide_players", "HIDE PLAYERS"},
            {"Magnetise", "magnetise", "MAGNETISE"},
            {"RestPackets", "rest_packets", "REST PACKETS"}
    };

    private final List<JsonObject> scripts = new ArrayList<>();
    private final List<JsonObject> inventoryItems = new ArrayList<>();
    private final List<JsonObject> bankItems = new ArrayList<>();
    private final List<JsonObject> quests = new ArrayList<>();
    private final List<JsonObject> tabs = new ArrayList<>();
    private final List<String> drops = new ArrayList<>();
    private final List<String> logs = new ArrayList<>();
    private final List<JsonObject> chatMessages = new ArrayList<>();

    private JsonObject status = new JsonObject();
    private JsonObject options = new JsonObject();
    private JsonObject queue = new JsonObject();

    private Page page = Page.DASHBOARD;
    private EditBox chatInput;

    private String selectedScriptPath = "";
    private int selectedItemId;
    private String selectedItemName = "";
    private int selectedQuestId;
    private int selectedPid;
    private String selectedDrop = "";
    private boolean showBank;
    private boolean confirmFleetStart;
    private boolean confirmFleetStop;
    private String notice = "Initialising VibeSkua link...";
    private String connection = "CHECKING";

    private boolean statusPending;
    private boolean scriptsPending;
    private int listOffset;
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

        frameWidth = Math.min(760, Math.max(320, width - 24));
        frameHeight = Math.min(490, Math.max(280, height - 24));
        frameX = (width - frameWidth) / 2;
        frameY = (height - frameHeight) / 2;
        contentX = frameX + 18;
        contentY = frameY + 112;
        contentWidth = frameWidth - 36;
        contentHeight = frameHeight - 155;

        chatInput = new EditBox(font, contentX + 15, contentY + contentHeight - 42,
                contentWidth - 112, 27, Component.literal("Chat message"));
        chatInput.setMaxLength(250);
        chatInput.setVisible(page == Page.CHAT);
        addRenderableWidget(chatInput);

        refreshStatus();
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
                if (response.has("options") && response.get("options").isJsonObject()) {
                    options = response.getAsJsonObject("options");
                }
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
            listOffset = 0;

            if (result != null && result.isJsonArray()) {
                appendObjects(result.getAsJsonArray(), scripts);
            } else if (result != null && result.isJsonObject()) {
                JsonElement list = result.getAsJsonObject().get("scripts");
                if (list != null && list.isJsonArray()) {
                    appendObjects(list.getAsJsonArray(), scripts);
                }
            }

            notice = scripts.isEmpty()
                    ? "No scripts returned. Check the desktop Skua script catalogue."
                    : "Loaded " + scripts.size() + " scripts from VibeSkua.";
        });
    }

    private void refreshInventory() {
        SkuaApiClient.get("/api/inventory", result -> {
            inventoryItems.clear();
            readObjectArray(result, "items", inventoryItems);
            selectedItemId = 0;
            selectedItemName = "";
            showBank = false;
            listOffset = 0;
            notice = "Inventory refreshed  /  " + inventoryItems.size() + " entries.";
        });
    }

    private void refreshBank() {
        SkuaApiClient.get("/api/bank", result -> {
            bankItems.clear();
            readObjectArray(result, "items", bankItems);
            selectedItemId = 0;
            selectedItemName = "";
            showBank = true;
            listOffset = 0;
            notice = "Bank refreshed  /  " + bankItems.size() + " entries.";
        });
    }

    private void refreshQuests() {
        SkuaApiClient.get("/api/quests", result -> {
            quests.clear();
            readObjectArray(result, "quests", quests);
            listOffset = 0;
            notice = "Quest list refreshed  /  " + quests.size() + " entries.";
        });
    }

    private void refreshDrops() {
        SkuaApiClient.get("/api/drops", result -> {
            drops.clear();
            JsonElement array = result != null && result.isJsonObject()
                    ? result.getAsJsonObject().get("drops") : result;

            if (array != null && array.isJsonArray()) {
                for (JsonElement element : array.getAsJsonArray()) {
                    if (element != null && !element.isJsonNull()) {
                        String value = element.getAsString();
                        if (!value.isBlank()) {
                            drops.add(value);
                        }
                    }
                }
            }

            listOffset = 0;
            notice = "Drop list refreshed  /  " + drops.size() + " entries.";
        });
    }

    private void refreshChat() {
        SkuaApiClient.get("/api/chat", result -> {
            chatMessages.clear();
            readObjectArray(result, "messages", chatMessages);
            listOffset = 0;
            notice = "Chat feed refreshed.";
        });
    }

    private void refreshLogs() {
        SkuaApiClient.get("/api/logs", result -> {
            logs.clear();
            JsonElement array = result != null && result.isJsonObject()
                    ? result.getAsJsonObject().get("logs") : result;

            if (array != null && array.isJsonArray()) {
                for (JsonElement element : array.getAsJsonArray()) {
                    if (element != null && !element.isJsonNull()) {
                        logs.add(element.getAsString());
                    }
                }
            }

            listOffset = 0;
            notice = "Script log refreshed  /  " + logs.size() + " lines.";
        });
    }

    private void refreshOptions() {
        SkuaApiClient.get("/api/options", result -> {
            if (result != null && result.isJsonObject()) {
                options = result.getAsJsonObject();
                notice = "Client options synchronized.";
            } else {
                notice = "Could not read client options.";
            }
        });
    }

    private void refreshFleet() {
        SkuaApiClient.get("/api/tabs", result -> {
            tabs.clear();
            readObjectArray(result, "tabs", tabs);

            if (result != null && result.isJsonObject()) {
                int active = readNumber(result.getAsJsonObject(), "active_pid");
                if (active > 0) {
                    selectedPid = active;
                }
            }

            notice = "Client tabs refreshed  /  " + tabs.size() + " tabs.";
        });

        SkuaApiClient.get("/api/army/queue", result -> {
            if (result != null && result.isJsonObject()) {
                queue = result.getAsJsonObject();
            }
        });
    }

    private void appendObjects(JsonArray array, List<JsonObject> target) {
        for (JsonElement element : array) {
            if (element != null && element.isJsonObject()) {
                target.add(element.getAsJsonObject());
            }
        }
    }

    private void readObjectArray(JsonElement source, String key, List<JsonObject> target) {
        if (source == null) {
            return;
        }

        JsonElement array = source;
        if (source.isJsonObject()) {
            array = source.getAsJsonObject().get(key);
        }

        if (array != null && array.isJsonArray()) {
            appendObjects(array.getAsJsonArray(), target);
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
                refreshCurrentPage();
            }
        });
    }

    private void refreshCurrentPage() {
        switch (page) {
            case SCRIPTS -> refreshScripts();
            case INVENTORY -> {
                if (showBank) {
                    refreshBank();
                } else {
                    refreshInventory();
                }
            }
            case QUESTS -> refreshQuests();
            case DROPS -> refreshDrops();
            case CHAT -> refreshChat();
            case LOGS -> refreshLogs();
            case OPTIONS -> refreshOptions();
            case FLEET -> refreshFleet();
            default -> refreshStatus();
        }
    }

    private void selectPage(Page nextPage) {
        page = nextPage;
        listOffset = 0;
        chatInput.setVisible(page == Page.CHAT);
        if (page != Page.CHAT) {
            chatInput.setFocused(false);
        }

        switch (page) {
            case SCRIPTS -> refreshScripts();
            case INVENTORY -> refreshInventory();
            case QUESTS -> refreshQuests();
            case DROPS -> refreshDrops();
            case CHAT -> refreshChat();
            case LOGS -> refreshLogs();
            case OPTIONS -> refreshOptions();
            case FLEET -> refreshFleet();
            default -> refreshStatus();
        }
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

    private int visibleRows(int reservedHeight) {
        return Math.max(1, Math.min(8, (contentHeight - reservedHeight) / 27));
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
                frameX + 23, frameY + 13, TEAL, false);
        graphics.drawString(font, "VIBE LINK  " + connection,
                frameX + frameWidth - 125, frameY + 13,
                "ONLINE".equals(connection) ? TEAL : RED, false);
        graphics.drawString(font, "POCKET COMMAND DECK     •     PROJECT INFINITY EDITION",
                frameX + 23, frameY + 28, MUTED, false);

        renderNavigation(graphics, mouseX, mouseY);
        drawPanel(graphics, contentX, contentY, contentWidth, contentHeight, PANEL);

        switch (page) {
            case DASHBOARD -> renderDashboard(graphics);
            case SCRIPTS -> renderScripts(graphics);
            case CONTROLS -> renderControls(graphics);
            case INVENTORY -> renderInventory(graphics);
            case QUESTS -> renderQuests(graphics);
            case DROPS -> renderDrops(graphics);
            case CHAT -> renderChat(graphics);
            case LOGS -> renderLogs(graphics);
            case OPTIONS -> renderOptions(graphics);
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
        int gap = 5;
        int navWidth = (frameWidth - 36 - gap * 4) / 5;
        int navHeight = 22;

        for (int row = 0; row < NAVIGATION.length; row++) {
            int navY = frameY + 47 + row * 25;
            for (int column = 0; column < NAVIGATION[row].length; column++) {
                int x = frameX + 18 + column * (navWidth + gap);
                Page target = Page.values()[row * 5 + column];
                boolean selected = page == target;
                boolean hovered = inside(mouseX, mouseY, x, navY, navWidth, navHeight);
                int color = selected ? TEAL_DARK : hovered ? PANEL_ALT : PANEL_DARK;

                graphics.fill(x, navY, x + navWidth, navY + navHeight, color);
                graphics.fill(x, navY, x + navWidth, navY + 2, selected ? TEAL : 0xFF35475B);
                graphics.drawCenteredString(font, NAVIGATION[row][column],
                        x + navWidth / 2, navY + 7, selected ? TEXT : MUTED);
            }
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

        int buttonY = contentY + contentHeight - 41;
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
        int rowHeight = 24;
        int rows = visibleRows(96);
        int end = Math.min(scripts.size(), listOffset + rows);

        for (int i = listOffset; i < end; i++) {
            JsonObject script = scripts.get(i);
            int y = rowTop + (i - listOffset) * 27;
            String path = readString(script, "path", "");
            boolean selected = path.equals(selectedScriptPath);
            graphics.fill(left, y, contentX + contentWidth - 15, y + rowHeight,
                    selected ? TEAL_DARK : PANEL_DARK);

            String name = readString(script, "name", readString(script, "fileName", "Unnamed script"));
            String category = readString(script, "category", "Other");
            String local = readBoolean(script, "downloaded", false) ? "LOCAL" : "REMOTE";

            graphics.drawString(font, clipped(name, contentWidth - 170), left + 8, y + 8, TEXT, false);
            graphics.drawString(font, clipped(category + "  •  " + local, 112),
                    contentX + contentWidth - 126, y + 8, selected ? TEAL : MUTED, false);
        }

        if (scripts.isEmpty()) {
            drawValue(graphics, scriptsPending ? "Syncing the Skua script catalogue..."
                    : "No script entries. Check that VibeSkua has loaded its scripts.",
                    left, rowTop + 12);
        }

        int footerY = contentY + contentHeight - 41;
        int buttonWidth = (contentWidth - 42) / 3;
        drawButton(graphics, left, footerY, buttonWidth, 27, "REFRESH", VIOLET, false);
        drawButton(graphics, left + buttonWidth + 6, footerY, buttonWidth, 27, "LOAD SELECTED", TEAL, false);
        drawButton(graphics, left + (buttonWidth + 6) * 2, footerY,
                buttonWidth, 27, "START SELECTED", AMBER, false);
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
        int top = contentY + 13;
        List<JsonObject> items = showBank ? bankItems : inventoryItems;

        graphics.drawString(font, showBank ? "BANK STORAGE" : "INVENTORY",
                left, top, TEAL, false);
        graphics.drawString(font, items.size() + " item entries  •  "
                        + (showBank ? "Bank" : "Bag"), left, top + 15, MUTED, false);

        int rowTop = top + 34;
        int rowHeight = 23;
        int rows = visibleRows(96);
        int end = Math.min(items.size(), listOffset + rows);

        for (int i = listOffset; i < end; i++) {
            JsonObject item = items.get(i);
            int y = rowTop + (i - listOffset) * 26;
            int id = readNumber(item, "id");
            boolean selected = selectedItemId == id && readString(item, "name", "").equals(selectedItemName);
            graphics.fill(left, y, contentX + contentWidth - 15, y + rowHeight,
                    selected ? TEAL_DARK : PANEL_DARK);

            String name = readString(item, "name", "Unknown item");
            String quantity = "x" + Math.max(1, readNumber(item, "quantity"));
            String category = readString(item, "category", "Item");
            String state = readBoolean(item, "equipped", false) ? "EQUIPPED" : category;

            graphics.drawString(font, clipped(name, contentWidth - 235), left + 8, y + 7, TEXT, false);
            graphics.drawString(font, quantity + "  •  " + clipped(state, 90),
                    contentX + contentWidth - 143, y + 7, selected ? TEAL : MUTED, false);
        }

        if (items.isEmpty()) {
            drawValue(graphics, showBank ? "No bank entries loaded yet. Refresh the bank."
                    : "No inventory entries returned by VibeSkua.", left, rowTop + 12);
        }

        int footerY = contentY + contentHeight - 41;
        int buttonWidth = (contentWidth - 54) / 4;
        drawButton(graphics, left, footerY, buttonWidth, 27, "BAG", TEAL, !showBank);
        drawButton(graphics, left + buttonWidth + 6, footerY, buttonWidth, 27, "BANK", VIOLET, showBank);
        drawButton(graphics, left + (buttonWidth + 6) * 2, footerY,
                buttonWidth, 27, "EQUIP", AMBER, false);
        drawButton(graphics, left + (buttonWidth + 6) * 3, footerY,
                buttonWidth, 27, showBank ? "WITHDRAW" : "DEPOSIT", RED, false);
    }

    private void renderQuests(GuiGraphics graphics) {
        int left = contentX + 15;
        int top = contentY + 13;
        int listWidth = (contentWidth - 42) / 2;

        graphics.drawString(font, "ACTIVE QUESTS", left, top, TEAL, false);
        graphics.drawString(font, quests.size() + " registered quests",
                left, top + 15, MUTED, false);

        int rowTop = top + 34;
        int rows = visibleRows(82);
        int end = Math.min(quests.size(), listOffset + rows);

        for (int i = listOffset; i < end; i++) {
            JsonObject quest = quests.get(i);
            int y = rowTop + (i - listOffset) * 26;
            int id = readNumber(quest, "id");
            boolean selected = id == selectedQuestId;
            graphics.fill(left, y, left + listWidth, y + 23,
                    selected ? TEAL_DARK : PANEL_DARK);
            graphics.drawString(font, clipped(readString(quest, "name", "Quest #" + id), listWidth - 62),
                    left + 7, y + 7, TEXT, false);
            drawValue(graphics, readBoolean(quest, "can_complete", false) ? "READY" : "ACTIVE",
                    left + listWidth - 50, y + 7);
        }

        JsonObject selected = findById(quests, selectedQuestId);
        int detailX = left + listWidth + 14;
        graphics.drawString(font, "TURN-IN CHECK", detailX, top, VIOLET, false);
        if (selected == null) {
            drawValue(graphics, "Select a quest to inspect requirements.", detailX, top + 21);
        } else {
            drawValue(graphics, clipped(readString(selected, "name", "Selected quest"),
                    contentWidth - listWidth - 50), detailX, top + 20);
            JsonArray requirements = selected.has("requirements") && selected.get("requirements").isJsonArray()
                    ? selected.getAsJsonArray("requirements") : new JsonArray();
            int reqY = top + 47;
            for (int i = 0; i < Math.min(5, requirements.size()); i++) {
                JsonElement element = requirements.get(i);
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject req = element.getAsJsonObject();
                String line = readString(req, "name", "Item") + "  "
                        + readNumber(req, "current") + " / " + readNumber(req, "required");
                drawValue(graphics, clipped(line, contentWidth - listWidth - 50), detailX, reqY + i * 18);
            }
        }

        int footerY = contentY + contentHeight - 41;
        int buttonWidth = (contentWidth - 42) / 3;
        drawButton(graphics, left, footerY, buttonWidth, 27, "REFRESH", VIOLET, false);
        drawButton(graphics, left + buttonWidth + 6, footerY, buttonWidth, 27,
                "COMPLETE SELECTED", TEAL, false);
    }

    private void renderDrops(GuiGraphics graphics) {
        int left = contentX + 15;
        int top = contentY + 13;

        graphics.drawString(font, "DROP COLLECTOR", left, top, TEAL, false);
        graphics.drawString(font, drops.size() + " visible drop entries",
                left, top + 15, MUTED, false);

        int rowTop = top + 34;
        int rows = visibleRows(90);
        int end = Math.min(drops.size(), listOffset + rows);

        for (int i = listOffset; i < end; i++) {
            int y = rowTop + (i - listOffset) * 27;
            String item = drops.get(i);
            boolean selected = selectedDrop.equals(item);
            graphics.fill(left, y, contentX + contentWidth - 15, y + 24,
                    selected ? TEAL_DARK : PANEL_DARK);
            drawValue(graphics, clipped(item, contentWidth - 55), left + 8, y + 8);
        }

        if (drops.isEmpty()) {
            drawValue(graphics, "No pending drops reported by VibeSkua.", left, rowTop + 12);
        }

        int footerY = contentY + contentHeight - 41;
        int buttonWidth = (contentWidth - 42) / 3;
        drawButton(graphics, left, footerY, buttonWidth, 27, "REFRESH", VIOLET, false);
        drawButton(graphics, left + buttonWidth + 6, footerY, buttonWidth, 27, "PICK UP SELECTED", TEAL, false);
        drawButton(graphics, left + (buttonWidth + 6) * 2, footerY, buttonWidth, 27, "PICK UP ALL", AMBER, false);
    }

    private void renderChat(GuiGraphics graphics) {
        int left = contentX + 15;
        int top = contentY + 13;
        graphics.drawString(font, "LIVE CHAT", left, top, TEAL, false);
        graphics.drawString(font, chatMessages.size() + " recent messages",
                left, top + 15, MUTED, false);

        int rowTop = top + 34;
        int rows = visibleRows(92);
        int end = Math.min(chatMessages.size(), listOffset + rows);

        for (int i = listOffset; i < end; i++) {
            JsonObject message = chatMessages.get(i);
            int y = rowTop + (i - listOffset) * 25;
            String sender = readString(message, "sender", "Unknown");
            String type = readString(message, "type", "zone").toUpperCase();
            String content = readString(message, "message", "");
            drawLabel(graphics, clipped(type + "  /  " + sender, 160), left, y + 2);
            drawValue(graphics, clipped(content, contentWidth - 205), left + 170, y + 2);
        }

        int footerY = contentY + contentHeight - 42;
        drawButton(graphics, contentX + contentWidth - 91, footerY,
                76, 27, "SEND", TEAL, false);
    }

    private void renderLogs(GuiGraphics graphics) {
        int left = contentX + 15;
        int top = contentY + 13;

        graphics.drawString(font, "SCRIPT TELEMETRY", left, top, TEAL, false);
        graphics.drawString(font, logs.size() + " recent log lines",
                left, top + 15, MUTED, false);

        int rowTop = top + 36;
        int rows = visibleRows(74);
        int end = Math.min(logs.size(), listOffset + rows);

        for (int i = listOffset; i < end; i++) {
            int y = rowTop + (i - listOffset) * 24;
            graphics.fill(left, y, contentX + contentWidth - 15, y + 21, PANEL_DARK);
            drawValue(graphics, clipped(logs.get(i), contentWidth - 48), left + 7, y + 7);
        }

        if (logs.isEmpty()) {
            drawValue(graphics, "No script logs returned. Start or run a script to populate telemetry.",
                    left, rowTop + 10);
        }

        drawButton(graphics, left, contentY + contentHeight - 41,
                132, 27, "REFRESH LOGS", VIOLET, false);
    }

    private void renderOptions(GuiGraphics graphics) {
        int left = contentX + 15;
        int top = contentY + 14;
        int gap = 8;
        int columns = 3;
        int buttonWidth = (contentWidth - 30 - gap * (columns - 1)) / columns;

        graphics.drawString(font, "CLIENT OPTIONS", left, top, TEAL, false);
        graphics.drawString(font, "Changes are applied directly to the active VibeSkua client.",
                left, top + 16, MUTED, false);

        for (int i = 0; i < OPTION_FIELDS.length; i++) {
            int column = i % columns;
            int row = i / columns;
            int x = left + column * (buttonWidth + gap);
            int y = top + 43 + row * 39;
            String key = OPTION_FIELDS[i][1];
            boolean enabled = readBoolean(options, key, false);
            drawButton(graphics, x, y, buttonWidth, 31,
                    OPTION_FIELDS[i][2] + "  " + (enabled ? "ON" : "OFF"),
                    enabled ? TEAL : MUTED, enabled);
        }
    }

    private void renderFleet(GuiGraphics graphics) {
        int left = contentX + 15;
        int top = contentY + 13;
        int listWidth = (contentWidth - 42) / 2;
        graphics.drawString(font, "CLIENT FLEET", left, top, TEAL, false);
        graphics.drawString(font, tabs.size() + " visible client tabs",
                left, top + 15, MUTED, false);

        int rowTop = top + 34;
        int rows = visibleRows(100);
        int end = Math.min(tabs.size(), listOffset + rows);

        for (int i = listOffset; i < end; i++) {
            JsonObject tab = tabs.get(i);
            int y = rowTop + (i - listOffset) * 25;
            int pid = readNumber(tab, "pid");
            boolean selected = pid == selectedPid;
            graphics.fill(left, y, left + listWidth, y + 22,
                    selected ? TEAL_DARK : PANEL_DARK);
            String name = readString(tab, "username", "Skua tab");
            String active = readBoolean(tab, "is_active", false) ? "ACTIVE" : "READY";
            graphics.drawString(font, clipped(name, listWidth - 80), left + 7, y + 7, TEXT, false);
            drawValue(graphics, active, left + listWidth - 49, y + 7);
        }

        int detailX = left + listWidth + 14;
        graphics.drawString(font, "ARMY QUEUE", detailX, top, VIOLET, false);
        String queueState = readBoolean(queue, "is_running", false)
                ? (readBoolean(queue, "is_paused", false) ? "PAUSED" : "RUNNING")
                : "STOPPED";
        drawValue(graphics, "STATUS  " + queueState, detailX, top + 22);
        drawValue(graphics, clipped(readString(queue, "status", "Manager queue state"),
                contentWidth - listWidth - 55), detailX, top + 42);
        drawValue(graphics, "STEPS  " + arraySize(queue, "steps"), detailX, top + 62);

        int footerY = contentY + contentHeight - 41;
        int buttonWidth = (contentWidth - 54) / 4;
        drawButton(graphics, left, footerY, buttonWidth, 27, "REFRESH", VIOLET, false);
        drawButton(graphics, left + buttonWidth + 6, footerY, buttonWidth, 27, "SELECT TAB", TEAL, false);
        drawButton(graphics, left + (buttonWidth + 6) * 2, footerY,
                buttonWidth, 27, "QUEUE START", AMBER, false);
        drawButton(graphics, left + (buttonWidth + 6) * 3, footerY,
                buttonWidth, 27, "QUEUE STOP", RED, false);
    }

    private int arraySize(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return 0;
        }

        return object.getAsJsonArray(key).size();
    }

    private JsonObject findById(List<JsonObject> source, int id) {
        for (JsonObject item : source) {
            if (readNumber(item, "id") == id) {
                return item;
            }
        }

        return null;
    }

    private void clickNavigation(double mouseX, double mouseY) {
        int gap = 5;
        int navWidth = (frameWidth - 36 - gap * 4) / 5;
        int navHeight = 22;

        for (int row = 0; row < NAVIGATION.length; row++) {
            int navY = frameY + 47 + row * 25;
            for (int column = 0; column < NAVIGATION[row].length; column++) {
                int x = frameX + 18 + column * (navWidth + gap);
                if (inside(mouseX, mouseY, x, navY, navWidth, navHeight)) {
                    selectPage(Page.values()[row * 5 + column]);
                    return;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        clickNavigation(mouseX, mouseY);
        if (page == Page.CHAT && chatInput.mouseClicked(mouseX, mouseY, button)) {
            chatInput.setFocused(true);
            return true;
        }

        if (!inside(mouseX, mouseY, contentX, contentY, contentWidth, contentHeight)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        switch (page) {
            case DASHBOARD -> clickDashboard(mouseX, mouseY);
            case SCRIPTS -> clickScripts(mouseX, mouseY);
            case CONTROLS -> clickControls(mouseX, mouseY);
            case INVENTORY -> clickInventory(mouseX, mouseY);
            case QUESTS -> clickQuests(mouseX, mouseY);
            case DROPS -> clickDrops(mouseX, mouseY);
            case CHAT -> clickChat(mouseX, mouseY);
            case LOGS -> clickLogs(mouseX, mouseY);
            case OPTIONS -> clickOptions(mouseX, mouseY);
            case FLEET -> clickFleet(mouseX, mouseY);
        }

        return true;
    }

    private void clickDashboard(double mouseX, double mouseY) {
        int left = contentX + 15;
        int y = contentY + contentHeight - 41;
        int buttonWidth = (contentWidth - 42) / 3;
        JsonObject request = new JsonObject();

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
        int rowTop = contentY + 13 + 34;
        int rows = visibleRows(96);
        int end = Math.min(scripts.size(), listOffset + rows);

        for (int i = listOffset; i < end; i++) {
            int y = rowTop + (i - listOffset) * 27;
            if (inside(mouseX, mouseY, left, y, contentWidth - 30, 24)) {
                JsonObject selected = scripts.get(i);
                selectedScriptPath = readString(selected, "path", "");
                notice = "Selected: " + readString(selected, "name", selectedScriptPath);
                return;
            }
        }

        int y = contentY + contentHeight - 41;
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

        JsonObject request = new JsonObject();
        request.addProperty("path", selectedScriptPath);
        post(start ? "/api/scripts/start" : "/api/scripts/load", request);
    }

    private void clickControls(double mouseX, double mouseY) {
        int left = contentX + 15;
        int buttonWidth = (contentWidth - 42) / 3;
        int rowY = contentY + 15 + 49;

        if (inside(mouseX, mouseY, left, rowY, buttonWidth, 34)) {
            post("/api/combat/attack", new JsonObject());
            return;
        }

        if (inside(mouseX, mouseY, left + buttonWidth + 6, rowY, buttonWidth, 34)) {
            post("/api/player/rest", new JsonObject());
            return;
        }

        if (inside(mouseX, mouseY, left + (buttonWidth + 6) * 2, rowY, buttonWidth, 34)) {
            JsonObject request = new JsonObject();
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
                post("/api/combat/skill/" + (i + 1), new JsonObject());
                return;
            }
        }
    }

    private void clickInventory(double mouseX, double mouseY) {
        int left = contentX + 15;
        int rowTop = contentY + 13 + 34;
        List<JsonObject> items = showBank ? bankItems : inventoryItems;
        int rows = visibleRows(96);
        int end = Math.min(items.size(), listOffset + rows);

        for (int i = listOffset; i < end; i++) {
            int y = rowTop + (i - listOffset) * 26;
            if (inside(mouseX, mouseY, left, y, contentWidth - 30, 23)) {
                JsonObject selected = items.get(i);
                selectedItemId = readNumber(selected, "id");
                selectedItemName = readString(selected, "name", "");
                notice = "Selected: " + selectedItemName;
                return;
            }
        }

        int y = contentY + contentHeight - 41;
        int buttonWidth = (contentWidth - 54) / 4;

        if (inside(mouseX, mouseY, left, y, buttonWidth, 27)) {
            refreshInventory();
        } else if (inside(mouseX, mouseY, left + buttonWidth + 6, y, buttonWidth, 27)) {
            if (showBank) {
                refreshBank();
            } else {
                refreshBank();
            }
        } else if (inside(mouseX, mouseY, left + (buttonWidth + 6) * 2, y, buttonWidth, 27)) {
            if (showBank) {
                notice = "Equip is only available for items in your bag.";
            } else if (selectedItemId > 0) {
                JsonObject request = new JsonObject();
                request.addProperty("id", selectedItemId);
                post("/api/inventory/equip", request);
            } else {
                notice = "Select a bag item first.";
            }
        } else if (inside(mouseX, mouseY, left + (buttonWidth + 6) * 3, y, buttonWidth, 27)) {
            if (selectedItemId <= 0) {
                notice = "Select an item first.";
                return;
            }

            JsonObject request = new JsonObject();
            request.addProperty("id", selectedItemId);
            post(showBank ? "/api/inventory/unbank" : "/api/inventory/bank", request);
        }
    }

    private void clickQuests(double mouseX, double mouseY) {
        int left = contentX + 15;
        int top = contentY + 13;
        int listWidth = (contentWidth - 42) / 2;
        int rowTop = top + 34;
        int rows = visibleRows(82);
        int end = Math.min(quests.size(), listOffset + rows);

        for (int i = listOffset; i < end; i++) {
            int y = rowTop + (i - listOffset) * 26;
            if (inside(mouseX, mouseY, left, y, listWidth, 23)) {
                selectedQuestId = readNumber(quests.get(i), "id");
                return;
            }
        }

        int y = contentY + contentHeight - 41;
        int buttonWidth = (contentWidth - 42) / 3;
        if (inside(mouseX, mouseY, left, y, buttonWidth, 27)) {
            refreshQuests();
        } else if (inside(mouseX, mouseY, left + buttonWidth + 6, y, buttonWidth, 27)) {
            JsonObject selected = findById(quests, selectedQuestId);
            if (selected == null) {
                notice = "Select a quest first.";
            } else if (!readBoolean(selected, "can_complete", false)) {
                notice = "Quest requirements are not complete yet.";
            } else {
                JsonObject request = new JsonObject();
                request.addProperty("id", selectedQuestId);
                post("/api/quests/complete", request);
            }
        }
    }

    private void clickDrops(double mouseX, double mouseY) {
        int left = contentX + 15;
        int rowTop = contentY + 13 + 34;
        int rows = visibleRows(90);
        int end = Math.min(drops.size(), listOffset + rows);

        for (int i = listOffset; i < end; i++) {
            int y = rowTop + (i - listOffset) * 27;
            if (inside(mouseX, mouseY, left, y, contentWidth - 30, 24)) {
                selectedDrop = drops.get(i);
                notice = "Selected drop: " + selectedDrop;
                return;
            }
        }

        int y = contentY + contentHeight - 41;
        int buttonWidth = (contentWidth - 42) / 3;
        if (inside(mouseX, mouseY, left, y, buttonWidth, 27)) {
            refreshDrops();
        } else if (inside(mouseX, mouseY, left + buttonWidth + 6, y, buttonWidth, 27)) {
            if (selectedDrop.isBlank()) {
                notice = "Select a drop first.";
                return;
            }
            JsonObject request = new JsonObject();
            request.addProperty("item", selectedDrop);
            post("/api/drops/pickup", request);
        } else if (inside(mouseX, mouseY, left + (buttonWidth + 6) * 2, y, buttonWidth, 27)) {
            post("/api/drops/pickup-all", new JsonObject());
        }
    }

    private void clickChat(double mouseX, double mouseY) {
        int y = contentY + contentHeight - 42;
        int x = contentX + contentWidth - 91;
        if (!inside(mouseX, mouseY, x, y, 76, 27)) {
            return;
        }

        String message = chatInput.getValue().trim();
        if (message.isBlank()) {
            notice = "Type a message first.";
            return;
        }

        JsonObject request = new JsonObject();
        request.addProperty("message", message);
        request.addProperty("type", "zone");
        post("/api/chat/send", request);
        chatInput.setValue("");
    }

    private void clickLogs(double mouseX, double mouseY) {
        int left = contentX + 15;
        int y = contentY + contentHeight - 41;
        if (inside(mouseX, mouseY, left, y, 132, 27)) {
            refreshLogs();
        }
    }

    private void clickOptions(double mouseX, double mouseY) {
        int left = contentX + 15;
        int top = contentY + 14;
        int gap = 8;
        int columns = 3;
        int buttonWidth = (contentWidth - 30 - gap * (columns - 1)) / columns;

        for (int i = 0; i < OPTION_FIELDS.length; i++) {
            int column = i % columns;
            int row = i / columns;
            int x = left + column * (buttonWidth + gap);
            int y = top + 43 + row * 39;
            if (inside(mouseX, mouseY, x, y, buttonWidth, 31)) {
                String optionName = OPTION_FIELDS[i][0];
                String optionKey = OPTION_FIELDS[i][1];
                boolean enabled = readBoolean(options, optionKey, false);

                JsonObject request = new JsonObject();
                request.addProperty("option", optionName);
                request.addProperty("value", !enabled);
                post("/api/options/toggle", request);
                return;
            }
        }
    }

    private void clickFleet(double mouseX, double mouseY) {
        int left = contentX + 15;
        int top = contentY + 13;
        int listWidth = (contentWidth - 42) / 2;
        int rowTop = top + 34;
        int rows = visibleRows(100);
        int end = Math.min(tabs.size(), listOffset + rows);

        for (int i = listOffset; i < end; i++) {
            int y = rowTop + (i - listOffset) * 25;
            if (inside(mouseX, mouseY, left, y, listWidth, 22)) {
                selectedPid = readNumber(tabs.get(i), "pid");
                notice = "Selected tab PID " + selectedPid;
                return;
            }
        }

        int y = contentY + contentHeight - 41;
        int buttonWidth = (contentWidth - 54) / 4;

        if (inside(mouseX, mouseY, left, y, buttonWidth, 27)) {
            refreshFleet();
        } else if (inside(mouseX, mouseY, left + buttonWidth + 6, y, buttonWidth, 27)) {
            if (selectedPid <= 0) {
                notice = "Select a client tab first.";
                return;
            }
            JsonObject request = new JsonObject();
            request.addProperty("pid", selectedPid);
            post("/api/tabs/select", request);
        } else if (inside(mouseX, mouseY, left + (buttonWidth + 6) * 2, y, buttonWidth, 27)) {
            post("/api/army/queue/start", new JsonObject());
        } else if (inside(mouseX, mouseY, left + (buttonWidth + 6) * 3, y, buttonWidth, 27)) {
            post("/api/army/queue/stop", new JsonObject());
        }
    }

    private JsonObject findById(List<JsonObject> source, int id) {
        for (JsonObject item : source) {
            if (readNumber(item, "id") == id) {
                return item;
            }
        }

        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxOffset = 0;

        switch (page) {
            case SCRIPTS -> maxOffset = Math.max(0, scripts.size() - visibleRows(96));
            case INVENTORY -> maxOffset = Math.max(0,
                    (showBank ? bankItems.size() : inventoryItems.size()) - visibleRows(96));
            case QUESTS -> maxOffset = Math.max(0, quests.size() - visibleRows(82));
            case DROPS -> maxOffset = Math.max(0, drops.size() - visibleRows(90));
            case CHAT -> maxOffset = Math.max(0, chatMessages.size() - visibleRows(92));
            case LOGS -> maxOffset = Math.max(0, logs.size() - visibleRows(74));
            case FLEET -> maxOffset = Math.max(0, tabs.size() - visibleRows(100));
            default -> {
                return super.mouseScrolled(mouseX, mouseY, delta);
            }
        }

        listOffset = Math.max(0, Math.min(maxOffset,
                listOffset - (int) Math.signum(delta) * 2));
        return true;
    }
}
