const LETTERS = ["C", "A", "N", "T", "A", "L", "E"];

export function Hero() {
  return (
    <section
      aria-label="CANTALE — L'effort crée les forts"
      className="relative flex min-h-svh flex-col items-center justify-center overflow-hidden px-4"
    >
      <div
        aria-hidden
        className="pointer-events-none absolute left-1/2 top-1/2 h-[60vmin] w-[90vmin] -translate-x-1/2 -translate-y-1/2 rounded-full opacity-80"
        style={{ background: "radial-gradient(ellipse at center, var(--color-bleu) 0%, transparent 68%)" }}
      />

      <h1
        aria-label="CANTALE"
        className="relative select-none text-center font-hero leading-none text-bone"
        style={{ fontSize: "clamp(4.5rem, 17vw, 15rem)", letterSpacing: "0.06em" }}
      >
        {LETTERS.map((letter, i) => (
          <span
            key={i}
            aria-hidden
            className="hero-letter"
            style={{ animationDelay: `${150 + i * 90}ms` }}
          >
            {letter}
          </span>
        ))}
      </h1>

      <p
        className="hero-fade relative mt-8 text-center font-tech text-sm text-bone sm:text-base"
        style={{ animationDelay: "1s" }}
      >
        L&apos;effort crée les forts
      </p>

      <div
        aria-hidden
        className="hero-fade absolute bottom-10 flex flex-col items-center gap-3"
        style={{ animationDelay: "1.6s" }}
      >
        <span className="font-sans text-xs text-steel">Défiler</span>
        <span className="h-10 w-px bg-gradient-to-b from-bone to-transparent" />
      </div>
    </section>
  );
}
