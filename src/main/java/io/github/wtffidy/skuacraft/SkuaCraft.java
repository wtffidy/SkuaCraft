package io.github.wtffidy.skuacraft;

import io.github.wtffidy.skuacraft.block.SkuaRackBlock;
import io.github.wtffidy.skuacraft.block.entity.SkuaRackBlockEntity;
import io.github.wtffidy.skuacraft.config.SkuaCraftConfig;
import io.github.wtffidy.skuacraft.item.SkuaConsoleItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(SkuaCraft.MOD_ID)
public final class SkuaCraft {
    public static final String MOD_ID = "skuacraft";

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final RegistryObject<Block> SERVER_RACK_BLOCK = BLOCKS.register(
            "server_rack",
            () -> new SkuaRackBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL))
    );

    public static final RegistryObject<BlockEntityType<SkuaRackBlockEntity>> SERVER_RACK_ENTITY = BLOCK_ENTITIES.register(
            "server_rack",
            () -> BlockEntityType.Builder.of(SkuaRackBlockEntity::new, SERVER_RACK_BLOCK.get()).build(null)
    );

    public static final RegistryObject<Item> SERVER_RACK = ITEMS.register(
            "server_rack",
            () -> new BlockItem(SERVER_RACK_BLOCK.get(), new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final RegistryObject<Item> SKUA_CONSOLE = ITEMS.register(
            "skua_console",
            () -> new SkuaConsoleItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    public static final RegistryObject<CreativeModeTab> SKUA_TAB = CREATIVE_TABS.register(
            "skuacraft",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.skuacraft"))
                    .icon(() -> new ItemStack(SKUA_CONSOLE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(SKUA_CONSOLE.get());
                        output.accept(SERVER_RACK.get());
                    })
                    .build()
    );

    public SkuaCraft() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        SkuaCraftConfig.register();
    }
}
