package io.github.catt1eyaa.chronovault.client;

import io.github.catt1eyaa.ChronoVault;
import io.github.catt1eyaa.Config;
import io.github.catt1eyaa.chronovault.restore.RestoreExecutor;
import io.github.catt1eyaa.chronovault.restore.RestoreResult;
import io.github.catt1eyaa.chronovault.snapshot.Manifest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 快照恢复界面。
 */
public class RestoreSnapshotsScreen extends Screen {

    private static final int PAGE_SIZE = 8;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Screen parent;
    private final LevelStorageSource.LevelStorageAccess levelAccess;
    private final RestoreExecutor restoreExecutor;
    private final List<SnapshotView> snapshots;

    private int page;
    private Component statusMessage;

    public RestoreSnapshotsScreen(Screen parent, LevelStorageSource.LevelStorageAccess levelAccess) {
        super(Component.translatable("chrono_vault.restore.title"));
        this.parent = parent;
        this.levelAccess = levelAccess;

        Path backupDir = Minecraft.getInstance().gameDirectory.toPath().resolve(Config.getBackupPath()).normalize();
        this.restoreExecutor = new RestoreExecutor(backupDir);
        this.snapshots = loadSnapshots();
        this.page = 0;
        this.statusMessage = Component.empty();
    }

    @Override
    protected void init() {
        super.init();

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, snapshots.size());

        int y = 48;
        for (int i = start; i < end; i++) {
            SnapshotView snapshot = snapshots.get(i);
            addRenderableWidget(Button.builder(
                    Component.literal(snapshot.label()),
                    button -> confirmRestore(snapshot)
            ).bounds(this.width / 2 - 150, y, 300, 20).build());
            y += 24;
        }

        addRenderableWidget(Button.builder(
                Component.translatable("chrono_vault.restore.prev_page"),
                button -> {
                    page = Math.max(0, page - 1);
                    rebuildWidgets();
                }
        ).bounds(this.width / 2 - 150, this.height - 52, 70, 20).build()).active = page > 0;

        int maxPage = snapshots.isEmpty() ? 0 : (snapshots.size() - 1) / PAGE_SIZE;
        addRenderableWidget(Button.builder(
                Component.translatable("chrono_vault.restore.next_page"),
                button -> {
                    page = Math.min(maxPage, page + 1);
                    rebuildWidgets();
                }
        ).bounds(this.width / 2 - 74, this.height - 52, 70, 20).build()).active = page < maxPage;

        addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                button -> onClose()
        ).bounds(this.width / 2 + 80, this.height - 52, 70, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);

        int maxPage = snapshots.isEmpty() ? 0 : (snapshots.size() - 1) / PAGE_SIZE;
        String pageText = (page + 1) + " / " + (maxPage + 1);
        guiGraphics.drawCenteredString(this.font, pageText, this.width / 2, this.height - 74, 0xA0A0A0);

        if (snapshots.isEmpty()) {
            guiGraphics.drawCenteredString(
                    this.font,
                    Component.translatable("chrono_vault.restore.empty"),
                    this.width / 2,
                    this.height / 2 - 10,
                    0xE06060
            );
        }

        if (!statusMessage.getString().isEmpty()) {
            guiGraphics.drawCenteredString(this.font, statusMessage, this.width / 2, this.height - 90, 0x80FF80);
        }
    }

    private void confirmRestore(SnapshotView snapshot) {
        this.minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (!confirmed) {
                        this.minecraft.setScreen(this);
                        return;
                    }
                    runRestore(snapshot.snapshotId());
                    this.minecraft.setScreen(this);
                },
                Component.translatable("chrono_vault.restore.confirm_title"),
                Component.translatable("chrono_vault.restore.confirm_message", snapshot.snapshotId())
        ));
    }

    private void runRestore(String snapshotId) {
        try {
            Path savesDir = Minecraft.getInstance().gameDirectory.toPath().resolve("saves").normalize();
            String worldName = levelAccess.getLevelId();
            RestoreResult result = restoreExecutor.restoreToNewWorld(snapshotId, savesDir, worldName);
            this.statusMessage = Component.translatable(
                    "chrono_vault.restore.success_new_world",
                    result.targetWorldName(),
                    result.restoredFiles(),
                    result.restoredRegions(),
                    result.restoredChunks()
            );
        } catch (IOException e) {
            ChronoVault.LOGGER.error("Failed to restore snapshot {}", snapshotId, e);
            this.statusMessage = Component.translatable("chrono_vault.restore.failed", snapshotId, e.getMessage());
        }
    }

    private List<SnapshotView> loadSnapshots() {
        try {
            List<String> ids = restoreExecutor.listSnapshots();
            List<SnapshotView> views = new ArrayList<>();
            for (String id : ids) {
                Manifest manifest = restoreExecutor.loadManifest(id);
                String time = TIME_FORMATTER.format(Instant.ofEpochSecond(manifest.timestamp()).atZone(ZoneId.systemDefault()));
                String description = manifest.description() == null || manifest.description().isBlank()
                        ? "-"
                        : manifest.description();
                String label = id + " | " + time + " | " + description;
                views.add(new SnapshotView(id, label));
            }
            views.sort(Comparator.comparing(SnapshotView::snapshotId).reversed());
            return views;
        } catch (IOException e) {
            ChronoVault.LOGGER.error("Failed to load snapshots", e);
            return List.of();
        }
    }

    private record SnapshotView(String snapshotId, String label) {
    }
}
