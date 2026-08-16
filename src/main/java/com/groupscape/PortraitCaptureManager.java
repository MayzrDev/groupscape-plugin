package com.groupscape;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.Player;

/**
 * Drives the character portrait mesh capture — login, equipment change, plus a periodic backstop
 * (wired by {@link GroupScapeTrackerPlugin}). Capture always happens on the client thread, since
 * every trigger it's called from (event subscribers, non-async scheduled methods) is already on
 * it — same assumption the rest of this plugin's state readers make.
 */
@Slf4j
@Singleton
public class PortraitCaptureManager {
    @Inject
    private Client client;

    @Inject
    private DataManager dataManager;

    public void onLogin() {
        capture();
    }

    public void onEquipmentSynced() {
        capture();
    }

    public void captureIfLoggedIn() {
        if (client.getGameState() == GameState.LOGGED_IN) {
            capture();
        }
    }

    private void capture() {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        Player local = client.getLocalPlayer();
        if (local == null) {
            return;
        }

        Model model = local.getModel();
        if (model == null) {
            // Momentarily unavailable right after login/teleport; the next trigger catches it.
            return;
        }

        byte[] mesh = ModelExporter.toBytes(model, client.getTextureProvider());
        log.debug("GroupScape portrait captured: {} bytes", mesh.length);
        dataManager.uploadPortrait(mesh);
    }
}
