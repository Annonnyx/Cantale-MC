package fr.cantale.plugin.collections;

import fr.cantale.plugin.crate.CrateType;
import fr.cantale.plugin.spawners.SpawnerRarity;

/**
 * Récompense d'un palier de collection.
 *
 * @param spawner rareté exacte du spawner offert ({@code null} = aucun).
 *                YAML : {@code spawner: true} (= RARE, rétro-compatible) ou
 *                {@code spawner: RARE|EPIC|MYTHIC}.
 */
public record CollectionReward(int cantox, int lives, CrateType key, SpawnerRarity spawner) {
}
