package dev.retrogen.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.retrogen.config.RetrogenConfig;
import dev.retrogen.runtime.RetrogenRuntime;
import dev.retrogen.runtime.RetrogenRuntime.CommandResult;
import dev.retrogen.runtime.RetrogenRuntime.RuntimeStatus;
import dev.retrogen.state.PassSummary;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RetrogenCommands {
	private static final SuggestionProvider<CommandSourceStack> PASS_SUGGESTIONS = (context, builder) -> {
		for (RetrogenConfig.Pass pass : RetrogenRuntime.configuredPasses()) {
			builder.suggest(pass.id);
		}
		return builder.buildFuture();
	};

	private RetrogenCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) ->
			dispatcher.register(
				literal("retrogen")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(RetrogenCommands::usage)
					.then(literal("status")
						.executes(RetrogenCommands::statusAll)
						.then(argument("pass", StringArgumentType.word())
							.suggests(PASS_SUGGESTIONS)
							.executes(RetrogenCommands::statusOne)))
					.then(literal("retry")
						.then(argument("pass", StringArgumentType.word())
							.suggests(PASS_SUGGESTIONS)
							.executes(RetrogenCommands::retryCurrent)
							.then(argument("chunkX", IntegerArgumentType.integer())
								.then(argument("chunkZ", IntegerArgumentType.integer())
									.executes(RetrogenCommands::retryAt)))))
					.then(literal("clear")
						.then(argument("pass", StringArgumentType.word())
							.suggests(PASS_SUGGESTIONS)
							.then(argument("chunkX", IntegerArgumentType.integer())
								.then(argument("chunkZ", IntegerArgumentType.integer())
									.then(literal("confirm")
										.executes(RetrogenCommands::clearAt))))))
			)
		);
	}

	private static int usage(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSystemMessage(Component.literal(
			"Usage: /retrogen status [pass], /retrogen retry <pass> [chunkX chunkZ], "
				+ "/retrogen clear <pass> <chunkX> <chunkZ> confirm"
		));
		return 1;
	}

	private static int statusAll(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		int count = 0;
		for (RetrogenConfig.Pass pass : RetrogenRuntime.configuredPasses()) {
			sendStatus(source, pass.id);
			count++;
		}
		if (count == 0) {
			source.sendFailure(Component.literal("Retrogen is not ready or no passes are configured."));
			return 0;
		}
		return count;
	}

	private static int statusOne(CommandContext<CommandSourceStack> context) {
		String pass = StringArgumentType.getString(context, "pass");
		if (!RetrogenRuntime.hasConfiguredPass(pass)) {
			context.getSource().sendFailure(Component.literal("Unknown Retrogen pass: " + pass));
			return 0;
		}
		sendStatus(context.getSource(), pass);
		return 1;
	}

	private static void sendStatus(CommandSourceStack source, String passId) {
		RuntimeStatus status = RetrogenRuntime.status(source.getLevel(), passId);
		PassSummary summary = status.summary();
		source.sendSystemMessage(Component.literal(
			"Retrogen " + (status.enabled() ? "enabled" : "disabled")
				+ " | dimension=" + status.dimension()
				+ " | pass=" + passId
				+ " | queued=" + status.queued()
				+ " | completed=" + summary.completed()
				+ " | failed=" + summary.failed()
				+ " | inProgress=" + summary.inProgress()
		));
	}

	private static int retryCurrent(CommandContext<CommandSourceStack> context) {
		BlockPos block = BlockPos.containing(context.getSource().getPosition());
		ChunkPos chunk = ChunkPos.containing(block);
		return retry(context, chunk.x(), chunk.z());
	}

	private static int retryAt(CommandContext<CommandSourceStack> context) {
		return retry(
			context,
			IntegerArgumentType.getInteger(context, "chunkX"),
			IntegerArgumentType.getInteger(context, "chunkZ")
		);
	}

	private static int retry(CommandContext<CommandSourceStack> context, int x, int z) {
		String pass = StringArgumentType.getString(context, "pass");
		CommandResult result = RetrogenRuntime.retry(context.getSource().getLevel(), pass, ChunkPos.pack(x, z));
		return report(context.getSource(), result, pass, x, z);
	}

	private static int clearAt(CommandContext<CommandSourceStack> context) {
		String pass = StringArgumentType.getString(context, "pass");
		int x = IntegerArgumentType.getInteger(context, "chunkX");
		int z = IntegerArgumentType.getInteger(context, "chunkZ");
		CommandResult result = RetrogenRuntime.clear(context.getSource().getLevel(), pass, ChunkPos.pack(x, z));
		return report(context.getSource(), result, pass, x, z);
	}

	private static int report(CommandSourceStack source, CommandResult result, String pass, int x, int z) {
		String target = "pass=" + pass + ", chunk=" + x + "," + z;
		return switch (result) {
			case QUEUED -> success(source, "Queued Retrogen retry for " + target);
			case CLEARED -> success(source, "Cleared Retrogen state for " + target);
			case CLEARED_AND_QUEUED -> success(source, "Cleared state and queued Retrogen for " + target);
			case MOD_DISABLED -> success(source, "Cleared retry block, but Retrogen is disabled: " + target);
			case NOT_READY -> failure(source, "Retrogen server state is not ready.");
			case UNKNOWN_PASS -> failure(source, "Unknown Retrogen pass: " + pass);
				case PASS_INACTIVE -> failure(source, "Pass is disabled or does not match this dimension: " + pass);
				case ALREADY_COMPLETE -> failure(source, "Pass is already complete; use the confirmed clear command to reset it.");
				case NOTHING_TO_CLEAR -> failure(source, "No Retrogen state exists for " + target);
				case WAITING_FOR_CHUNK_LOAD -> success(source, "Cleared retry block; Retrogen will run when the chunk is next loaded: " + target);
				case CLEARED_WAITING_FOR_CHUNK_LOAD -> success(source, "Cleared state; Retrogen will run when the chunk is next loaded: " + target);
				case PERSISTENCE_FAILED -> failure(source, "Could not persist Retrogen state; no generation was queued.");
			};
	}

	private static int success(CommandSourceStack source, String message) {
		source.sendSuccess(() -> Component.literal(message), true);
		return 1;
	}

	private static int failure(CommandSourceStack source, String message) {
		source.sendFailure(Component.literal(message));
		return 0;
	}
}
