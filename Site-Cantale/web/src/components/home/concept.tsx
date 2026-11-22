import Link from "next/link";
import { LifeNotches } from "@/components/ui/life-notches";

const CARDS = [
  {
    title: "Factions et claims",
    text: "Une faction claim des chunks, les protège, et peut recruter. Les zones contestées ne sont à personne.",
    href: "/factions",
  },
  {
    title: "Cantox",
    text: "La monnaie du serveur. Elle circule par /shop, l'hôtel des ventes, /pay et /trade.",
    href: "/wiki",
  },
  {
    title: "Items forgés",
    text: "Pickantaxe, Cantaxe, Multi-Cantool, Cantalame. Aucun ne se crafte.",
    href: "/items",
  },
  {
    title: "Primes",
    text: "/wanted pose une prime en Cantox. Le joueur qui tue la cible l'encaisse.",
    href: "/wiki",
  },
] as const;

export function Concept() {
  return (
    <section aria-labelledby="concept-title" className="mx-auto w-full max-w-7xl px-5 py-24 sm:px-8 sm:py-32">
      <div className="grid items-center gap-12 lg:grid-cols-2">
        <div className="flex flex-col gap-6">
          <h2
            id="concept-title"
            className="font-display text-4xl font-semibold leading-[1.05] text-bone sm:text-5xl"
          >
            Trois vies.
          </h2>
          <p className="max-w-lg text-base leading-relaxed text-steel">
            Chaque joueur commence avec trois vies. Une mort en retire une, en PvP comme en PvE.
            À zéro, le ban est automatique et le nom rejoint{" "}
            <Link href="/la-liste" className="text-bone underline decoration-iron-line underline-offset-4">
              La Liste
            </Link>
            .
          </p>
          <div className="flex flex-wrap gap-8 pt-2">
            <div className="flex flex-col gap-2">
              <LifeNotches lives={3} />
              <span className="font-sans text-xs text-steel">3 vies</span>
            </div>
            <div className="flex flex-col gap-2">
              <LifeNotches lives={1} />
              <span className="font-sans text-xs text-steel">1 vie</span>
            </div>
            <div className="flex flex-col gap-2">
              <LifeNotches lives={0} />
              <span className="font-sans text-xs text-steel">Banni</span>
            </div>
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          {CARDS.map((card) => (
            <Link
              key={card.title}
              href={card.href}
              className="card-lift group flex flex-col gap-3 border border-iron-line bg-iron p-6"
            >
              <span className="font-display text-lg font-semibold text-bone">{card.title}</span>
              <span className="text-sm leading-relaxed text-steel">{card.text}</span>
              <span className="mt-auto pt-2 text-sm text-bone">
                Voir <span className="cta-arrow">→</span>
              </span>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}
