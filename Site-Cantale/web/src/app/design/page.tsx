import type { Metadata } from "next";
import type { ReactNode } from "react";
import Link from "next/link";
import { LifeNotches } from "@/components/ui/life-notches";
import { Stamp } from "@/components/ui/stamp";

export const metadata: Metadata = {
  title: "Design System",
  description: "Palette, polices et composants du site CANTALE.",
  robots: { index: false, follow: false },
};

const PALETTE = [
  { name: "Encre", token: "--color-encre", hex: "#2B2F4A", swatch: "bg-encre", usage: "Texte" },
  { name: "Papier givré", token: "--color-papier", hex: "#EEF1EE", swatch: "bg-papier", usage: "Fond" },
  { name: "Bleu poudre", token: "--color-bleu", hex: "#B9D0E8", swatch: "bg-bleu", usage: "Aplat" },
  { name: "Sauge", token: "--color-sauge", hex: "#C3D8C2", swatch: "bg-sauge", usage: "Aplat" },
  { name: "Lilas gris", token: "--color-lilas", hex: "#CBC3E3", swatch: "bg-lilas", usage: "Aplat" },
  { name: "Rose cœur", token: "--color-rose", hex: "#F2A7B1", swatch: "bg-rose", usage: "Aplat" },
  { name: "Beurre", token: "--color-beurre", hex: "#F2E2A0", swatch: "bg-beurre", usage: "Aplat" },
  { name: "Rose d'action", token: "--color-action", hex: "#C2455A", swatch: "bg-action", usage: "Bouton principal" },
] as const;

const SURFACES = [
  { name: "ash", hex: "#EEF1EE", note: "Papier — fond" },
  { name: "ash-deep", hex: "#E4E9E5", note: "Bande, footer" },
  { name: "iron", hex: "#F7F8F6", note: "Carte" },
  { name: "bone", hex: "#2B2F4A", note: "Encre — texte" },
  { name: "steel", hex: "#555A72", note: "Texte secondaire" },
] as const;

const RARITIES = [
  { name: "Rare", hex: "#466DA7", classes: "border-rare text-rare", item: "Pickantaxe" },
  { name: "Épique", hex: "#8C50BB", classes: "border-epique text-epique", item: "Cantaxe" },
  { name: "Mythique", hex: "#BA424F", classes: "border-mythique text-mythique", item: "Multi-Cantool" },
  { name: "Légendaire", hex: "#876628", classes: "border-legendaire text-legendaire", item: "Cantalame" },
] as const;

const FONTS = [
  {
    name: "Zina",
    token: "--font-hero",
    role: "Le mot CANTALE",
    fontClass: "font-hero",
    sample: "CANTALE",
    sampleClass: "text-5xl tracking-[0.12em] text-bone sm:text-6xl",
  },
  {
    name: "Clash Display",
    token: "--font-display",
    role: "Titres",
    fontClass: "font-display",
    sample: "Trois vies.",
    sampleClass: "text-3xl font-semibold leading-[1.05] text-bone sm:text-4xl",
  },
  {
    name: "Cabinet Grotesk",
    token: "--font-sans",
    role: "Texte",
    fontClass: "font-sans",
    sample: "Chaque joueur commence avec trois vies. À zéro, le ban est automatique.",
    sampleClass: "max-w-xl text-base leading-relaxed text-steel",
  },
  {
    name: "Nippo",
    token: "--font-tech",
    role: "Interface",
    fontClass: "font-tech",
    sample: "L'effort crée les forts",
    sampleClass: "text-sm text-bone",
  },
] as const;

const NOTCH_STATES = [
  { lives: 3, label: "3 vies" },
  { lives: 2, label: "2 vies" },
  { lives: 1, label: "1 vie" },
  { lives: 0, label: "Banni" },
] as const;

function Section({
  id,
  title,
  children,
}: {
  id: string;
  title: string;
  children: ReactNode;
}) {
  return (
    <section aria-labelledby={id} className="border-t border-iron-line/60 py-14 first:border-t-0 first:pt-0">
      <h2 id={id} className="font-display text-3xl font-semibold text-bone">
        {title}
      </h2>
      <div className="mt-8">{children}</div>
    </section>
  );
}

export default function DesignPage() {
  return (
    <main className="mx-auto w-full max-w-7xl px-5 pb-24 pt-28 sm:px-8 sm:pt-32">
      <header className="flex flex-col gap-4 pb-14">
        <h1 className="font-display text-4xl font-semibold leading-[1.05] text-bone sm:text-5xl">
          Design
        </h1>
        <p className="max-w-2xl text-base leading-relaxed text-steel">
          Papier givré, encre, cinq aplats pastel. Le rose d&apos;action ne sert qu&apos;au bouton
          principal. Le texte long est encre sur papier. Les polices ne changent pas : Zina,
          Clash Display, Cabinet Grotesk, Nippo.
        </p>
      </header>

      <Section id="couleurs" title="Couleurs">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {PALETTE.map((color) => (
            <div key={color.token} className="flex flex-col overflow-hidden border border-iron-line bg-iron">
              <div className={`h-16 w-full border-b border-iron-line/60 ${color.swatch}`} />
              <div className="flex flex-col gap-1.5 p-4">
                <span className="font-display text-sm font-semibold text-bone">{color.name}</span>
                <span className="font-tech text-xs text-steel">{color.hex}</span>
                <span className="text-xs leading-relaxed text-steel">{color.usage}</span>
              </div>
            </div>
          ))}
        </div>
        <ul className="mt-8 grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
          {SURFACES.map((surface) => (
            <li key={surface.name} className="border border-iron-line bg-iron px-4 py-3 text-sm text-steel">
              <span className="font-tech text-bone">{surface.name}</span>
              <span className="mt-1 block">{surface.hex}</span>
              <span className="mt-1 block">{surface.note}</span>
            </li>
          ))}
        </ul>
      </Section>

      <Section id="raretes" title="Raretés">
        <p className="mb-6 max-w-2xl text-sm leading-relaxed text-steel">
          Même teinte qu&apos;avant, assombrie pour rester lisible sur le papier.
        </p>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {RARITIES.map((rarity) => (
            <div key={rarity.name} className={`flex flex-col gap-3 border bg-iron p-5 ${rarity.classes}`}>
              <span className="font-tech text-xs">
                {rarity.name} — {rarity.hex}
              </span>
              <span className="font-display text-xl font-semibold">{rarity.item}</span>
            </div>
          ))}
        </div>
      </Section>

      <Section id="typographie" title="Polices">
        <div className="flex flex-col gap-4">
          {FONTS.map((font) => (
            <div
              key={font.token}
              className="flex flex-col gap-4 border border-iron-line bg-iron p-6 sm:flex-row sm:items-start sm:justify-between sm:gap-10"
            >
              <div className="flex shrink-0 flex-col gap-1.5 sm:w-56">
                <span className="font-display text-sm font-semibold text-bone">{font.name}</span>
                <span className="font-tech text-xs text-steel">{font.token}</span>
                <span className="text-xs leading-relaxed text-steel">{font.role}</span>
              </div>
              <p className={`${font.fontClass} ${font.sampleClass}`}>{font.sample}</p>
            </div>
          ))}
        </div>
      </Section>

      <Section id="encoches" title="Encoches de vie">
        <div className="flex flex-wrap items-end gap-8 border border-iron-line bg-iron p-6">
          {NOTCH_STATES.map((state) => (
            <div key={state.lives} className="flex flex-col gap-2">
              <LifeNotches lives={state.lives} />
              <span className="text-xs text-steel">
                {state.lives}/3 — {state.label}
              </span>
            </div>
          ))}
        </div>
      </Section>

      <Section id="tampons" title="Tampons">
        <div className="flex flex-wrap items-center gap-8 border border-iron-line bg-iron p-8">
          <Stamp tone="ember">Recrute</Stamp>
          <Stamp tone="gold" rotation={1.5}>
            Fermée
          </Stamp>
          <Stamp tone="steel" rotation={2}>
            Info
          </Stamp>
        </div>
      </Section>

      <Section id="boutons" title="Boutons">
        <div className="flex flex-wrap items-center gap-4 border border-iron-line bg-iron p-8">
          <button
            type="button"
            className="pressable border border-action bg-action px-4 py-2 font-tech text-[11px] uppercase tracking-[0.22em] text-white"
          >
            Rejoindre
          </button>
          <button
            type="button"
            className="pressable border border-bone px-4 py-2 font-tech text-[11px] uppercase tracking-[0.22em] text-bone hover:bg-bone hover:text-ash"
          >
            Secondaire
          </button>
          <Link href="/design" className="text-sm text-bone underline decoration-iron-line underline-offset-4">
            Lien dans le texte
          </Link>
        </div>
      </Section>
    </main>
  );
}
