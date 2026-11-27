package io.github.wtffidy.skuacraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public final class SkuaToolsPanel {
    private static final int SHELL = 0xF0151D2B;
    private static final int INK = 0xFF0B121C;
    private static final int PANEL = 0xFF1C2939;
    private static final int PANEL_ALT = 0xFF26384B;
    private static final int TEAL = 0xFF57E6C1;
    private static final int TEAL_DARK = 0xFF1B766F;
    private static final int VIOLET = 0xFFB18AF5;
    private static final int AMBER = 0xFFFFC36A;
    private static final int RED = 0xFFFF777D;
    private static final int TEXT = 0xFFE8F1F4;
    private static final int MUTED = 0xFF91A5B4;

    private final List<JsonObject> scripts = new ArrayList<>();

    private JsonObject playerStatus = new JsonObject();
    private String selectedScriptPath = "";
    private String message = "Connect to the embedded Skua runtime to load scripts.";
    private boolean open;
    private boolean scriptsLoading;
    private int scriptOffset;

    public boolean isOpen() {
        return open;
    }

    public void toggle(int screenWidth, int screenHeight) {
        setOpen(!open, screenWidth, screenHeight);
    }

    public void setOpen(boolean open, int screenWidth, int screenHeight) {
        this.open = open;
        if (open) {
            refresh();
        }
    }

    public void refresh() {
        if (!PlayerControlClient.isConnected()) {
            message = "Skua control link is not ready.";
            return;
        }

        PlayerControlClient.get("status", this::handleStatus);
        if (!scriptsLoading) {
            scriptsLoading = true;
            PlayerControlClient.get("scripts", this::handleScripts);
        }
    }

    private void handleStatus(JsonElement response) {
        if (response != null && response.isJsonObject()) {
            JsonObject result = response.getAsJsonObject();
            if (isSuccessful(result)) {
                playerStatus = result;
                return;
            }

            message = getString(result, "message", "Could not read Skua player status.");
            return;
        }

        message = "Skua returned an empty status response.";
    }

    private void handleScripts(JsonElement response) {
        scriptsLoading = false;
        scripts.clear();
        scriptOffset = 0;

        if (response != null && response.isJsonObject()) {
            JsonObject result = response.getAsJsonObject();
            JsonElement array = result.get("scripts");
            if (array != null && array.isJsonArray()) {
                for (JsonElement element : array.getAsJsonArray()) {
                    if (element != null && element.isJsonObject()) {
                        scripts.add(element.getAsJsonObject());
                    }
                }
            }

            message = isSuccessful(result)
                    ? "Loaded " + scripts.size() + " scripts from your Skua fork."
                    : getString(result, "message", "Could not load the script catalogue.");
            return;
        }

        message = "Could not read the Skua script catalogue.";
    }

    private void post(String endpoint, JsonObject body) {
        message = "Sending command to Skua Core...";
        PlayerControlClient.post(endpoint, body, result -> {
            if (result != null && result.isJsonObject()) {
                JsonObject response = result.getAsJsonObject();
                message = getString(response, "message",
                        isSuccessful(response) ? "Command accepted." : "Skua command failed.");
                if (isSuccessful(response)) {
                    refresh();
                }
                return;
            }

            message = "Skua returned an empty command response.";
        });
    }

    private static boolean isSuccessful(JsonObject object) {
        return object != null
                && (!object.has("success") || object.get("success").isJsonNull()
                || object.get("success").getAsBoolean());
    }

    private static String getString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }

        try {
            return object.get(key).getAsString();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static int getNumber(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return 0;
        }

        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private static boolean getBoolean(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return false;
        }

        try {
            return object.get(key).getAsBoolean();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static JsonObject emptyBody() {
        return new JsonObject();
    }

    private int panelX(int screenWidth) {
        return Math.max(8, screenWidth - Math.min(278, screenWidth - 16) - 8);
    }

    private int panelWidth(int screenWidth) {
        return Math.min(278, screenWidth - 16);
    }

    private int panelY() {
        return 43;
    }

    private int panelHeight(int screenHeight) {
        return screenHeight - 83;
    }

    public boolean isPointerOver(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (!open) {
            return false;
        }

        int x = panelX(screenWidth);
        int y = panelY();
        return mouseX >= x && mouseX <= x + panelWidth(screenWidth)
                && mouseY >= y && mouseY <= y + panelHeight(screenHeight);
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!open) {
            return;
        }

        int x = panelX(screenWidth);
        int y = panelY();
        int width = panelWidth(screenWidth);
        int height = panelHeight(screenHeight);
        int right = x + width;

        graphics.fill(x - 3, y - 3, right + 3, y + height + 3, 0xB8000000);
        graphics.fill(x, y, right, y + height, SHELL);
        graphics.fill(x + 4, y + 4, right - 4, y + height - 4, INK);
        graphics.fill(x + 10, y + 10, right - 10, y + 11, TEAL_DARK);

        graphics.drawString(font, "SKUA TOOLS", x + 15, y + 15, TEAL, false);
        graphics.drawString(font, "CORE LINK  " + (PlayerControlClient.isConnected() ? "ONLINE" : "OFFLINE"),
                x + 15, y + 31, PlayerControlClient.isConnected() ? TEAL : RED, false);

        drawButton(graphics, font, right - 76, y + 12, 61, 22, "REFRESH", VIOLET, false);

        String username = getString(playerStatus, "username", "Waiting for AQW...");
        String map = getString(playerStatus, "map", "Unknown map");
        String server = getString(playerStatus, "server", "Unknown");
        graphics.drawString(font, clip(font, username + "  ·  Lv " + getNumber(playerStatus, "level"), width - 30),
                x + 15, y + 55, TEXT, false);
        graphics.drawString(font, clip(font, map + "  /  " + server, width - 30),
                x + 15, y + 69, MUTED, false);

        int listTop = y + 91;
        int listBottom = y + height - 100;
        int rowHeight = 23;
        int visibleRows = Math.max(1, (listBottom - listTop) / rowHeight);
        int end = Math.min(scripts.size(), scriptOffset + visibleRows);

        graphics.drawString(font, scripts.size() + " SCRIPTS  /  YOUR FORK", x + 15, listTop - 14, VIOLET, false);

        for (int i = scriptOffset; i < end; i++) {
            JsonObject script = scripts.get(i);
            int rowY = listTop + (i - scriptOffset) * rowHeight;
            boolean selected = getString(script, "path", "").equals(selectedScriptPath);
            boolean hovered = mouseX >= x + 10 && mouseX <= right - 10
                    && mouseY >= rowY && mouseY <= rowY + rowHeight - 2;

            graphics.fill(x + 10, rowY, right - 10, rowY + rowHeight - 2,
                    selected ? TEAL_DARK : hovered ? PANEL_ALT : PANEL);

            String name = getString(script, "name", getString(script, "fileName", "Unnamed"));
            String category = getString(script, "category", category(script));
            String state = getBoolean(script, "downloaded") ? "LOCAL" : "REMOTE";
            graphics.drawString(font, clip(font, name, width - 105),
                    x + 17, rowY + 7, TEXT, false);
            graphics.drawString(font, clip(font, category + " · " + state, 85),
                    right - 94, rowY + 7, selected ? TEAL : MUTED, false);
        }

        if (scripts.isEmpty()) {
            String empty = scriptsLoading ? "Loading script catalogue..."
                    : "Open the player host log if the catalogue did not load.";
            graphics.drawString(font, clip(font, empty, width - 30), x + 15, listTop + 12, MUTED, false);
        }

        int actionY = y + height - 81;
        int gap = 6;
        int buttonWidth = (width - 30 - gap) / 2;
        drawButton(graphics, font, x + 10, actionY, buttonWidth, 24, "LOAD", TEAL, false);
        drawButton(graphics, font, x + 16 + buttonWidth, actionY, buttonWidth, 24, "START", AMBER, false);
        drawButton(graphics, font, x + 10, actionY + 29, buttonWidth, 24, "PAUSE / RESUME", VIOLET, false);
        drawButton(graphics, font, x + 16 + buttonWidth, actionY + 29, buttonWidth, 24, "STOP", RED, false);

        graphics.fill(x + 10, y + height - 22, right - 10, y + height - 21, 0xFF34485B);
        graphics.drawString(font, clip(font, message, width - 30),
                x + 15, y + height - 14, message.toLowerCase().contains("fail") ? RED : MUTED, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!open || button != 0 || !isPointerOver(mouseX, mouseY, screenWidth, screenHeight)) {
            return false;
        }

        int x = panelX(screenWidth);
        int y = panelY();
        int width = panelWidth(screenWidth);
        int height = panelHeight(screenHeight);
        int right = x + width;

        if (inside(mouseX, mouseY, right - 76, y + 12, 61, 22)) {
            refresh();
            return true;
        }

        int listTop = y + 91;
        int listBottom = y + height - 100;
        int rowHeight = 23;
        int visibleRows = Math.max(1, (listBottom - listTop) / rowHeight);
        int end = Math.min(scripts.size(), scriptOffset + visibleRows);

        for (int i = scriptOffset; i < end; i++) {
            int rowY = listTop + (i - scriptOffset) * rowHeight;
            if (inside(mouseX, mouseY, x + 10, rowY, width - 20, rowHeight - 2)) {
                selectedScriptPath = getString(scripts.get(i), "path", "");
                message = "Selected " + getString(scripts.get(i), "name", selectedScriptPath);
                return true;
            }
        }

        int actionY = y + height - 81;
        int gap = 6;
        int buttonWidth = (width - 30 - gap) / 2;

        if (inside(mouseX, mouseY, x + 10, actionY, buttonWidth, 24)) {
            sendSelected("scripts/load");
        } else if (inside(mouseX, mouseY, x + 16 + buttonWidth, actionY, buttonWidth, 24)) {
            sendSelected("scripts/start");
        } else if (inside(mouseX, mouseY, x + 10, actionY + 29, buttonWidth, 24)) {
            post("scripts/pause", emptyBody());
        } else if (inside(mouseX, mouseY, x + 16 + buttonWidth, actionY + 29, buttonWidth, 24)) {
            post("scripts/stop", emptyBody());
        }

        return true;
    }

    private void sendSelected(String endpoint) {
        if (selectedScriptPath.isBlank()) {
            message = "Select a script first.";
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("path", selectedScriptPath);
        post(endpoint, body);
    }



    public boolean mouseScrolled(double mouseX, double mouseY, double delta, int screenWidth, int screenHeight) {
        if (!isPointerOver(mouseX, mouseY, screenWidth, screenHeight)) {
            return false;
        }

        int height = panelHeight(screenHeight);
        int listTop = panelY() + 91;
        int listBottom = panelY() + height - 100;
        int visibleRows = Math.max(1, (listBottom - listTop) / 23);
        int maxOffset = Math.max(0, scripts.size() - visibleRows);
        scriptOffset = Math.max(0, Math.min(maxOffset,
                scriptOffset - (int) Math.signum(delta) * 3));
        return true;
    }

    private static void drawButton(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                   String text, int accent, boolean active) {
        graphics.fill(x, y, x + width, y + height, active ? TEAL_DARK : PANEL_ALT);
        graphics.fill(x, y, x + 3, y + height, accent);
        graphics.drawCenteredString(font, text, x + width / 2, y + (height - 8) / 2, TEXT);
    }

    private static String category(JsonObject script) {
        String path = getString(script, "path", "");
        int separator = path.lastIndexOf('/');
        return separator <= 0 ? "Root" : path.substring(0, separator);
    }

    private static String clip(Font font, String value, int maxWidth) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String clipped = value;
        while (clipped.length() > 1 && font.width(clipped) > maxWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }

        return clipped.equals(value) ? value : clipped + "...";
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
