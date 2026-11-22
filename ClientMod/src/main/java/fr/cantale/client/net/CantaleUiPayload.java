package fr.cantale.client.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Payload brut du canal {@code cantale:ui} (JSON UTF-8). */
public record CantaleUiPayload(byte[] data) implements CustomPayload {

	public static final Identifier CHANNEL = Identifier.of("cantale", "ui");
	public static final CustomPayload.Id<CantaleUiPayload> ID = new CustomPayload.Id<>(CHANNEL);
	public static final PacketCodec<RegistryByteBuf, CantaleUiPayload> CODEC = CustomPayload.codecOf(
			(payload, buf) -> buf.writeBytes(payload.data()),
			buf -> {
				byte[] bytes = new byte[buf.readableBytes()];
				buf.readBytes(bytes);
				return new CantaleUiPayload(bytes);
			});

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
