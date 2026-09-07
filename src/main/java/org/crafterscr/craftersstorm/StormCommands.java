package org.crafterscr.craftersstorm;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
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
                .requires(source -> source.hasPermission(2))
                .executes(context -> show(context, manager));

        root.then(literal("estado")
                .executes(context -> show(context, manager)));

        root.then(literal("preparar")
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

        root.then(literal("fase")
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

        root.then(literal("iniciar")
                .executes(context -> run(
                        context, manager, StormManager::start
                )));

        root.then(literal("pausar")
                .executes(context -> run(
                        context, manager, StormManager::pause
                )));

        root.then(literal("reanudar")
                .executes(context -> run(
                        context, manager, StormManager::resume
                )));

        root.then(literal("detener")
                .executes(context -> run(
                        context, manager, StormManager::stop
                )));

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
                () -> Component.literal(manager.status()),
                false
        );

        return 1;
    }
}