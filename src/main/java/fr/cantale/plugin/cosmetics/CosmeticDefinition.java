package fr.cantale.plugin.cosmetics;

import java.util.List;

public record CosmeticDefinition(String id, CosmeticKind kind, int tier) {

    public static final List<CosmeticDefinition> SEASON_ONE = List.of(
            new CosmeticDefinition("title_novice", CosmeticKind.TITLE, 1),
            new CosmeticDefinition("aura_ember", CosmeticKind.AURA, 2),
            new CosmeticDefinition("cape_linen", CosmeticKind.CAPE, 3),
            new CosmeticDefinition("title_voyageur", CosmeticKind.TITLE, 4),
            new CosmeticDefinition("aura_aurora", CosmeticKind.AURA, 5),
            new CosmeticDefinition("cape_banner", CosmeticKind.CAPE, 6)
    );

    public static CosmeticDefinition byId(String id) {
        if (id == null) {
            return null;
        }
        for (CosmeticDefinition definition : SEASON_ONE) {
            if (definition.id().equalsIgnoreCase(id)) {
                return definition;
            }
        }
        return null;
    }

    public static boolean known(String id) {
        return byId(id) != null;
    }
}
