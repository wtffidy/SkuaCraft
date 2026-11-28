package io.github.wtffidy.skuacraft.client;

import io.github.wtffidy.skuacraft.block.entity.SkuaRackBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
public final class SkuaRackClientHooks {
    private static final Map<SkuaRackBlockEntity, Set<String>> STARTED_ACCOUNTS = new WeakHashMap<>();

    private SkuaRackClientHooks() {
    }

    public static void tick(SkuaRackBlockEntity rack) {
        Level level = rack.getLevel();
        if (level == null || level.getGameTime() % 40 != 0) {
            return;
        }

        synchronized (STARTED_ACCOUNTS) {
            Set<String> started = STARTED_ACCOUNTS.computeIfAbsent(rack, ignored -> new HashSet<>());

            for (String accountId : rack.getAccountIds()) {
                if (!started.add(accountId)) {
                    continue;
                }

                PlayerHostLauncher.ensureRunningInBackground(accountId, result -> {
                    if (!result.success()) {
                        synchronized (STARTED_ACCOUNTS) {
                            Set<String> current = STARTED_ACCOUNTS.get(rack);
                            if (current != null) {
                                current.remove(accountId);
                            }
                        }

                        Minecraft.getInstance().gui.setOverlayMessage(
                                net.minecraft.network.chat.Component.literal("Skua rack: " + result.message()),
                                false
                        );
                    }
                });
            }

            started.retainAll(rack.getAccountIds());
        }
    }

    public static void openRack(BlockPos position) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || !(level.getBlockEntity(position) instanceof SkuaRackBlockEntity rack)) {
            return;
        }

        minecraft.setScreen(new SkuaRackScreen(position, rack.getAccountSlots()));
    }
}
