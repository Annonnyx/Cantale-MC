package fr.cantale.client.net;

import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fr.cantale.client.CantaleMod;
import fr.cantale.client.ui.menu.AbstractCantaleScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;

/** Canal {@code cantale:ui}, distinct de la carte. Le serveur reste la source de vérité. */
public final class CantaleUiNetwork {

	private static final Queue<String> incoming = new ConcurrentLinkedQueue<>();
	private static final int MAX_QUEUED = 64;
	private static int helloRetry = -1;

	private CantaleUiNetwork() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(CantaleUiPayload.ID, CantaleUiPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CantaleUiPayload.ID, CantaleUiPayload.CODEC);

		ClientPlayNetworking.registerGlobalReceiver(CantaleUiPayload.ID, (payload, context) -> {
			try {
				if (incoming.size() < MAX_QUEUED) {
					incoming.offer(new String(payload.data(), StandardCharsets.UTF_8));
				}
			} catch (Exception e) {
				CantaleMod.LOGGER.debug("[Cantale] Message cantale:ui illisible : {}", e.toString());
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			incoming.clear();
			sendHello(client);
			helloRetry = 40;
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			incoming.clear();
			helloRetry = -1;
		});
	}

	public static void tick(MinecraftClient client) {
		String json;
		while ((json = incoming.poll()) != null) {
			apply(json);
		}
		if (helloRetry > 0 && --helloRetry == 0) {
			sendHello(client);
		}
	}

	public static void sendHello(MinecraftClient client) {
		sendRaw(client, ("HELLO " + CantaleNetworkHandler.PROTOCOL_VERSION).getBytes(StandardCharsets.UTF_8));
	}

	public static void sendClick(int slot, int button, boolean shift) {
		JsonObject msg = new JsonObject();
		msg.addProperty("v", 1);
		msg.addProperty("type", "click");
		msg.addProperty("slot", slot);
		msg.addProperty("button", button);
		msg.addProperty("shift", shift);
		sendJson(msg);
	}

	public static void sendAction(String id) {
		sendAction(id, null);
	}

	public static void sendAction(String id, String text) {
		JsonObject msg = new JsonObject();
		msg.addProperty("v", 1);
		msg.addProperty("type", "click");
		msg.addProperty("action", id);
		if (text != null && !text.isBlank()) {
			msg.addProperty("text", text.length() > 32 ? text.substring(0, 32) : text);
		}
		sendJson(msg);
	}

	public static void sendClose() {
		JsonObject msg = new JsonObject();
		msg.addProperty("v", 1);
		msg.addProperty("type", "close");
		sendJson(msg);
	}

	private static void sendJson(JsonObject msg) {
		sendRaw(MinecraftClient.getInstance(), msg.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static void sendRaw(MinecraftClient client, byte[] data) {
		try {
			if (client == null || client.getNetworkHandler() == null) return;
			if (data == null || data.length == 0 || data.length > 2048) return;
			ClientPlayNetworking.send(new CantaleUiPayload(data));
		} catch (Exception e) {
			CantaleMod.LOGGER.debug("[Cantale] Envoi cantale:ui impossible : {}", e.toString());
		}
	}

	private static void apply(String json) {
		try {
			JsonObject msg = JsonParser.parseString(json).getAsJsonObject();
			String type = msg.has("type") ? msg.get("type").getAsString() : "";
			if ("screen".equals(type)) {
				AbstractCantaleScreen.show(msg);
			} else if ("close".equals(type)) {
				AbstractCantaleScreen.hide();
			}
		} catch (Exception e) {
			CantaleMod.LOGGER.debug("[Cantale] Écran ignoré : {}", e.toString());
		}
	}
}
