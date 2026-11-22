import Link from "next/link";
import { CopyIp } from "@/components/layout/copy-ip";

export function JoinCta() {
  return (
    <section
      id="rejoindre"
      aria-labelledby="rejoindre-title"
      className="relative overflow-hidden border-t border-iron-line/60"
    >
      <div className="relative mx-auto flex w-full max-w-4xl flex-col items-center gap-8 px-5 py-24 text-center sm:px-8 sm:py-32">
        <h2 id="rejoindre-title" className="font-display text-4xl font-semibold leading-[1.05] text-bone sm:text-6xl">
          Rejoindre
        </h2>
        <p className="max-w-xl text-base leading-relaxed text-steel">
          Minecraft 1.21. L&apos;adresse est play.cantale.world. Le premier spawn crée le
          profil, avec trois vies.
        </p>
        <div className="flex flex-col items-center gap-4 sm:flex-row">
          <CopyIp primary />
          <a
            href="https://discord.gg/65a9upGPHx"
            target="_blank"
            rel="noopener noreferrer"
            className="pressable inline-flex items-center gap-3 border border-bone px-5 py-3 font-tech text-[11px] uppercase tracking-[0.22em] text-bone hover:bg-bone hover:text-ash"
          >
            Rejoindre le Discord
          </a>
        </div>
        <p className="text-sm text-steel">
          Avant d&apos;entrer,{" "}
          <Link href="/reglement" className="text-bone underline decoration-iron-line underline-offset-4">
            lis le règlement
          </Link>
          .
        </p>
      </div>
    </section>
  );
}
