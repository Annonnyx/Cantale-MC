package fr.cantale.client;

import fr.cantale.client.data.ServerDataStore;
import fr.cantale.client.net.CantaleNetworkHandler;
import fr.cantale.client.render.TerrainSampler;
import fr.cantale.client.render.WaypointWorldRenderer;
import fr.cantale.client.ui.MapScreen;
import fr.cantale.client.ui.MinimapHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d'entrée du mod client Cantale.
 *
 * Fournit une vraie carte façon JourneyMap intégrée au serveur Cantale :
 *  - touche M : carte plein écran (terrain, drag souris, molette pour zoomer) ;
 *  - touche N : minimap en haut à droite ;
 *  - overlays serveur (claims colorés par relation, warps, homes, /f spawn) reçus
 *    via le canal plugin {@code cantale:map} quand le serveur exécute le plugin Cantale.
 *
 * Sans serveur Cantale (ou sans handshake), la carte affiche simplement le terrain.
 */
public final class CantaleMod implements ClientModInitializer {

	public static final String MOD_ID = "cantale";
	public static final Logger LOGGER = LoggerFactory.getLogger("Cantale");

	/** Catégorie des touches du mod (visible dans Options → Contrôles). */
	private static final KeyBinding.Category KEY_CATEGORY =
			KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

	private static KeyBinding openMapKey;
	private static KeyBinding toggleMinimapKey;

	@Override
	public void onInitializeClient() {
		openMapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.cantale.map",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				KEY_CATEGORY));
		toggleMinimapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.cantale.minimap",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_N,
				KEY_CATEGORY));

		CantaleNetworkHandler.register();
		MinimapHud.init();
		WaypointWorldRenderer.init();

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		LOGGER.info("[Cantale] Mod client initialisé — touche M : carte, touche N : minimap.");
	}

	private void onClientTick(MinecraftClient client) {
		// Consomme les messages réseau reçus + retry du handshake (thread client uniquement)
		CantaleNetworkHandler.tick(client);
		// Rafraîchissement doux du cache terrain autour du joueur
		TerrainSampler.INSTANCE.tick(client);

		while (openMapKey.wasPressed()) {
			if (client.currentScreen instanceof MapScreen) {
				client.setScreen(null);
			} else if (client.player != null && client.world != null) {
				client.setScreen(new MapScreen());
			}
		}
		while (toggleMinimapKey.wasPressed()) {
			MinimapHud.toggle();
		}
	}

	/**
	 * Nom du monde serveur correspondant à la dimension actuellement affichée
	 * (mapping reçu dans le {@code welcome}, défauts Bukkit usuels sinon) :
	 * utilisé pour filtrer claims et marqueurs.
	 */
	public static String getServerWorldName() {
		return ServerDataStore.currentServerWorld(MinecraftClient.getInstance().world);
	}
}
