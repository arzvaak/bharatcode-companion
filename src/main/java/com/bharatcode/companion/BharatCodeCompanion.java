package com.bharatcode.companion;

import org.slf4j.Logger;

import com.bharatcode.companion.entity.CompanionEntity;
import com.bharatcode.companion.item.SummoningCharmItem;
import com.bharatcode.companion.server.CompanionService;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(BharatCodeCompanion.MODID)
public final class BharatCodeCompanion {
    public static final String MODID = "bharatcode_companion";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE,
            MODID);

    public static final DeferredHolder<Item, SummoningCharmItem> SUMMONING_CHARM = ITEMS.register("summoning_charm",
            () -> new SummoningCharmItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> COMPANION = ENTITY_TYPES.register(
            "companion",
            () -> EntityType.Builder.of(CompanionEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .noSave()
                    .build("companion"));

    public BharatCodeCompanion(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::registerGameTests);
        NeoForge.EVENT_BUS.addListener(CompanionService::onServerStarted);
        NeoForge.EVENT_BUS.addListener(CompanionService::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(CompanionService::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(CompanionService::onServerTick);
        NeoForge.EVENT_BUS.addListener(CompanionService::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(CompanionService::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(CompanionService::onServerStopping);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(SUMMONING_CHARM.get());
        }
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(COMPANION.get(), CompanionEntity.createAttributes().build());
    }

    private void registerGameTests(RegisterGameTestsEvent event) {
        try {
            // The lifecycle harness lives in the gametest source set and is not part
            // of the published jar. Class lookup keeps the production artifact clean.
            event.register(Class.forName("com.bharatcode.companion.gametest.CompanionLifecycleGameTests"));
        } catch (ClassNotFoundException ignored) {
            // No harness on the production classpath.
        }
    }
}
