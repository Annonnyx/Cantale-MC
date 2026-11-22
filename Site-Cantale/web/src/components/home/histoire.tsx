import Link from "next/link";

export function Histoire() {
  return (
    <section aria-labelledby="histoire-title" className="border-t border-iron-line/60 bg-ash-deep">
      <div className="mx-auto grid w-full max-w-7xl gap-12 px-5 py-24 sm:px-8 sm:py-32 lg:grid-cols-[1fr_1.6fr]">
        <h2
          id="histoire-title"
          className="font-display text-4xl font-semibold leading-[1.05] text-bone sm:text-5xl"
        >
          Ce que le serveur fait
        </h2>

        <div className="flex flex-col gap-6 text-base leading-relaxed text-steel">
          <p>
            CANTALE est un serveur Minecraft PvP factions. Vies, claims, Cantox et items
            sont dans le plugin du serveur.
          </p>
          <p>
            Une faction prend des chunks avec <span className="text-bone">/f claim</span>. Le
            PvP est ouvert hors des zones protégées. Pickantaxe, Cantaxe, Multi-Cantool et
            Cantalame ne se craftent pas : ils sortent des caisses.
          </p>
          <p>
            À zéro vie, le joueur est banni. La Liste, sur le site, garde le nom.
          </p>
          <div className="flex flex-wrap gap-4 pt-2">
            <Link
              href="/wiki"
              className="pressable border border-bone px-5 py-3 font-tech text-[11px] uppercase tracking-[0.22em] text-bone hover:bg-bone hover:text-ash"
            >
              Lire le wiki
            </Link>
            <Link
              href="/reglement"
              className="pressable border border-iron-line px-5 py-3 font-tech text-[11px] uppercase tracking-[0.22em] text-steel hover:border-bone hover:text-bone"
            >
              Le règlement
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
