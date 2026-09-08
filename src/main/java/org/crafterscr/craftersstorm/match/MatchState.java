package org.crafterscr.craftersstorm.match;

import java.util.*;

/** Server-owned, persisted match rules; independent of rendering and Minecraft. */
public final class MatchState {
    public enum Stage { IDLE, LOBBY, RUNNING, CELEBRATING, FINISHED }
    public enum Status { REGISTERED, ALIVE, ELIMINATED }
    public static final int RECONNECT_TICKS = 60 * 20;
    public Stage stage = Stage.IDLE;
    public int teamSize = 1;
    public Map<String, Destination> spawns = new LinkedHashMap<>();
    public boolean enrollment;
    public boolean test;
    public long clock;
    public int celebrationTicks;
    public Destination lobby;
    public Map<UUID, Destination> pendingReturn = new LinkedHashMap<>();
    public static final class Destination {
        public String dimension = "minecraft:overworld";
        public double x, y, z;
        public float yaw, pitch;
    }
    public Map<UUID, Member> members = new LinkedHashMap<>();
    public Map<UUID, Integer> pendingRestore = new LinkedHashMap<>();

    public static final class Member {
        public UUID id;
        public String name;
        public Status status = Status.REGISTERED;
        public String team = "";
        public int kills;
        public int originalMode = -1;
        public int reconnectTicks = RECONNECT_TICKS;
        public UUID lastAttacker;
        public long lastHit = -1000;
        public Member() {}
        public Member(UUID id, String name) { this.id = id; this.name = name; }
    }

    public void join(UUID id, String name) {
        if (stage != Stage.LOBBY || !enrollment) throw new IllegalStateException("Las inscripciones están cerradas.");
        if (members.containsKey(id)) throw new IllegalStateException("Ese jugador ya está inscrito.");
        Member member = new Member(id, name);
        member.team = teamSize == 1 ? id.toString() : availableTeam();
        members.put(id, member);
    }
    public Set<String> teams() {
        Set<String> result = new LinkedHashSet<>();
        for (Member m : members.values()) result.add(teamOf(m.id));
        return result;
    }
    public String teamOf(UUID id) {
        Member m = members.get(id);
        return teamSize == 1 || m == null || m.team == null || m.team.isEmpty() ? id.toString() : m.team;
    }
    public boolean teammates(UUID a, UUID b) {
        return !a.equals(b) && members.containsKey(a) && members.containsKey(b) && teamOf(a).equals(teamOf(b));
    }
    public long teamCount(String team) { return members.values().stream().filter(m -> teamOf(m.id).equals(team)).count(); }
    private String availableTeam() {
        for (String team : teams()) if (teamCount(team) < teamSize) return team;
        int i = 1; while (teams().contains("equipo" + i)) i++;
        return "equipo" + i;
    }
    public void changeTeam(UUID id, String team, boolean create) {
        if (stage != Stage.LOBBY) throw new IllegalStateException("Los equipos se cambian antes de iniciar.");
        if (teamSize == 1) throw new IllegalStateException("El modo solo no tiene equipos.");
        Member m = members.get(id);
        if (m == null) throw new IllegalStateException("Primero inscríbete con /tormenta unirme.");
        if (!team.matches("[a-z0-9_-]{1,16}")) throw new IllegalArgumentException("Usa de 1 a 16 letras minúsculas, números, _ o -.");
        if (create && teams().contains(team)) throw new IllegalStateException("Ese equipo ya existe.");
        if (!create && !teams().contains(team)) throw new IllegalStateException("Ese equipo no existe.");
        if (teamOf(id).equals(team)) return;
        if (teamCount(team) >= teamSize) throw new IllegalStateException("Ese equipo está lleno.");
        m.team = team;
    }
    public boolean canSpectate(UUID viewer, UUID target) {
        Member v = members.get(viewer), t = members.get(target);
        return (stage == Stage.RUNNING || stage == Stage.CELEBRATING)
                && v != null && v.status == Status.ELIMINATED
                && t != null && t.status == Status.ALIVE && !viewer.equals(target)
                && (teamSize == 1 || teammates(viewer, target));
    }
    public Set<String> aliveTeams() {
        Set<String> result = new LinkedHashSet<>();
        for (Member m : members.values()) if (m.status == Status.ALIVE) result.add(teamOf(m.id));
        return result;
    }
    public void validateStart(boolean test) {
        if (stage != Stage.LOBBY || members.size() < (test ? 1 : 2))
            throw new IllegalStateException("Abre inscripciones y registra al menos " + (test ? 1 : 2) + " jugadores.");
        if (!test && teams().size() < 2) throw new IllegalStateException("Se necesitan al menos dos equipos distintos.");
        for (String team : teams()) if (teamCount(team) > teamSize) throw new IllegalStateException("El equipo " + team + " supera el límite.");
    }
    /** Shuffled round-robin by team: teammates stay together and spawn loads differ by at most one team. */
    public Map<UUID, Destination> spawnAssignments(Random random) {
        Map<UUID, Destination> result = new LinkedHashMap<>();
        if (spawns.isEmpty()) return result;
        List<String> groups = new ArrayList<>(teams()); Collections.shuffle(groups, random);
        List<Destination> points = new ArrayList<>(spawns.values()); Collections.shuffle(points, random);
        for (int i = 0; i < groups.size(); i++) {
            String group = groups.get(i); Destination point = points.get(i % points.size());
            for (Member m : members.values()) if (teamOf(m.id).equals(group)) result.put(m.id, point);
        }
        return result;
    }
    public boolean alive(UUID id) {
        Member m = members.get(id);
        return stage == Stage.RUNNING && m != null && m.status == Status.ALIVE;
    }
    public long aliveCount() { return members.values().stream().filter(m -> m.status == Status.ALIVE).count(); }
    public void start(boolean test) {
        validateStart(test);
        this.test = test; stage = Stage.RUNNING; enrollment = false; clock = 0;
        for (Member m : members.values()) {
            m.status = Status.ALIVE; m.kills = 0; m.reconnectTicks = RECONNECT_TICKS;
            m.lastAttacker = null; m.lastHit = -1000;
        }
    }
    public boolean eliminate(UUID victim, UUID killer) {
        if (!alive(victim)) return false;
        Member m = members.get(victim);
        // A kill belongs only to another still-competing participant.
        if (killer != null && !killer.equals(victim) && !teammates(killer, victim) && alive(killer)) members.get(killer).kills++;
        m.status = Status.ELIMINATED;
        return true;
    }
    public UUID recentKiller(UUID victim) {
        Member m = members.get(victim);
        return m != null && m.lastAttacker != null && clock - m.lastHit <= 200
                && !teammates(victim, m.lastAttacker) && alive(m.lastAttacker) ? m.lastAttacker : null;
    }
    public boolean tickDisconnected(UUID id) {
        if (!alive(id)) return false;
        Member m = members.get(id);
        if (--m.reconnectTicks <= 0) return eliminate(id, recentKiller(id));
        return false;
    }
}
