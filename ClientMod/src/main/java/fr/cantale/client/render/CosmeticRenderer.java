package fr.cantale.client.render;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import fr.cantale.client.data.ServerDataStore;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.Vec3d;

/**
 * Affiche le titre, l'aura et la cape envoyés par le serveur. Aucun équipement n'est décidé ici.
 */
public final class CosmeticRenderer {

	private static final int EMBER = 0xFF6A1A;
	private static final int AURORA = 0x7AD7FF;
	private static final int LINEN = 0xF4EDE4;
	private static final int BANNER = 0xC9A227;
	private static final int TITLE = 0xFFFFE7A8;
	private static final float VIEW_DISTANCE = 48.0f;

	private CosmeticRenderer() {
	}

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(context -> renderTitles(context.matrices(), context.consumers()));
	}

	public static void tick(MinecraftClient client) {
		if (client.world == null || client.player == null || client.particleManager == null) {
			return;
		}
		if (!ServerDataStore.isServerSupportsMod()) {
			return;
		}
		if ((client.world.getTime() & 3L) != 0L) {
			return;
		}
		double time = client.world.getTime() * 0.35;
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (player.squaredDistanceTo(client.player) > VIEW_DISTANCE * VIEW_DISTANCE) {
				continue;
			}
			ServerDataStore.CosmeticLook look = ServerDataStore.cosmeticOf(
					player.getUuid().toString(), player.getName().getString());
			if (!look.visible()) {
				continue;
			}
			Vec3d pos = player.getLerpedPos(1.0f);
			spawnAura(client, look.aura(), pos, time + player.getId());
			spawnCape(client, look.cape(), player, pos);
		}
	}

	private static void spawnAura(MinecraftClient client, String aura, Vec3d pos, double time) {
		int color = switch (aura) {
			case "aura_ember" -> EMBER;
			case "aura_aurora" -> AURORA;
			default -> -1;
		};
		if (color < 0) {
			return;
		}
		DustParticleEffect dust = new DustParticleEffect(color, 1.0f);
		for (int i = 0; i < 3; i++) {
			double angle = time + i * Math.PI * 2.0 / 3.0;
			client.particleManager.addParticle(dust,
					pos.x + Math.cos(angle) * 0.55,
					pos.y + 1.05 + Math.sin(angle) * 0.12,
					pos.z + Math.sin(angle) * 0.55,
					0.0, 0.01, 0.0);
		}
	}

	private static void spawnCape(MinecraftClient client, String cape, AbstractClientPlayerEntity player, Vec3d pos) {
		int color = switch (cape) {
			case "cape_linen" -> LINEN;
			case "cape_banner" -> BANNER;
			default -> -1;
		};
		if (color < 0) {
			return;
		}
		double yaw = Math.toRadians(player.getBodyYaw());
		double x = pos.x + Math.sin(yaw) * 0.35;
		double z = pos.z - Math.cos(yaw) * 0.35;
		client.particleManager.addParticle(new DustParticleEffect(color, 0.85f),
				x, pos.y + 1.15, z, 0.0, -0.01, 0.0);
	}

	private static void renderTitles(MatrixStack matrices, VertexConsumerProvider consumers) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || matrices == null || consumers == null) {
			return;
		}
		if (!ServerDataStore.isServerSupportsMod()) {
			return;
		}
		Camera camera = client.gameRenderer.getCamera();
		if (camera == null || !camera.isReady()) {
			return;
		}
		float tick = camera.getLastTickProgress();
		Vec3d cameraPos = cameraPos(camera, tick);
		TextRenderer text = client.textRenderer;
		Quaternionf rotation = camera.getRotation();
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (player == client.player && !camera.isThirdPerson()) {
				continue;
			}
			if (player.squaredDistanceTo(client.player) > VIEW_DISTANCE * VIEW_DISTANCE) {
				continue;
			}
			ServerDataStore.CosmeticLook look = ServerDataStore.cosmeticOf(
					player.getUuid().toString(), player.getName().getString());
			if (look.title().isEmpty()) {
				continue;
			}
			Vec3d head = player.getLerpedPos(tick).add(0.0, player.getHeight() + 0.35, 0.0);
			int width = text.getWidth(look.title());
			matrices.push();
			matrices.translate(head.x - cameraPos.x, head.y - cameraPos.y, head.z - cameraPos.z);
			matrices.multiply(rotation);
			matrices.scale(-0.025f, -0.025f, 0.025f);
			text.draw(look.title(), -width / 2.0f, 0.0f, TITLE, false,
					matrices.peek().getPositionMatrix(), consumers,
					TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
			matrices.pop();
		}
	}

	private static Vec3d cameraPos(Camera camera, float tick) {
		Vec3d eye = camera.getFocusedEntity().getCameraPosVec(tick);
		if (!camera.isThirdPerson()) {
			return eye;
		}
		float distance = 4.0f;
		Vector3f forward = new Vector3f(0.0f, 0.0f, -1.0f);
		camera.getRotation().transform(forward);
		return eye.subtract(forward.x * distance, forward.y * distance, forward.z * distance);
	}
}
