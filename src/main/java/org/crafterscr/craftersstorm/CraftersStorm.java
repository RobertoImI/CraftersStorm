package org.crafterscr.craftersstorm;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(CraftersStorm.MOD_ID)
public final class CraftersStorm {
    public static final String MOD_ID = "craftersstorm";

    private StormManager manager;

    public CraftersStorm(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::serverStarted);
        NeoForge.EVENT_BUS.addListener(this::serverStopped);
        NeoForge.EVENT_BUS.addListener(this::serverTick);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .optional()
                .playToClient(
                        StormPayload.TYPE,
                        StormPayload.CODEC,
                        (payload, context) ->
                                context.enqueueWork(() -> StormView.receive(payload))
                );
    }

    private void registerCommands(RegisterCommandsEvent event) {
        StormCommands.register(event.getDispatcher(), () -> manager);
    }

    private void serverStarted(ServerStartedEvent event) {
        manager = new StormManager(event.getServer());
    }

    private void serverStopped(ServerStoppedEvent event) {
        manager = null;
    }

    private void serverTick(ServerTickEvent.Post event) {
        if (manager != null) {
            manager.tick();
        }
    }
}