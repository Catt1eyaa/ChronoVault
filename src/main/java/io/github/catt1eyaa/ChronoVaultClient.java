/*
 * Copyright (C) 2026 Cattleya
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.catt1eyaa;

import io.github.catt1eyaa.chronovault.client.RestoreSnapshotsScreen;
import io.github.catt1eyaa.mixin.EditWorldScreenMixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = ChronoVault.MOD_ID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = ChronoVault.MOD_ID, value = Dist.CLIENT)
public class ChronoVaultClient {
    public ChronoVaultClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        NeoForge.EVENT_BUS.addListener(ChronoVaultClient::onScreenInit);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        ChronoVault.LOGGER.info("HELLO FROM CLIENT SETUP");
        ChronoVault.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof EditWorldScreen editWorldScreen)) {
            return;
        }

        if (!(editWorldScreen instanceof EditWorldScreenMixin accessor)) {
            return;
        }

        Button optimizeButton = null;
        Component optimizeMessage = Component.translatable("selectWorld.edit.optimize");
        for (GuiEventListener listener : editWorldScreen.children()) {
            if (listener instanceof Button button && button.getMessage().equals(optimizeMessage)) {
                optimizeButton = button;
                break;
            }
        }

        int buttonX = editWorldScreen.width / 2 - 100;
        int buttonY = editWorldScreen.height / 4 + 145;
        int buttonWidth = 200;
        int buttonHeight = 20;
        if (optimizeButton != null) {
            buttonX = optimizeButton.getX();
            buttonY = optimizeButton.getY() + 25;
            buttonWidth = optimizeButton.getWidth();
            buttonHeight = optimizeButton.getHeight();
        }

        Button restoreButton = Button.builder(
                Component.translatable("gui.chrono_vault.restore.button"),
                button -> Minecraft.getInstance().setScreen(
                        new RestoreSnapshotsScreen(editWorldScreen, accessor.chronovault$getLevelAccess())
                )
        ).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build();

        event.addListener(restoreButton);
    }
}
