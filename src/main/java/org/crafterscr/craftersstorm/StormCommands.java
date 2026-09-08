package org.crafterscr.craftersstorm;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class StormCommands {
    private static final DynamicCommandExceptionType ERROR =
            new DynamicCommandExceptionType(
                    message -> Component.literal(String.valueOf(message))
            );

    private StormCommands() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            Supplier<StormManager> manager
    ) {
        var root = literal("tormenta")
                .executes(c -> { c.getSource().sendSuccess(() -> Component.literal("/tormenta unirme | salir"), false); return 1; });

        root.then(literal("estado").requires(source -> source.hasPermission(2))
                .executes(context -> show(context, manager)));

        root.then(literal("preparar").requires(source -> source.hasPermission(2))
                .then(argument("cierres", IntegerArgumentType.integer(1, 20))
                        .then(argument("lado_final", DoubleArgumentType.doubleArg(2))
                                .executes(context -> run(
                                        context,
                                        manager,
                                        m -> m.prepare(
                                                IntegerArgumentType.getInteger(
                                                        context, "cierres"
                                                ),
                                                DoubleArgumentType.getDouble(
                                                        context, "lado_final"
                                                )
                                        )
                                )))));

        root.then(literal("fase").requires(source -> source.hasPermission(2))
                .then(argument("numero", IntegerArgumentType.integer(1, 20))
                        .then(argument("lado", DoubleArgumentType.doubleArg(2))
                                .then(argument(
                                        "espera",
                                        IntegerArgumentType.integer(0, 7200)
                                ).then(argument(
                                        "cierre",
                                        IntegerArgumentType.integer(1, 7200)
                                ).then(argument(
                                        "dano",
                                        FloatArgumentType.floatArg(0, 100)
                                ).executes(context -> run(
                                        context,
                                        manager,
                                        m -> m.editPhase(
                                                IntegerArgumentType.getInteger(
                                                        context, "numero"
                                                ),
                                                DoubleArgumentType.getDouble(
                                                        context, "lado"
                                                ),
                                                IntegerArgumentType.getInteger(
                                                        context, "espera"
                                                ),
                                                IntegerArgumentType.getInteger(
                                                        context, "cierre"
                                                ),
                                                FloatArgumentType.getFloat(
                                                        context, "dano"
                                                )
                                        )
                                ))))))));

        root.then(literal("iniciar").requires(source -> source.hasPermission(2))
                .executes(context -> run(
                        context, manager, m -> CraftersStorm.match().start(false)
                )).then(literal("prueba").executes(context -> run(context, manager,
                        m -> CraftersStorm.match().start(true)))));

        root.then(literal("pausar").requires(source -> source.hasPermission(2))
                .executes(context -> run(
                        context, manager, StormManager::pause
                )));

        root.then(literal("reanudar").requires(source -> source.hasPermission(2))
                .executes(context -> run(
                        context, manager, StormManager::resume
                )));

        root.then(literal("detener").requires(source -> source.hasPermission(2))
                .executes(context -> run(
                        context, manager, StormManager::stop
                )));

        root.then(literal("lobby").requires(source -> source.hasPermission(2)).executes(c -> {
            var player = c.getSource().getPlayerOrException();
            return run(c, manager, m -> CraftersStorm.match().setLobby(player));
        }));
        root.then(literal("abrir").requires(source -> source.hasPermission(2))
                .executes(c -> run(c, manager, m -> CraftersStorm.match().open())));
        root.then(literal("cerrar").requires(source -> source.hasPermission(2))
                .executes(c -> run(c, manager, m -> CraftersStorm.match().closeEnrollment())));
        root.then(literal("unirme").executes(c -> {
            var player = c.getSource().getPlayerOrException();
            return run(c, manager, m -> CraftersStorm.match().join(player, false));
        }));
        root.then(literal("salir").executes(c -> {
            var player = c.getSource().getPlayerOrException();
            return run(c, manager, m -> CraftersStorm.match().remove(player.getUUID()));
        }));
        root.then(literal("participantes").requires(source -> source.hasPermission(2))
                .then(literal("listar").executes(c -> {
                    requireManager(manager);
                    c.getSource().sendSuccess(() -> Component.literal(CraftersStorm.match().roster()), false);
                    return 1;
                }))
                .then(literal("agregar").then(argument("jugador", EntityArgument.player()).executes(c -> {
                    var player = EntityArgument.getPlayer(c, "jugador");
                    return run(c, manager, m -> CraftersStorm.match().join(player, true));
                })))
                .then(literal("quitar").then(argument("jugador", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c,b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(CraftersStorm.match().names(), b))
                        .executes(c -> run(c, manager, m -> CraftersStorm.match().removeByName(
                                com.mojang.brigadier.arguments.StringArgumentType.getString(c, "jugador")))))));
        MatchSetupCommands.attach(root);
        dispatcher.register(root);
    }

    private static StormManager requireManager(
            Supplier<StormManager> supplier
    ) throws CommandSyntaxException {
        StormManager manager = supplier.get();

        if (manager == null) {
            throw ERROR.create("El servidor aun no esta listo.");
        }

        return manager;
    }

    private static int show(
            CommandContext<CommandSourceStack> context,
            Supplier<StormManager> supplier
    ) throws CommandSyntaxException {
        StormManager manager = requireManager(supplier);

        context.getSource().sendSuccess(
                () -> Component.literal(manager.status()),
                false
        );

        return 1;
    }

    private static int run(
            CommandContext<CommandSourceStack> context,
            Supplier<StormManager> supplier,
            Consumer<StormManager> action
    ) throws CommandSyntaxException {
        StormManager manager = requireManager(supplier);

        try {
            action.accept(manager);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw ERROR.create(exception.getMessage());
        }

        context.getSource().sendSuccess(
                () -> Component.literal("Operación completada."),
                false
        );

        return 1;
    }
}