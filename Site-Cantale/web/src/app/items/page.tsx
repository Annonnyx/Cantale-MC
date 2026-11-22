import type { Metadata } from "next";
import { ITEMS } from "@/lib/items-data";
import { ItemsCatalog } from "./items-catalog";

export const metadata: Metadata = {
  title: "Items",
  description:
    "Pickantaxe, Cantaxe, Multi-Cantool, Cantalame, item Vie, rune de fortification et armure du Garde. Aucun ne se crafte.",
};

export default function ItemsPage() {
  return (
    <main className="mx-auto w-full max-w-7xl px-5 pb-24 pt-28 sm:px-8 sm:pt-32">
      <header className="flex flex-col gap-4 pb-14">
        <h1 className="font-display text-4xl font-semibold leading-[1.05] text-bone sm:text-5xl">
          Items
        </h1>
        <p className="max-w-2xl text-base leading-relaxed text-steel">
          Pickantaxe, Cantaxe, Multi-Cantool, Cantalame, l&apos;item Vie, la rune de
          fortification et l&apos;armure du Garde. Aucun ne se crafte : caisses, grades ou
          events.
        </p>
      </header>

      <ItemsCatalog items={ITEMS} />
    </main>
  );
}
