import type { Metadata } from "next";
import Link from "next/link";
import { env } from "@/server/env";
import { Stamp } from "@/components/ui/stamp";
import { BoutiqueShop } from "./shop-client";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Boutique",
  description: "La boutique de CANTALE est fermée. Aucun achat n'est possible.",
};

function ComingSoon() {
  return (
    <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col px-5 pb-24 pt-28 sm:px-8 sm:pt-32">
      <div className="mx-auto flex w-full max-w-2xl flex-col items-start gap-8">
        <header className="flex flex-col gap-4">
          <h1 className="font-display text-4xl font-semibold leading-[1.05] text-bone sm:text-5xl">
            Boutique
          </h1>
          <Stamp tone="gold" rotation={-2}>
            Fermée
          </Stamp>
        </header>

        <div className="flex w-full flex-col gap-6 border border-iron-line bg-iron p-6 sm:p-8">
          <p className="max-w-xl text-base leading-relaxed text-steel">
            La boutique est fermée. On ne vend pas de vies, de Cantox, de vol ni de clés.
            Rien n&apos;est débité.
          </p>
          <p className="max-w-xl text-base leading-relaxed text-steel">
            En jeu, la Cantox passe par /shop, l&apos;hôtel des ventes et les échanges. Les
            trois vies de départ ne s&apos;achètent pas ici.
          </p>
          <div className="flex flex-col gap-4 border-t border-iron-line/60 pt-6 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm leading-relaxed text-steel">
              Le vote, lui, est ouvert.
            </p>
            <Link
              href="/vote"
              className="pressable inline-block shrink-0 border border-bone px-4 py-2 text-center font-tech text-[11px] uppercase tracking-[0.22em] text-bone hover:bg-bone hover:text-ash"
            >
              Voter
            </Link>
          </div>
        </div>
      </div>
    </main>
  );
}

export default function BoutiquePage() {
  // Le flag est lu côté serveur : désactivé, le catalogue n'est jamais rendu.
  if (!env.shopEnabled) return <ComingSoon />;
  return <BoutiqueShop />;
}
