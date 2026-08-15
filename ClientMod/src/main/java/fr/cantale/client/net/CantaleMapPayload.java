package fr.cantale.client.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Payload brut du canal {@code cantale:map} (JSON UTF-8 dans un tableau d'octets).
 * Capacité 64 Ko : le serveur pagine ses messages sous 32 Ko (limite historique Bukkit).
 */
public record CantaleMapPayload(byte[] data) implements CustomPayload {

	public static final Identifier CHANNEL = Identifier.of("cantale", "map");
	public static final CustomPayload.Id<CantaleMapPayload> ID = new CustomPayload.Id<>(CHANNEL);
	public static final PacketCodec<RegistryByteBuf, CantaleMapPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.byteArray(65536), CantaleMapPayload::data, CantaleMapPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
