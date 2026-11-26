package io.github.wtffidy.skuacraft;

import io.github.wtffidy.skuacraft.config.SkuaCraftConfig;
import io.github.wtffidy.skuacraft.item.SkuaConsoleItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(SkuaCraft.MOD_ID)
public final class SkuaCraft {
    public static final String MOD_ID = "skuacraft";

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final RegistryObject<Item> SKUA_CONSOLE = ITEMS.register(
            "skua_console",
            () -> new SkuaConsoleItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    public static final RegistryObject<CreativeModeTab> SKUA_TAB = CREATIVE_TABS.register(
            "skuacraft",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.skuacraft"))
                    .icon(() -> new ItemStack(SKUA_CONSOLE.get()))
                    .displayItems((parameters, output) -> output.accept(SKUA_CONSOLE.get()))
                    .build()
    );

    public SkuaCraft() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        SkuaCraftConfig.register();
    }
}
