package dev.retrogen;

import dev.retrogen.runtime.RetrogenRuntime;
import dev.retrogen.command.RetrogenCommands;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RetrogenMod implements ModInitializer {
	public static final String MOD_ID = "retrogen";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		RetrogenRuntime.registerEvents();
		RetrogenCommands.register();
		LOGGER.info("Retrogen initialized; configuration is loaded before server worlds");
	}
}
