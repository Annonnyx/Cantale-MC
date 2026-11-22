package fr.cantale.client.render;

/**
 * Palette unique des relations de claim, partagée par la carte, le HUD et le tooltip.
 * Les valeurs restent celles de la carte in-game du serveur.
 */
public final class ClaimColors {

	public static final int OWN = 0x3FD23F;
	public static final int ALLY = 0x3FB4D2;
	public static final int WAR = 0xE69B3A;
	public static final int ENEMY = 0xD23F3F;
	public static final int PASDIC = 0xB04FD8;
	public static final int OTHER = 0x8A8A96;
	/** Wilderness : distinct du gris « autre faction ». */
	public static final int WILDERNESS = 0x9BB0C4;

	private ClaimColors() {
	}

	public static int rgb(String rel) {
		if (rel == null) return OTHER;
		return switch (rel) {
			case "OWN" -> OWN;
			case "ALLY" -> ALLY;
			case "WAR" -> WAR;
			case "ENEMY" -> ENEMY;
			case "PASDIC" -> PASDIC;
			default -> OTHER;
		};
	}

	/** Clé de langue de la relation diplomatique affichée au joueur. */
	public static String translationKey(String rel) {
		if (rel == null) return "cantale.rel.other";
		return switch (rel) {
			case "OWN" -> "cantale.rel.own";
			case "ALLY" -> "cantale.rel.ally";
			case "WAR" -> "cantale.rel.war";
			case "ENEMY" -> "cantale.rel.enemy";
			case "PASDIC" -> "cantale.rel.pasdic";
			default -> "cantale.rel.other";
		};
	}
}
