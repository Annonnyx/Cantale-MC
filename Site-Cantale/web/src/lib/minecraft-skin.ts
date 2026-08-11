/**
 * URLs de textures / avatars Minecraft.
 *
 * Fournisseur : mc-heads.net (CORS `Access-Control-Allow-Origin: *`, UUID ou
 * pseudo). Aligné sur classements / la-liste / factions.
 *
 * Historique : crafatar.com était utilisé pour le viewer 3D mais renvoie souvent
 * HTTP 521 (origine Cloudflare down) — skins et fallback 2D cassés ensemble.
 * Approche type NameMC = profil Mojang → textures.minecraft.net ; mc-heads
 * fait ce resolve côté CDN et reste le proxy public le plus stable ici.
 */

/** Texture skin PNG (64×64) pour skinview3d — préférer l'UUID. */
export function minecraftSkinUrl(uuidOrName: string): string {
  return `https://mc-heads.net/skin/${encodeURIComponent(uuidOrName)}`;
}

/** Avatar tête 2D (repli si WebGL indisponible). */
export function minecraftAvatarUrl(uuidOrName: string, size = 160): string {
  return `https://mc-heads.net/avatar/${encodeURIComponent(uuidOrName)}/${size}`;
}
