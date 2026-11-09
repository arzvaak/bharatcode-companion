package com.bharatcode.companion.client;

import com.bharatcode.companion.BharatCodeCompanion;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = BharatCodeCompanion.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CompanionClientEvents {
    private CompanionClientEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BharatCodeCompanion.COMPANION.get(), CompanionRenderer::new);
    }
}
