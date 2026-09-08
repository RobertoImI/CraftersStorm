package org.crafterscr.craftersstorm;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.crafterscr.craftersstorm.match.MatchManager;
import java.util.Locale;
import java.util.function.Consumer;
import static net.minecraft.commands.Commands.*;

public final class MatchSetupCommands {
    private static final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(x -> Component.literal(String.valueOf(x)));
    private MatchSetupCommands() {}
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        var modes = literal("modo").requires(s -> s.hasPermission(2));
        String[] names = {"solo", "duos", "trios", "escuadrones"};
        for (int i = 0; i < names.length; i++) {
            final int size = i + 1;
            modes.then(literal(names[i]).executes(c -> run(c, m -> m.configureMode(size))));
        }
        root.then(modes);
        root.then(literal("spawn").requires(s -> s.hasPermission(2))
                .then(literal("agregar").then(argument("id", StringArgumentType.word()).executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    return run(c, m -> m.saveSpawn(StringArgumentType.getString(c,"id"), p));
                })))
                .then(literal("quitar").then(argument("id", StringArgumentType.word())
                        .suggests((c,b) -> SharedSuggestionProvider.suggest(CraftersStorm.match().settings().spawns.keySet(), b))
                        .executes(c -> run(c, m -> m.removeSpawn(StringArgumentType.getString(c,"id"))))))
                .then(literal("limpiar").executes(c -> run(c, MatchManager::clearSpawns)))
                .then(literal("listar").executes(c -> run(c, m -> {
                    if (m.settings().spawns.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("Sin spawns: se usarán las posiciones actuales."), false);
                    m.settings().spawns.forEach((id,d) -> c.getSource().sendSuccess(() -> Component.literal(
                            String.format(Locale.ROOT,"%s: X %.1f, Y %.1f, Z %.1f",id,d.x,d.y,d.z)), false));
                }))));
        root.then(literal("equipo")
                .then(literal("listar").executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    return run(c, m -> m.showTeams(p));
                }))
                .then(literal("crear").then(argument("nombre", StringArgumentType.word()).executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    return run(c, m -> m.changeTeam(p, StringArgumentType.getString(c,"nombre"), true));
                })))
                .then(literal("unirme").then(argument("nombre", StringArgumentType.word())
                        .suggests((c,b) -> SharedSuggestionProvider.suggest(CraftersStorm.match().settings().teams(), b))
                        .executes(c -> {
                            var p = c.getSource().getPlayerOrException();
                            return run(c, m -> m.changeTeam(p, StringArgumentType.getString(c,"nombre"), false));
                        }))));
    }
    private static int run(CommandContext<CommandSourceStack> c, Consumer<MatchManager> action) throws CommandSyntaxException {
        MatchManager m = CraftersStorm.match();
        if (m == null) throw ERROR.create("El servidor aún no está listo.");
        try { action.accept(m); }
        catch (IllegalStateException | IllegalArgumentException e) { throw ERROR.create(e.getMessage()); }
        c.getSource().sendSuccess(() -> Component.literal("Operación completada."), false);
        return 1;
    }
}
