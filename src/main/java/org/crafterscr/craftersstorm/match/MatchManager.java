package org.crafterscr.craftersstorm.match;

import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import org.crafterscr.craftersstorm.compat.PlayerReviveCompat;
import org.crafterscr.craftersstorm.mixin.DeathLootInvoker;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.crafterscr.craftersstorm.StormData;
import org.crafterscr.craftersstorm.StormManager;

import java.util.*;

public final class MatchManager {
    private static final double DEPLOYMENT_HALF_SIZE = 5.0;
    private static final double DEPLOYMENT_DROP_EXIT = 2.0;

    private final MinecraftServer server;
    private final StormManager storm;
    private final StormData data;
    private final MatchState s;
    private final Map<UUID, UUID> watching = new HashMap<>();
    private record Camera(int perspective, int fov) {}
    private final Map<UUID, Camera> cameras = new HashMap<>();
    private final Map<UUID, Long> lastControl = new HashMap<>();
    private final Map<UUID, Long> lastCamera = new HashMap<>();
    private long ticks;

    public MatchManager(MinecraftServer server, StormManager storm) {
        this.server = server;
        this.storm = storm;
        this.data = StormData.get(server);
        if (data.values.match == null) data.values.match = new MatchState();
        this.s = data.values.match;
    }

    public boolean allowSpectatorCamera(ServerPlayer spectator, net.minecraft.world.entity.Entity target) {
        MatchState.Member member = s.members.get(spectator.getUUID());
        if (!running() || s.teamSize == 1 || member == null || member.status != MatchState.Status.ELIMINATED
                || target == null || target == spectator) return true;
        return target instanceof ServerPlayer other && other.isAlive()
                && s.canSpectate(spectator.getUUID(), other.getUUID());
    }

    public MatchState settings() { return s; }

    public void configureMode(int size) {
        notRunning();
        if (!s.members.isEmpty())
            throw new IllegalStateException("Configura el modo antes de inscribir jugadores (preparar reinicia la lista).");
        if (size < 1 || size > 4) throw new IllegalArgumentException("Modo inválido.");
        s.teamSize = size;
        dirty();
    }

    public void saveSpawn(String id, ServerPlayer p) {
        notRunning();
        if (!id.matches("[a-z0-9_-]{1,16}"))
            throw new IllegalArgumentException("ID: 1 a 16 letras minúsculas, números, _ o -.");
        if (!p.level().dimension().equals(Level.OVERWORLD))
            throw new IllegalStateException("Los puntos de salida deben estar en el Overworld.");
        if (s.spawns.size() >= 100 && !s.spawns.containsKey(id))
            throw new IllegalStateException("Máximo 100 puntos.");
        MatchState.Destination d = new MatchState.Destination();
        d.x = p.getX();
        d.y = p.getY();
        d.z = p.getZ();
        d.yaw = p.getYRot();
        d.pitch = p.getXRot();
        s.spawns.put(id, d);
        dirty();
    }

    public void removeSpawn(String id) {
        notRunning();
        if (s.spawns.remove(id) == null) throw new IllegalStateException("Ese punto no existe.");
        dirty();
    }

    public void clearSpawns() {
        notRunning();
        s.spawns.clear();
        dirty();
    }

    public void changeTeam(ServerPlayer p, String team, boolean create) {
        if (!s.enrollment) throw new IllegalStateException("Las inscripciones están cerradas.");
        s.changeTeam(p.getUUID(), team, create);
        dirty();
        p.sendSystemMessage(Component.literal("Tu equipo: " + team).withStyle(ChatFormatting.GREEN));
    }

    public void showTeams(ServerPlayer p) {
        if (s.teamSize == 1) {
            p.sendSystemMessage(Component.literal("Modo solo: sin equipos."));
            return;
        }
        for (String team : s.teams()) {
            String names = String.join(", ", s.members.values().stream()
                    .filter(m -> s.teamOf(m.id).equals(team)).map(m -> m.name).toList());
            var text = Component.literal(team + " [" + s.teamCount(team) + "/" + s.teamSize + "]: " + names);
            if (s.stage == MatchState.Stage.LOBBY && s.enrollment && s.teamCount(team) < s.teamSize)
                text.append(Component.literal(" [UNIRME]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tormenta equipo unirme " + team))));
            p.sendSystemMessage(text);
        }
    }

    public boolean running() { return s.stage == MatchState.Stage.RUNNING || celebrating(); }
    public boolean celebrating() { return s.stage == MatchState.Stage.CELEBRATING; }
    public boolean alive(ServerPlayer p) { return s.alive(p.getUUID()); }
    private ServerPlayer player(UUID id) { return server.getPlayerList().getPlayer(id); }
    private void dirty() { data.setDirty(); }
    private void announce(Component text) { server.getPlayerList().broadcastSystemMessage(text, false); }
    private void announce(String text) { announce(Component.literal(text)); }

    private void notRunning() {
        if (running() || storm.isActiveFor(server)) throw new IllegalStateException("Primero detén la partida.");
    }

    public void reset() {
        notRunning();
        restoreModes();
        s.members.clear();
        s.stage = MatchState.Stage.IDLE;
        s.enrollment = false;
        s.test = false;
        s.clock = 0;
        s.celebrationTicks = 0;
        watching.clear();
        cameras.clear();
        dirty();
        sync();
    }

    public void open() {
        notRunning();
        if (s.stage != MatchState.Stage.LOBBY) {
            reset();
            s.stage = MatchState.Stage.LOBBY;
        }
        ensureLobby();
        s.enrollment = true;
        dirty();
        Component button = Component.literal("[UNIRME]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tormenta unirme"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Inscribirme en el Battle Royale"))));
        announce(Component.literal("¡Inscripciones abiertas! ").append(button));
    }

    public void closeEnrollment() {
        if (s.stage != MatchState.Stage.LOBBY) throw new IllegalStateException("No hay inscripciones abiertas.");
        s.enrollment = false;
        dirty();
        announce("Inscripciones cerradas.");
    }

    public void join(ServerPlayer p, boolean admin) {
        if (s.stage != MatchState.Stage.LOBBY) throw new IllegalStateException("Primero abre las inscripciones.");
        boolean old = s.enrollment;
        try {
            if (admin) s.enrollment = true;
            s.join(p.getUUID(), p.getGameProfile().getName());
        } finally {
            s.enrollment = old;
        }

        MatchState.Destination lobby = ensureLobby();
        teleportToDestination(p, lobby);
        p.setDeltaMovement(0, 0, 0);
        p.fallDistance = 0;
        dirty();
        p.sendSystemMessage(Component.literal("Estás inscrito en el Battle Royale."
                + (s.teamSize > 1 ? " Equipo: " + s.teamOf(p.getUUID()) + ". /tormenta equipo listar" : "")));
    }

    public void remove(UUID id) {
        if (s.stage != MatchState.Stage.LOBBY)
            throw new IllegalStateException("Solo puedes retirar inscritos antes de iniciar.");
        if (s.members.remove(id) == null) throw new IllegalStateException("Ese jugador no está inscrito.");
        dirty();
    }

    public Collection<String> names() {
        return s.members.values().stream().map(m -> m.name).toList();
    }

    public void removeByName(String name) {
        UUID id = s.members.values().stream().filter(m -> m.name.equalsIgnoreCase(name)).map(m -> m.id)
                .findFirst().orElseThrow(() -> new IllegalStateException("Participante no encontrado."));
        remove(id);
    }

    public String roster() {
        StringBuilder b = new StringBuilder("Partida: " + switch (s.stage) {
            case IDLE -> "Sin preparar";
            case LOBBY -> "Inscripciones";
            case RUNNING -> "En curso";
            case CELEBRATING -> "Celebración";
            case FINISHED -> "Finalizada";
        } + (s.test ? " (PRUEBA)" : ""));
        s.members.values().forEach(m -> b.append("\n").append(m.name).append(" — ")
                .append(switch (m.status) {
                    case REGISTERED -> "Inscrito";
                    case ALIVE -> m.downed ? "Derribado" : "Vivo";
                    case ELIMINATED -> "Eliminado";
                }).append(" — kills: ").append(m.kills)
                .append(s.teamSize > 1 ? " — " + s.teamOf(m.id) : ""));
        return b.toString();
    }

    public void start(boolean test) {
        notRunning();
        s.validateStart(test);
        Map<UUID, MatchState.Destination> arrivals = s.spawnAssignments(new Random());

        for (MatchState.Destination point : s.spawns.values()) {
            if (!point.dimension.equals("minecraft:overworld") || !storm.insideInitial(point.x, point.z)
                    || !Double.isFinite(point.y) || point.y < server.overworld().getMinBuildHeight()
                    || point.y >= server.overworld().getMaxBuildHeight())
                throw new IllegalStateException("Todos los spawns deben estar dentro del borde preparado y de la altura del mundo.");
        }

        for (MatchState.Member m : s.members.values()) {
            ServerPlayer p = player(m.id);
            if (p == null || !p.isAlive() || (arrivals.isEmpty()
                    && (!p.level().dimension().equals(Level.OVERWORLD) || !storm.insideInitial(p))))
                throw new IllegalStateException(m.name + " debe estar conectado, vivo y dentro del borde inicial.");
            if (!p.connection.hasChannel(MatchPayload.TYPE) || !p.connection.hasChannel(MatchControl.TYPE)
                    || !p.connection.hasChannel(TeamPayload.TYPE) || !p.connection.hasChannel(RevivePayload.TYPE))
                throw new IllegalStateException(m.name + " necesita la versión actual de CraftersStorm en el cliente.");
        }

        ensureLobby();
        storm.start();
        for (MatchState.Member m : s.members.values())
            m.originalMode = player(m.id).gameMode.getGameModeForPlayer().getId();

        s.start(test);
        for (MatchState.Member m : s.members.values()) {
            ServerPlayer p = player(m.id);
            p.setCamera(p);
            p.stopRiding();
            MatchState.Destination arrival = arrivals.get(m.id);
            if (arrival != null) {
                p.teleportTo(server.overworld(), arrival.x, arrival.y, arrival.z, Set.of(), arrival.yaw, arrival.pitch);
                m.deploymentX = arrival.x;
                m.deploymentY = arrival.y;
                m.deploymentZ = arrival.z;
                m.deploymentProtected = true;
                m.deploymentFallProtected = true;
            }
            p.setDeltaMovement(0, 0, 0);
            p.fallDistance = 0;
            p.setGameMode(GameType.SURVIVAL);
        }

        dirty();
        sync();
        announce(test ? "Partida de prueba iniciada (sin ganador automático)." : "¡Battle Royale iniciado!");
    }

    public void setLobby(ServerPlayer p) {
        notRunning();
        MatchState.Destination d = new MatchState.Destination();
        d.dimension = p.level().dimension().location().toString();
        d.x = p.getX();
        d.y = p.getY();
        d.z = p.getZ();
        d.yaw = p.getYRot();
        d.pitch = p.getXRot();
        s.lobby = d;
        dirty();
        p.sendSystemMessage(Component.literal("Lobby guardado en tu posición actual."));
    }

    private MatchState.Destination ensureLobby() {
        if (s.lobby == null) {
            var spawn = server.overworld().getSharedSpawnPos();
            s.lobby = new MatchState.Destination();
            s.lobby.x = spawn.getX() + 0.5;
            s.lobby.y = spawn.getY();
            s.lobby.z = spawn.getZ() + 0.5;
            s.lobby.yaw = server.overworld().getSharedSpawnAngle();
            dirty();
        }
        return s.lobby;
    }

    private boolean teleportToDestination(ServerPlayer p, MatchState.Destination d) {
        if (d == null) return false;
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(d.dimension)));
        if (level == null) return false;
        p.setCamera(p);
        p.stopRiding();
        p.teleportTo(level, d.x, d.y, d.z, Set.of(), d.yaw, d.pitch);
        p.fallDistance = 0;
        p.clearFire();
        return true;
    }

    private void returnToLobby(ServerPlayer p) {
        MatchState.Destination d = s.pendingReturn.get(p.getUUID());
        if (d == null) return;
        if (!teleportToDestination(p, d)) return;
        s.pendingReturn.remove(p.getUUID());
        dirty();
    }

    public void onStormStopped() {
        if (running()) s.stage = MatchState.Stage.FINISHED;
        s.celebrationTicks = 0;
        restoreModes();
        dirty();
        sync();
    }

    private void restoreModes() {
        for (MatchState.Member m : s.members.values()) {
            if (m.originalMode >= 0) {
                s.pendingRestore.put(m.id, m.originalMode);
                m.originalMode = -1;
            }
            m.selfReviveHolding = false;
            m.selfReviveTicks = 0;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) restore(p);
        watching.clear();
        cameras.clear();
        lastControl.clear();
        lastCamera.clear();
    }

    private void restore(ServerPlayer p) {
        Integer mode = s.pendingRestore.remove(p.getUUID());
        if (mode != null) {
            p.setCamera(p);
            p.setGameMode(GameType.byId(mode));
            dirty();
        }
    }

    public void login(ServerPlayer p) {
        restore(p);
        returnToLobby(p);
        MatchState.Member m = s.members.get(p.getUUID());
        if (running() && m != null) {
            m.name = p.getGameProfile().getName();
            m.selfReviveHolding = false;
            m.selfReviveTicks = 0;
            if (m.status == MatchState.Status.ELIMINATED) makeSpectator(p);
            else if (m.status == MatchState.Status.ALIVE) p.setGameMode(GameType.SURVIVAL);
            dirty();
        }
        sync();
    }

    public void logout(ServerPlayer p) {
        cameras.remove(p.getUUID());
        watching.remove(p.getUUID());
        lastControl.remove(p.getUUID());
        lastCamera.remove(p.getUUID());
        MatchState.Member m = s.members.get(p.getUUID());
        if (m != null) {
            m.selfReviveHolding = false;
            m.selfReviveTicks = 0;
        }
        dirty();
    }

    public void interact(PlayerInteractEvent.EntityInteract event) {
        if (!running() || !(event.getEntity() instanceof ServerPlayer helper)
                || !(event.getTarget() instanceof ServerPlayer target)) return;
        if (!alive(target) || !PlayerReviveCompat.isBleeding(target)) return;

        boolean allowed = s.teamSize > 1
                && alive(helper)
                && !PlayerReviveCompat.isBleeding(helper)
                && s.teammates(helper.getUUID(), target.getUUID());
        if (!allowed) event.setCanceled(true);
    }

    public void incoming(LivingIncomingDamageEvent event) {
        if (!running() || !(event.getEntity() instanceof ServerPlayer victim)) return;
        MatchState.Member victimMember = s.members.get(victim.getUUID());

        if (celebrating() && victimMember != null) {
            event.setCanceled(true);
            return;
        }
        if (alive(victim) && storm.paused()) {
            event.setCanceled(true);
            return;
        }

        if (victimMember != null && victimMember.status == MatchState.Status.ALIVE) {
            updateDeployment(victimMember, victim);
            if (victimMember.deploymentProtected) {
                event.setCanceled(true);
                return;
            }
            if (victimMember.deploymentFallProtected && event.getSource().is(DamageTypes.FALL)) {
                event.setCanceled(true);
                victim.fallDistance = 0;
                return;
            }
        }

        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            MatchState.Member attackerMember = s.members.get(attacker.getUUID());
            if (attackerMember != null && attackerMember.status == MatchState.Status.ALIVE) {
                updateDeployment(attackerMember, attacker);
                if (attackerMember.deploymentProtected || PlayerReviveCompat.isBleeding(attacker)) {
                    event.setCanceled(true);
                    return;
                }
            }

            if (s.teammates(victim.getUUID(), attacker.getUUID())) {
                event.setCanceled(true);
                return;
            }
            if ((alive(victim) || alive(attacker)) && (!alive(victim) || !alive(attacker))) {
                event.setCanceled(true);
            }
        }
    }

    public void damaged(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || !(event.getEntity() instanceof ServerPlayer victim) || !alive(victim)) return;

        MatchState.Member victimMember = s.members.get(victim.getUUID());
        if (victimMember != null && PlayerReviveCompat.isBleeding(victim)) {
            victimMember.selfReviveTicks = 0;
        }

        if (event.getSource().getEntity() instanceof ServerPlayer killer && alive(killer)
                && killer != victim && !s.teammates(killer.getUUID(), victim.getUUID())) {
            victimMember.lastAttacker = killer.getUUID();
            victimMember.lastHit = s.clock;
        }
        dirty();
    }

    public void death(LivingDeathEvent event) {
        // PlayerRevive cancels the first lethal event to create the downed state.
        // Never interpret that canceled event as an elimination.
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim) || !alive(victim)) return;

        MatchState.Member member = s.members.get(victim.getUUID());
        UUID killer = null;
        if (event.getSource().getEntity() instanceof ServerPlayer p && p != victim
                && s.members.containsKey(p.getUUID()) && !s.teammates(p.getUUID(), victim.getUUID())) {
            killer = p.getUUID();
        }
        if (killer == null && member.downedBy != null && s.members.containsKey(member.downedBy)
                && !s.teammates(member.downedBy, victim.getUUID())) {
            killer = member.downedBy;
        }
        if (killer == null) killer = s.recentKiller(victim.getUUID());

        if (!s.eliminate(victim.getUUID(), killer)) return;

        // Prevent vanilla death/respawn from duplicating the match elimination.
        event.setCanceled(true);
        ((DeathLootInvoker) victim).craftersstorm$dropDeathLoot(victim.serverLevel(), event.getSource());
        if (!victim.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            victim.experienceLevel = 0;
            victim.experienceProgress = 0;
            victim.totalExperience = 0;
        }
        victim.setHealth(victim.getMaxHealth());
        victim.clearFire();
        victim.fallDistance = 0;
        makeSpectator(victim);

        String name = member.name;
        if (killer != null && s.canSpectate(victim.getUUID(), killer)
                && targets().stream().anyMatch(p -> p.getUUID().equals(killer))) {
            watching.put(victim.getUUID(), killer);
        }
        follow(victim, 0);

        if (killer != null && s.members.containsKey(killer)) {
            announce(Component.empty()
                    .append(Component.literal(s.members.get(killer).name).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" eliminó a ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(name).withStyle(ChatFormatting.RED)));
        } else if (event.getSource().is(DamageTypes.OUTSIDE_BORDER)) {
            announce(Component.literal(name + " murió en la tormenta").withStyle(ChatFormatting.RED));
        } else if (event.getSource().is(DamageTypes.FALL)) {
            announce(Component.literal(name + " murió por caída desde muy alto.").withStyle(ChatFormatting.RED));
        } else {
            announce(name + " murió");
        }

        dirty();
        sync();
    }

    private void makeSpectator(ServerPlayer p) {
        p.stopRiding();
        p.setGameMode(GameType.SPECTATOR);
        p.sendSystemMessage(Component.literal(s.teamSize == 1
                ? "Eliminado. Usa ← y → para cambiar de jugador."
                : "Eliminado. Usa ← y → para observar a tus compañeros vivos."));
    }

    public void control(ServerPlayer p, MatchControl message) {
        if (!running()) return;

        if (message.action() == 2 || message.action() == 3) {
            MatchState.Member m = s.members.get(p.getUUID());
            if (m == null || m.status != MatchState.Status.ALIVE) return;

            if (message.action() == 3) {
                m.selfReviveHolding = false;
                m.selfReviveTicks = 0;
                dirty();
                return;
            }

            if (s.teamSize == 1 && !m.selfReviveUsed && PlayerReviveCompat.isBleeding(p)) {
                m.selfReviveHolding = true;
                dirty();
            }
            return;
        }

        if (message.action() == 0) {
            if ((!alive(p) && !(celebrating() && s.members.containsKey(p.getUUID())
                    && s.members.get(p.getUUID()).status == MatchState.Status.ALIVE))
                    || message.perspective() < 0 || message.perspective() > 2
                    || message.fov() < 30 || message.fov() > 110) return;
            if (ticks - lastCamera.getOrDefault(p.getUUID(), -100L) < 2) return;
            lastCamera.put(p.getUUID(), ticks);
            cameras.put(p.getUUID(), new Camera(message.perspective(), message.fov()));
        } else {
            MatchState.Member m = s.members.get(p.getUUID());
            if (m == null || m.status != MatchState.Status.ELIMINATED || Math.abs((long) message.action()) != 1)
                return;
            if (ticks - lastControl.getOrDefault(p.getUUID(), -100L) < 4) return;
            lastControl.put(p.getUUID(), ticks);
            follow(p, message.action());
            sync();
        }
    }

    private List<ServerPlayer> targets() {
        return s.members.values().stream().filter(m -> m.status == MatchState.Status.ALIVE)
                .map(m -> player(m.id)).filter(Objects::nonNull).filter(ServerPlayer::isAlive)
                .filter(p -> p.level().dimension().equals(Level.OVERWORLD)).toList();
    }

    private void follow(ServerPlayer spectator, int step) {
        List<ServerPlayer> list = targets().stream()
                .filter(p -> s.canSpectate(spectator.getUUID(), p.getUUID())).toList();
        if (list.isEmpty()) {
            spectator.setCamera(spectator);
            watching.remove(spectator.getUUID());
            return;
        }
        UUID previous = watching.get(spectator.getUUID());
        int index = -1;
        for (int i = 0; i < list.size(); i++)
            if (list.get(i).getUUID().equals(previous)) index = i;
        if (index < 0) index = step < 0 ? list.size() - 1 : 0;
        else if (step != 0) index = Math.floorMod(index + step, list.size());
        ServerPlayer target = list.get(index);
        if (spectator.level() != target.level())
            spectator.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), Set.of(),
                    target.getYRot(), target.getXRot());
        spectator.setCamera(target);
        watching.put(spectator.getUUID(), target.getUUID());
    }

    private void updateDeployment(MatchState.Member m, ServerPlayer p) {
        if (!m.deploymentProtected && !m.deploymentFallProtected) return;

        if (!p.level().dimension().equals(Level.OVERWORLD)) {
            m.deploymentProtected = false;
            m.deploymentFallProtected = false;
            return;
        }

        if (m.deploymentProtected) {
            boolean leftArea = Math.abs(p.getX() - m.deploymentX) > DEPLOYMENT_HALF_SIZE
                    || Math.abs(p.getZ() - m.deploymentZ) > DEPLOYMENT_HALF_SIZE
                    || p.getY() < m.deploymentY - DEPLOYMENT_DROP_EXIT;
            if (leftArea) m.deploymentProtected = false;
        }

        // Preserve only first-fall protection after leaving the aerial platform.
        // If the player simply walks out onto solid ground, it is removed immediately.
        if (m.deploymentFallProtected && !m.deploymentProtected && p.onGround()) {
            m.deploymentFallProtected = false;
            p.fallDistance = 0;
        }
    }

    private void updateReviveState(MatchState.Member m, ServerPlayer p) {
        boolean bleeding = PlayerReviveCompat.isBleeding(p);

        if (bleeding) {
            if (!m.downed) {
                m.downed = true;
                m.downedBy = s.recentKiller(m.id);
                m.selfReviveHolding = false;
                m.selfReviveTicks = 0;
            }

            if (s.teamSize == 1 && !m.selfReviveUsed && m.selfReviveHolding) {
                m.selfReviveTicks++;
                if (m.selfReviveTicks >= MatchState.SELF_REVIVE_TICKS) {
                    m.selfReviveHolding = false;
                    m.selfReviveTicks = 0;
                    if (PlayerReviveCompat.revive(p)) {
                        m.selfReviveUsed = true;
                        m.downed = false;
                        m.downedBy = null;
                        p.sendSystemMessage(Component.literal("Auto-reanimación completada.")
                                .withStyle(ChatFormatting.GREEN));
                    }
                }
            } else if (s.teamSize != 1 || m.selfReviveUsed) {
                m.selfReviveHolding = false;
                m.selfReviveTicks = 0;
            }
        } else if (m.downed) {
            // A teammate successfully revived the player, or another compatible
            // mechanic removed PlayerRevive's bleeding state.
            m.downed = false;
            m.downedBy = null;
            m.selfReviveHolding = false;
            m.selfReviveTicks = 0;
        }
    }

    public void tick() {
        ticks++;
        if (running()) {
            if (!celebrating() && !storm.paused()) {
                s.clock++;
                for (MatchState.Member m : s.members.values()) {
                    if (m.status != MatchState.Status.ALIVE) continue;
                    ServerPlayer p = player(m.id);
                    if (p == null) {
                        m.selfReviveHolding = false;
                        m.selfReviveTicks = 0;
                        if (s.tickDisconnected(m.id)) announce(m.name + " fue eliminado por desconexión");
                    } else if (!p.level().dimension().equals(Level.OVERWORLD)) {
                        s.eliminate(m.id, null);
                        makeSpectator(p);
                        announce(m.name + " abandonó el área del evento");
                    } else if (!p.isAlive()) {
                        // Covers another mod bypassing LivingDeathEvent without granting a second life.
                        s.eliminate(m.id, m.downedBy);
                        announce(m.name + " murió");
                    } else {
                        updateDeployment(m, p);
                        updateReviveState(m, p);
                    }
                }
                dirty();
            }

            for (MatchState.Member m : s.members.values()) {
                ServerPlayer p = player(m.id);
                if (p != null && m.status == MatchState.Status.ELIMINATED && p.isAlive()) {
                    if (!p.isSpectator()) p.setGameMode(GameType.SPECTATOR);
                    follow(p, 0);
                }
            }

            // End-of-tick resolution: downed players still count as alive until final death.
            if (!celebrating() && !storm.paused() && !s.test && s.aliveTeams().size() <= 1) {
                ServerPlayer winner = targets().stream().findFirst().orElse(null);
                if (s.aliveCount() == 0 || winner != null) finish(winner);
            }
        }

        if (celebrating()) {
            if (--s.celebrationTicks <= 0) {
                for (UUID id : s.members.keySet()) s.pendingReturn.put(id, s.lobby);
                storm.stop();
                for (ServerPlayer p : server.getPlayerList().getPlayers()) returnToLobby(p);
            }
            dirty();
        }

        if (ticks % 5 == 0) sync();
    }

    private void finish(ServerPlayer winner) {
        s.stage = MatchState.Stage.CELEBRATING;
        s.celebrationTicks = 241;
        storm.freezeForVictory();
        Component title = Component.literal(winner == null ? "SIN GANADOR" : "Victoria Royale")
                .withStyle(ChatFormatting.GOLD);
        String winners = winner == null ? "" : String.join(", ", s.members.values().stream()
                .filter(m -> s.teamOf(m.id).equals(s.teamOf(winner.getUUID()))).map(m -> m.name).toList());
        String winnerLabel = winner == null ? "" : s.teamSize == 1
                ? winner.getGameProfile().getName() : "Equipo " + s.teamOf(winner.getUUID());
        Component subtitle = Component.literal(winner == null ? "No quedan participantes vivos" : winnerLabel);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 210, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            if (winner != null)
                p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        announce(winner == null ? "La partida terminó sin ganador."
                : winnerLabel + " ganó el Battle Royale." + (s.teamSize > 1 ? " " + winners : ""));
        dirty();
        sync();
    }

    public void sync() {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            MatchState.Member m = s.members.get(p.getUUID());

            if (p.connection.hasChannel(TeamPayload.TYPE)) {
                List<TeamPayload.Teammate> mates = new ArrayList<>();
                if (running() && m != null && s.teamSize > 1) {
                    for (MatchState.Member other : s.members.values()) {
                        ServerPlayer teammate = player(other.id);
                        if (other.status == MatchState.Status.ALIVE && teammate != null && teammate.isAlive()
                                && s.teammates(p.getUUID(), other.id)) {
                            mates.add(new TeamPayload.Teammate(other.id, teammate.getX(), teammate.getY(), teammate.getZ(),
                                    teammate.level().dimension().location()));
                        }
                    }
                }
                PacketDistributor.sendToPlayer(p, new TeamPayload(running(), mates));
            }

            if (p.connection.hasChannel(RevivePayload.TYPE)) {
                boolean downed = running() && m != null && m.status == MatchState.Status.ALIVE
                        && PlayerReviveCompat.isBleeding(p);
                boolean selfAvailable = downed && s.teamSize == 1 && !m.selfReviveUsed;
                int progress = selfAvailable
                        ? Math.min(100, (m.selfReviveTicks * 100) / MatchState.SELF_REVIVE_TICKS) : 0;
                boolean deployment = running() && m != null && m.status == MatchState.Status.ALIVE
                        && m.deploymentProtected;
                PacketDistributor.sendToPlayer(p, new RevivePayload(downed, selfAvailable, progress, deployment));
            }

            if (!p.connection.hasChannel(MatchPayload.TYPE)) continue;
            ServerPlayer target = watching.containsKey(p.getUUID()) ? player(watching.get(p.getUUID())) : null;
            if (target != null && !s.canSpectate(p.getUUID(), target.getUUID())) target = null;
            Camera camera = target == null ? new Camera(0, 70)
                    : cameras.getOrDefault(target.getUUID(), new Camera(0, 70));
            PacketDistributor.sendToPlayer(p, new MatchPayload(running(), m != null,
                    m != null && m.status == MatchState.Status.ELIMINATED, (int) s.aliveCount(), m == null ? 0 : m.kills,
                    celebrating() ? (s.celebrationTicks + 19) / 20 : storm.secondsRemaining(),
                    celebrating() ? "Victoria" : storm.timerLabel(), target == null ? -1 : target.getId(),
                    camera.perspective, camera.fov, target == null ? "" : target.getGameProfile().getName()));

            if (running() && m != null && m.status == MatchState.Status.ELIMINATED && target != null
                    && p.connection.hasChannel(SpectatorInventoryPayload.TYPE)) {
                PacketDistributor.sendToPlayer(p, SpectatorInventoryPayload.capture(target));
            }
        }
    }
}
