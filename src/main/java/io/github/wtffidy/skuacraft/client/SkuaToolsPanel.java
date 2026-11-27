package io.github.wtffidy.skuacraft.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    private static final int ROW_HEIGHT = 20;
    private static final int MAX_PAGE_SIZE = 12;

    private final List<JsonObject> scripts = new ArrayList<>();

    private JsonObject playerStatus = new JsonObject();
    private String selectedScriptPath = "";
    private String searchText = "";
    private String message = "Connect to the embedded Skua runtime to load scripts.";
    private boolean open;
    private boolean scriptsLoading;
    private boolean searchFocused;
    private boolean searchSelected;
    private int searchCaret;
    private int pageIndex;

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
        } else {
            searchFocused = false;
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
        pageIndex = 0;

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
        message = endpoint.endsWith("/start")
                ? "Downloading, verifying and compiling script..."
                : endpoint.endsWith("/load")
                    ? "Downloading and loading script..."
                    : "Sending command to Skua Core...";

        PlayerControlClient.post(endpoint, body, result -> {
            if (result != null && result.isJsonObject()) {
                JsonObject response = result.getAsJsonObject();
                boolean successful = isSuccessful(response);
                message = getString(response, "message",
                        successful ? "Command accepted." : "Skua command failed.");

                if (successful) {
                    refresh();
                }
                return;
            }

            message = "Skua returned an empty command response.";
        });
    }

    private static boolean isSuccessful(JsonObject object) {
        if (object == null || !object.has("success") || object.get("success").isJsonNull()) {
            return true;
        }

        try {
            return object.get("success").getAsBoolean();
        } catch (RuntimeException exception) {
            return false;
        }
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
        return Math.max(8, screenWidth - Math.min(340, screenWidth - 16) - 8);
    }

    private int panelWidth(int screenWidth) {
        return Math.min(340, screenWidth - 16);
    }

    private int panelY() {
        return 43;
    }

    private int panelHeight(int screenHeight) {
        return Math.max(132, screenHeight - panelY() - 8);
    }

    private PanelLayout layout(int screenWidth, int screenHeight) {
        int x = panelX(screenWidth);
        int y = panelY();
        int width = panelWidth(screenWidth);
        int height = panelHeight(screenHeight);
        int searchY = y + 58;
        int pageY = y + 80;
        int listTop = y + 102;
        int actionY = y + height - 73;
        int listBottom = actionY - 6;
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, (listBottom - listTop) / ROW_HEIGHT));
        return new PanelLayout(x, y, width, height, searchY, pageY, listTop, listBottom, actionY, pageSize);
    }

    public boolean isPointerOver(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (!open) {
            return false;
        }

        PanelLayout layout = layout(screenWidth, screenHeight);
        return mouseX >= layout.x && mouseX <= layout.x + layout.width
                && mouseY >= layout.y && mouseY <= layout.y + layout.height;
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!open) {
            return;
        }

        PanelLayout layout = layout(screenWidth, screenHeight);
        int x = layout.x;
        int y = layout.y;
        int width = layout.width;
        int right = x + width;

        List<JsonObject> filtered = getFilteredScripts();
        int pageCount = Math.max(1, (filtered.size() + layout.pageSize - 1) / layout.pageSize);
        pageIndex = Math.max(0, Math.min(pageIndex, pageCount - 1));
        int start = pageIndex * layout.pageSize;
        int end = Math.min(filtered.size(), start + layout.pageSize);

        graphics.fill(x - 3, y - 3, right + 3, y + layout.height + 3, 0xB8000000);
        graphics.fill(x, y, right, y + layout.height, SHELL);
        graphics.fill(x + 4, y + 4, right - 4, y + layout.height - 4, INK);
        graphics.fill(x + 10, y + 10, right - 10, y + 11, TEAL_DARK);

        graphics.drawString(font, "SKUA TOOLS", x + 15, y + 9, TEAL, false);
        graphics.drawString(font, "CORE LINK  " + (PlayerControlClient.isConnected() ? "ONLINE" : "OFFLINE"),
                x + 15, y + 25, PlayerControlClient.isConnected() ? TEAL : RED, false);

        drawButton(graphics, font, right - 76, y + 7, 61, 22, "REFRESH", VIOLET, false);

        String username = getString(playerStatus, "username", "AQW not connected");
        String map = getString(playerStatus, "map", "Unknown map");
        String server = getString(playerStatus, "server", "Unknown");
        String summary = username + "  ·  Lv " + getNumber(playerStatus, "level") + "  ·  " + map + " / " + server;
        graphics.drawString(font, clip(font, summary, width - 30), x + 15, y + 42, TEXT, false);

        graphics.fill(x + 10, layout.searchY, right - 10, layout.searchY + 18,
                searchFocused ? PANEL_ALT : PANEL);
        graphics.fill(x + 10, layout.searchY, x + 12, layout.searchY + 18,
                searchFocused ? TEAL : MUTED);
        String searchDisplay = searchText.isEmpty() ? "Search name, folder, or description..." : searchText;
        graphics.drawString(font, clip(font, searchDisplay, width - 42),
                x + 18, layout.searchY + 5, searchText.isEmpty() ? MUTED : TEXT, false);
        if (searchFocused) {
            int cursorX = x + 18 + font.width(clip(font, searchText.substring(0, searchCaret), width - 42));
            graphics.fill(Math.min(cursorX, right - 18), layout.searchY + 4,
                    Math.min(cursorX, right - 18) + 1, layout.searchY + 15, TEAL);
        }

        String countText = filtered.size() + " MATCHES  ·  PAGE " + (pageIndex + 1) + "/" + pageCount;
        graphics.drawString(font, clip(font, countText, width - 100), x + 15, layout.pageY + 5, VIOLET, false);
        drawButton(graphics, font, right - 65, layout.pageY, 25, 18, "<", VIOLET, pageIndex > 0);
        drawButton(graphics, font, right - 36, layout.pageY, 25, 18, ">", VIOLET, pageIndex + 1 < pageCount);

        for (int i = start; i < end; i++) {
            JsonObject script = filtered.get(i);
            int rowY = layout.listTop + (i - start) * ROW_HEIGHT;
            boolean selected = getString(script, "path", "").equals(selectedScriptPath);
            boolean hovered = mouseX >= x + 10 && mouseX <= right - 10
                    && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2;

            graphics.fill(x + 10, rowY, right - 10, rowY + ROW_HEIGHT - 2,
                    selected ? TEAL_DARK : hovered ? PANEL_ALT : PANEL);

            String name = getString(script, "name", getString(script, "fileName", "Unnamed"));
            String category = getString(script, "category", category(script));
            String state = getBoolean(script, "downloaded") ? "LOCAL" : "REMOTE";
            graphics.drawString(font, clip(font, name, width - 105),
                    x + 17, rowY + 6, TEXT, false);
            graphics.drawString(font, clip(font, category + " · " + state, 85),
                    right - 94, rowY + 6, selected ? TEAL : MUTED, false);
        }

        if (filtered.isEmpty()) {
            String empty = scriptsLoading ? "Loading script catalogue..."
                    : searchText.isBlank() ? "No scripts are available."
                    : "No scripts match that search.";
            graphics.drawString(font, clip(font, empty, width - 30), x + 15, layout.listTop + 6, MUTED, false);
        }

        int buttonWidth = (width - 30 - 6) / 2;
        drawButton(graphics, font, x + 10, layout.actionY, buttonWidth, 20, "LOAD", TEAL, false);
        drawButton(graphics, font, x + 16 + buttonWidth, layout.actionY, buttonWidth, 20, "START", AMBER, false);
        drawButton(graphics, font, x + 10, layout.actionY + 24, buttonWidth, 20, "PAUSE / RESUME", VIOLET, false);
        drawButton(graphics, font, x + 16 + buttonWidth, layout.actionY + 24, buttonWidth, 20, "STOP", RED, false);

        graphics.drawString(font, clip(font, message, width - 30),
                x + 15, y + layout.height - 14,
                message.toLowerCase(Locale.ROOT).contains("fail")
                        || message.toLowerCase(Locale.ROOT).contains("could not")
                        || message.toLowerCase(Locale.ROOT).contains("error") ? RED : MUTED,
                false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!open || button != 0 || !isPointerOver(mouseX, mouseY, screenWidth, screenHeight)) {
            return false;
        }

        PanelLayout layout = layout(screenWidth, screenHeight);
        int x = layout.x;
        int y = layout.y;
        int width = layout.width;
        int right = x + width;

        if (inside(mouseX, mouseY, right - 76, y + 7, 61, 22)) {
            searchFocused = false;
            refresh();
            return true;
        }

        if (inside(mouseX, mouseY, x + 10, layout.searchY, width - 20, 18)) {
            searchFocused = true;
            searchCaret = searchText.length();
            searchSelected = false;
            return true;
        }

        searchFocused = false;
        List<JsonObject> filtered = getFilteredScripts();
        int pageCount = Math.max(1, (filtered.size() + layout.pageSize - 1) / layout.pageSize);

        if (inside(mouseX, mouseY, right - 65, layout.pageY, 25, 18)) {
            pageIndex = Math.max(0, pageIndex - 1);
            return true;
        }

        if (inside(mouseX, mouseY, right - 36, layout.pageY, 25, 18)) {
            pageIndex = Math.min(pageCount - 1, pageIndex + 1);
            return true;
        }

        int start = pageIndex * layout.pageSize;
        int end = Math.min(filtered.size(), start + layout.pageSize);
        for (int i = start; i < end; i++) {
            int rowY = layout.listTop + (i - start) * ROW_HEIGHT;
            if (inside(mouseX, mouseY, x + 10, rowY, width - 20, ROW_HEIGHT - 2)) {
                selectedScriptPath = getString(filtered.get(i), "path", "");
                message = "Selected " + getString(filtered.get(i), "name", selectedScriptPath);
                return true;
            }
        }

        int buttonWidth = (width - 30 - 6) / 2;
        if (inside(mouseX, mouseY, x + 10, layout.actionY, buttonWidth, 20)) {
            sendSelected("scripts/load");
        } else if (inside(mouseX, mouseY, x + 16 + buttonWidth, layout.actionY, buttonWidth, 20)) {
            sendSelected("scripts/start");
        } else if (inside(mouseX, mouseY, x + 10, layout.actionY + 24, buttonWidth, 20)) {
            post("scripts/pause", emptyBody());
        } else if (inside(mouseX, mouseY, x + 16 + buttonWidth, layout.actionY + 24, buttonWidth, 20)) {
            post("scripts/stop", emptyBody());
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta, int screenWidth, int screenHeight) {
        if (!isPointerOver(mouseX, mouseY, screenWidth, screenHeight)) {
            return false;
        }

        PanelLayout layout = layout(screenWidth, screenHeight);
        List<JsonObject> filtered = getFilteredScripts();
        int pageCount = Math.max(1, (filtered.size() + layout.pageSize - 1) / layout.pageSize);

        if (delta > 0) {
            pageIndex = Math.max(0, pageIndex - 1);
        } else if (delta < 0) {
            pageIndex = Math.min(pageCount - 1, pageIndex + 1);
        }

        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!searchFocused) {
            return false;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            searchFocused = false;
            searchSelected = false;
            return true;
        }

        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_A) {
            searchSelected = true;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (searchSelected) {
                searchText = "";
                searchCaret = 0;
                searchSelected = false;
            } else if (searchCaret > 0) {
                searchText = searchText.substring(0, searchCaret - 1) + searchText.substring(searchCaret);
                searchCaret--;
            }

            pageIndex = 0;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (searchSelected) {
                searchText = "";
                searchCaret = 0;
                searchSelected = false;
            } else if (searchCaret < searchText.length()) {
                searchText = searchText.substring(0, searchCaret) + searchText.substring(searchCaret + 1);
            }

            pageIndex = 0;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            searchCaret = Math.max(0, searchCaret - 1);
            searchSelected = false;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            searchCaret = Math.min(searchText.length(), searchCaret + 1);
            searchSelected = false;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_HOME) {
            searchCaret = 0;
            searchSelected = false;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_END) {
            searchCaret = searchText.length();
            searchSelected = false;
            return true;
        }

        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!searchFocused || Character.isISOControl(codePoint) || searchText.length() >= 120) {
            return searchFocused;
        }

        if (searchSelected) {
            searchText = "";
            searchCaret = 0;
            searchSelected = false;
        }

        searchText = searchText.substring(0, searchCaret) + codePoint + searchText.substring(searchCaret);
        searchCaret++;
        pageIndex = 0;
        return true;
    }

    private List<JsonObject> getFilteredScripts() {
        String query = searchText.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return scripts;
        }

        List<JsonObject> filtered = new ArrayList<>();
        for (JsonObject script : scripts) {
            String name = getString(script, "name", getString(script, "fileName", ""));
            String path = getString(script, "path", "");
            String description = getString(script, "description", "");
            String category = getString(script, "category", category(script));
            String tags = "";

            JsonElement tagElement = script.get("tags");
            if (tagElement != null && tagElement.isJsonArray()) {
                List<String> values = new ArrayList<>();
                for (JsonElement tag : tagElement.getAsJsonArray()) {
                    if (tag != null && tag.isJsonPrimitive()) {
                        values.add(tag.getAsString());
                    }
                }
                tags = String.join(" ", values);
            }

            String searchable = (name + " " + path + " " + description + " " + category + " " + tags)
                    .toLowerCase(Locale.ROOT);
            if (searchable.contains(query)) {
                filtered.add(script);
            }
        }

        return filtered;
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

    private record PanelLayout(
            int x,
            int y,
            int width,
            int height,
            int searchY,
            int pageY,
            int listTop,
            int listBottom,
            int actionY,
            int pageSize
    ) {
    }
}
