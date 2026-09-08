package org.crafterscr.craftersstorm;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Locale;
import java.util.Random;

import static org.crafterscr.craftersstorm.StormData.Mode;


public final class StormManager {
    private final MinecraftServer server;
    private final ServerLevel level;
    private final StormData data;
    private final StormData.Values s;
    private final Random random = new Random();

    private final ServerBossEvent bar = new ServerBossEvent(
            Component.literal("Tormenta"),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS
    );

    private long ticks;

    public StormManager(MinecraftServer server) {
        this.server = server;
        this.level = server.overworld();
        this.data = StormData.get(server);
        this.s = data.values;

        if (active()) {
            validatePhases();

            if (s.phaseIndex < 0 || s.phaseIndex >= s.phases.size()) {
                throw new IllegalStateException("Indice de fase guardado invalido.");
            }

            // Una partida guardada vuelve pausada después de reiniciar.
            s.paused = true;
            disableVanillaDamage();
            applyBorder();
            data.setDirty();
        }
    }

    private WorldBorder border() {
        return level.getWorldBorder();
    }

    private boolean active() {
        return s.mode != Mode.IDLE;
    }

    private void requireIdle() {
        if (active()) {
            throw new IllegalStateException(
                    "Primero utiliza /tormenta detener."
            );
        }
    }

    private void requireActive() {
        if (!active()) {
            throw new IllegalStateException("No hay una tormenta activa.");
        }
    }

    private void requireStationaryBorder() {
        if (border().getLerpRemainingTime() > 0) {
            throw new IllegalStateException(
                    "Espera a que termine el movimiento del worldborder."
            );
        }
    }

    public void prepare(int closures, double finalSize) {
        requireIdle();
        requireStationaryBorder();

        WorldBorder b = border();
        double initialSize = b.getSize();

        if (finalSize >= initialSize) {
            throw new IllegalArgumentException(
                    "El lado final debe ser menor que el borde inicial."
            );
        }

        double limit = 29_999_984;

        if (Math.abs(b.getCenterX()) + initialSize / 2 > limit
                || Math.abs(b.getCenterZ()) + initialSize / 2 > limit) {
            throw new IllegalArgumentException(
                    "El borde inicial excede los limites del mundo."
            );
        }

        CraftersStorm.match().reset();
        s.initialX = b.getCenterX();
        s.initialZ = b.getCenterZ();
        s.initialSize = initialSize;
        s.phases.clear();

        // Reducción proporcional: alcanza exactamente el tamaño final.
        for (int i = 0; i < closures; i++) {
            double progress = (i + 1.0) / closures;
            double size = initialSize
                    * Math.pow(finalSize / initialSize, progress);

            if (i == closures - 1) {
                size = finalSize;
            }

            s.phases.add(new StormData.Phase(
                    size,
                    i == 0 ? 180 : 90,
                    90,
                    Math.min(10, i + 1)
            ));
        }

        s.configured = true;
        s.paused = false;
        data.setDirty();
    }

    public void editPhase(
            int number,
            double size,
            int waitSeconds,
            int closeSeconds,
            float damage
    ) {
        requireIdle();

        if (!s.configured || number < 1 || number > s.phases.size()) {
            throw new IllegalArgumentException(
                    "Esa fase no existe. Utiliza /tormenta preparar primero."
            );
        }

        // Se valida toda la secuencia al iniciar para permitir editar
        // varias fases sin depender del orden de edición.
        s.phases.set(number - 1, new StormData.Phase(
                size,
                waitSeconds,
                closeSeconds,
                damage
        ));

        data.setDirty();
    }

    private void validatePhases() {
        if (!s.configured || s.phases.isEmpty()) {
            throw new IllegalStateException(
                    "Primero utiliza /tormenta preparar <cierres> <lado_final>."
            );
        }

        double previous = s.initialSize;

        for (int i = 0; i < s.phases.size(); i++) {
            StormData.Phase p = s.phases.get(i);

            if (p == null
                    || !Double.isFinite(p.size())
                    || p.size() < 2
                    || p.size() >= previous
                    || p.waitSeconds() < 0
                    || p.waitSeconds() > 7200
                    || p.closeSeconds() < 1
                    || p.closeSeconds() > 7200
                    || !Float.isFinite(p.damage())
                    || p.damage() < 0
                    || p.damage() > 100) {
                throw new IllegalArgumentException(
                        "Fase " + (i + 1)
                                + " invalida. Cada zona debe ser menor que la anterior."
                );
            }

            previous = p.size();
        }
    }

    public void start() {
        requireIdle();
        requireStationaryBorder();
        validatePhases();

        WorldBorder b = border();

        s.previousX = b.getCenterX();
        s.previousZ = b.getCenterZ();
        s.previousSize = b.getSize();
        s.previousDamage = b.getDamagePerBlock();
        s.previousBuffer = b.getDamageSafeZone();

        s.x = s.initialX;
        s.z = s.initialZ;
        s.size = s.initialSize;
        s.phaseIndex = 0;
        s.paused = false;

        disableVanillaDamage();
        applyBorder();
        selectNextZone();
        data.setDirty();
        sync();
    }

    private void disableVanillaDamage() {
        // El daño lo aplica este mod una vez por segundo.
        border().setDamagePerBlock(0);
        border().setDamageSafeZone(0);
    }

    private StormData.Phase phase() {
        return s.phases.get(s.phaseIndex);
    }

    private void selectNextZone() {
        StormData.Phase p = phase();

        s.fromX = s.x;
        s.fromZ = s.z;
        s.fromSize = s.size;

        double available = (s.size - p.size()) / 2.0;

        s.targetX = s.x + (random.nextDouble() * 2 - 1) * available;
        s.targetZ = s.z + (random.nextDouble() * 2 - 1) * available;
        s.targetSize = p.size();

        s.elapsed = 0;
        s.duration = p.waitSeconds() * 20L;
        s.mode = Mode.WAITING;

        announce(String.format(
                Locale.ROOT,
                "Próxima zona: centro X %.0f, Z %.0f; lado %.1f.",
                s.targetX,
                s.targetZ,
                s.targetSize
        ));

        if (s.duration == 0) {
            beginClosing();
        }
    }

    private void beginClosing() {
        s.mode = Mode.CLOSING;
        s.elapsed = 0;
        s.duration = phase().closeSeconds() * 20L;

        announce("La tormenta comienza a cerrarse.");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.playNotifySound(net.minecraft.sounds.SoundEvents.ELDER_GUARDIAN_CURSE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.0F);
        }
    }

    public void freezeForVictory() {
        requireActive(); s.paused = true; data.setDirty(); sync();
    }

    public void pause() {
        requireActive();

        if (s.paused) {
            throw new IllegalStateException("La tormenta ya esta pausada.");
        }

        s.paused = true;
        data.setDirty();
        announce("Tormenta pausada. El daño tambien esta suspendido.");
        sync();
    }

    public void resume() {
        requireActive();
        if (CraftersStorm.match().celebrating()) throw new IllegalStateException("La partida ya terminó; espera el regreso al lobby.");

        if (!s.paused) {
            throw new IllegalStateException("La tormenta no esta pausada.");
        }

        s.paused = false;
        data.setDirty();
        announce("Tormenta reanudada.");
        sync();
    }

    public void stop() {
        requireActive();

        WorldBorder b = border();
        b.setCenter(s.previousX, s.previousZ);
        b.setSize(s.previousSize);
        b.setDamagePerBlock(s.previousDamage);
        b.setDamageSafeZone(s.previousBuffer);

        s.mode = Mode.IDLE;
        s.paused = false;
        data.setDirty();

        bar.removeAllPlayers();
        CraftersStorm.match().onStormStopped();
        announce("Tormenta detenida. Borde anterior restaurado.");
        sync();
    }

    public void tick() {
        ticks++;

        if (active() && !s.paused) {
            advance();

            if (ticks % 20 == 0) {
                damagePlayers();
            }
        }

        if (ticks % 20 == 0) {
            sync();
        }
    }

    private void advance() {
        if (s.mode == Mode.FINAL) {
            return;
        }

        s.elapsed++;

        if (s.mode == Mode.WAITING) {
            if (s.elapsed >= s.duration) {
                beginClosing();
            }
        } else if (s.mode == Mode.CLOSING) {
            double progress = Math.min(
                    1.0,
                    s.elapsed / (double) s.duration
            );

            s.x = lerp(s.fromX, s.targetX, progress);
            s.z = lerp(s.fromZ, s.targetZ, progress);
            s.size = lerp(s.fromSize, s.targetSize, progress);

            applyBorder();

            if (s.elapsed >= s.duration) {
                if (s.phaseIndex + 1 >= s.phases.size()) {
                    s.mode = Mode.FINAL;
                    s.elapsed = 0;
                    s.duration = 0;

                    announce(
                            "Zona final alcanzada. La tormenta sigue haciendo daño."
                    );
                } else {
                    s.phaseIndex++;
                    selectNextZone();
                }
            }
        }

        data.setDirty();
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    private void applyBorder() {
        // Cambian gradualmente tanto el centro como el tamaño.
        border().setCenter(s.x, s.z);
        border().setSize(s.size);
    }

    private void damagePlayers() {
        float damage = phase().damage();

        if (damage <= 0) {
            return;
        }

        double half = s.size / 2.0;

        for (ServerPlayer player : level.players()) {
            if (!CraftersStorm.match().alive(player) || !player.isAlive() || player.isCreative() || player.isSpectator()) {
                continue;
            }

            boolean outside =
                    player.getX() < s.x - half
                            || player.getX() > s.x + half
                            || player.getZ() < s.z - half
                            || player.getZ() > s.z + half;

            if (outside) {
                player.hurt(player.damageSources().outOfBorder(), damage);

                player.displayClientMessage(
                        Component.literal("¡Estas fuera de la zona segura!"),
                        true
                );
            }
        }
    }

    private String timerText() {
        long seconds = Math.max(0, (s.duration - s.elapsed + 19) / 20);

        return String.format(
                Locale.ROOT,
                "%02d:%02d",
                seconds / 60,
                seconds % 60
        );
    }

    private String title() {
        if (!active()) {
            return s.configured
                    ? "Tormenta preparada: " + s.phases.size() + " cierres."
                    : "Tormenta sin configurar.";
        }

        String state = switch (s.mode) {
            case WAITING -> "Espera " + timerText();
            case CLOSING -> "Cierre " + timerText();
            case FINAL -> "Zona final";
            default -> "";
        };

        return "Tormenta | " + state
                + (s.paused ? " | PAUSADA" : "");
    }

    private void sync() {
        bar.removeAllPlayers(); // The timer is now displayed below the minimap.

        StormPayload payload = active()
                ? new StormPayload(
                true,
                !s.paused && phase().damage() > 0,
                s.x,
                s.z,
                s.size,
                s.targetX,
                s.targetZ,
                s.targetSize
        )
                : StormPayload.EMPTY;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection.hasChannel(StormPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, new StormPayload(
                        payload.active(), payload.damaging() && CraftersStorm.match().alive(player),
                        payload.x(), payload.z(), payload.size(), payload.targetX(), payload.targetZ(), payload.targetSize()));
            }
        }
    }

    private void announce(String message) {
        Component text = Component.literal("[Tormenta] " + message);

        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(text);
        }
    }

    public String status() {
        StringBuilder result = new StringBuilder(title());

        for (int i = 0; i < s.phases.size(); i++) {
            StormData.Phase p = s.phases.get(i);

            result.append(String.format(
                    Locale.ROOT,
                    "\nFase %d: lado %.1f | espera %ds | cierre %ds | daño %.1f",
                    i + 1,
                    p.size(),
                    p.waitSeconds(),
                    p.closeSeconds(),
                    p.damage()
            ));
        }

        return result.toString();
    }

    public boolean isActiveFor(MinecraftServer candidate) {
        return server == candidate && active();
    }
    public boolean paused() { return s.paused; }
    public int secondsRemaining() {
        return active() ? (int)Math.max(0, (s.duration - s.elapsed + 19) / 20) : 0;
    }
    public String timerLabel() {
        if (!active()) return "";
        if (s.paused) return "Pausa";
        return switch (s.mode) { case WAITING -> "Espera"; case CLOSING -> "Cierre"; default -> "Final"; };
    }
    public boolean insideInitial(ServerPlayer p) {
        return insideInitial(p.getX(), p.getZ());
    }
    public boolean insideInitial(double x, double z) {
        return s.configured && Math.abs(x - s.initialX) < s.initialSize/2
                && Math.abs(z - s.initialZ) < s.initialSize/2;
    }
}