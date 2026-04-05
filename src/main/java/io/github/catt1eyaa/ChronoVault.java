package io.github.catt1eyaa;

import io.github.catt1eyaa.chronovault.backup.AutoBackupScheduler;
import io.github.catt1eyaa.chronovault.command.ChronoVaultCommands;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.nio.file.Path;

@Mod(ChronoVault.MOD_ID)
public class ChronoVault {
    public static final String MOD_ID = "chrono_vault";
    public static final Logger LOGGER = LogUtils.getLogger();

    private ChronoVaultCommands commands;
    private AutoBackupScheduler autoBackupScheduler;

    public ChronoVault(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        commands = new ChronoVaultCommands();

        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // 命令注册（此时命令对象已存在，可以正常注册）
        commands.register(event.getDispatcher());
        LOGGER.info("ChronoVault commands registered");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        Path backupDir = Path.of(Config.getBackupPath());
        int compressionLevel = Config.getCompressionLevel();

        commands.initBackupService(backupDir, compressionLevel);
        LOGGER.info("ChronoVault backup service initialized");

        if (Config.isAutoBackupEnabled()) {
            autoBackupScheduler = new AutoBackupScheduler(
                    event.getServer(),
                    backupDir,
                    Config.getAutoBackupIntervalMinutes(),
                    compressionLevel
            );
            autoBackupScheduler.start();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (autoBackupScheduler != null) {
            autoBackupScheduler.stop();
        }
        if (commands != null) {
            commands.shutdownBackupService();
        }
    }
}
