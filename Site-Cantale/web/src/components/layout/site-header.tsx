import Link from "next/link";
import { AuthEntry } from "./auth-entry";
import { LiveCounter } from "./live-counter";
import { MobileMenu } from "./mobile-menu";

const NAV_LINKS = [
  { href: "/items", label: "Items" },
  { href: "/factions", label: "Factions" },
  { href: "/classements", label: "Classements" },
  { href: "/carte", label: "Carte" },
  { href: "/chat", label: "Chat" },
  { href: "/wiki", label: "Wiki" },
  { href: "/vote", label: "Vote" },
  { href: "/recrutement", label: "Recrutement" },
  { href: "/partenariats", label: "Partenariats" },
];

/** Découverte secondaire — footer + section « Plus » du menu. */
const EXTRA_LINKS = [
  { href: "/reglement", label: "Règlement" },
  { href: "/boutique", label: "Boutique" },
  { href: "/stats", label: "Statistiques" },
  { href: "/la-liste", label: "La Liste" },
];

export function SiteHeader() {
  return (
    <header className="fixed inset-x-0 top-0 z-50 border-b border-iron-line/40 bg-ash/80 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-5 sm:px-8">
        <Link
          href="/"
          className="flex items-center gap-2 font-hero text-xl tracking-[0.18em] text-bone transition-colors hover:text-steel-light"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true" className="h-4 w-4">
            <path fill="currentColor" d="M2 5h15v5h-5.4v11h-3.2V10H2V5z" />
          </svg>
          CANTALE
        </Link>

        <div className="flex items-center gap-4">
          <LiveCounter />
          <span className="hidden lg:inline-block">
            <AuthEntry />
          </span>
          <Link
            href="/#rejoindre"
            className="pressable hidden border border-action bg-action px-4 py-2 font-tech text-[11px] uppercase tracking-[0.22em] text-white sm:inline-block"
          >
            Rejoindre
          </Link>
          <MobileMenu
            links={NAV_LINKS}
            extraLinks={EXTRA_LINKS}
            authLink={{ href: "/connexion", label: "Connexion / Profil" }}
          />
        </div>
      </div>
    </header>
  );
}
