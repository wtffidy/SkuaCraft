package io.github.wtffidy.skuacraft.client;

import io.github.wtffidy.skuacraft.SkuaCraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SkuaCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SkuaRackClientEvents {
    private SkuaRackClientEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SkuaCraft.SERVER_RACK_ENTITY.get(), SkuaRackRenderer::new);
    }
}
