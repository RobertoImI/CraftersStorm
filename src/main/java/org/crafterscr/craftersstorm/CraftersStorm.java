package org.crafterscr.craftersstorm;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.crafterscr.craftersstorm.match.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.EventPriority;
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

    private static StormManager manager;
    private static MatchManager match;
    public static MatchManager match() { return match; }

    public CraftersStorm(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::serverStarted);
        NeoForge.EVENT_BUS.addListener(this::serverStopped);
        NeoForge.EVENT_BUS.addListener(this::serverTick);
        NeoForge.EVENT_BUS.addListener(this::death);
        NeoForge.EVENT_BUS.addListener(this::incoming);
        NeoForge.EVENT_BUS.addListener(this::damaged);
        NeoForge.EVENT_BUS.addListener(this::login);
        NeoForge.EVENT_BUS.addListener(this::logout);
        // Must run before PlayerRevive's HIGH interaction handler so enemies never
        // become valid revive helpers for a CraftersStorm participant.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this::interact);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("6")
                .playToClient(
                        StormPayload.TYPE,
                        StormPayload.CODEC,
                        (payload, context) -> context.enqueueWork(() -> StormView.receive(payload))
                );
        event.registrar("6").playToClient(MatchPayload.TYPE, MatchPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> MatchView.receive(p)));
        event.registrar("6").playToClient(SpectatorInventoryPayload.TYPE, SpectatorInventoryPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> SpectatorInventoryView.receive(p)));
        event.registrar("6").playToClient(TeamPayload.TYPE, TeamPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> TeamView.receive(p)));
        event.registrar("6").playToClient(RevivePayload.TYPE, RevivePayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ReviveView.receive(p)));
        event.registrar("6").playToServer(MatchControl.TYPE, MatchControl.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (match != null && ctx.player() instanceof ServerPlayer player) match.control(player, p);
                }));
    }

    private void death(LivingDeathEvent e) {
        if (match != null) match.death(e);
    }

    private void incoming(LivingIncomingDamageEvent e) {
        if (match != null) match.incoming(e);
    }

    private void damaged(LivingDamageEvent.Post e) {
        if (match != null) match.damaged(e);
    }

    private void interact(PlayerInteractEvent.EntityInteract e) {
        if (match != null) match.interact(e);
    }

    private void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (match != null && e.getEntity() instanceof ServerPlayer p) match.login(p);
    }

    private void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (match != null && e.getEntity() instanceof ServerPlayer p) match.logout(p);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        StormCommands.register(event.getDispatcher(), () -> manager);
    }

    private void serverStarted(ServerStartedEvent event) {
        manager = new StormManager(event.getServer());
        match = new MatchManager(event.getServer(), manager);
    }

    private void serverStopped(ServerStoppedEvent event) {
        manager = null;
        match = null;
    }

    private void serverTick(ServerTickEvent.Post event) {
        if (manager != null) {
            manager.tick();
            match.tick();
        }
    }

    public static boolean isStormActive(MinecraftServer server) {
        return server != null && manager != null && manager.isActiveFor(server);
    }
}
