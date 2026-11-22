const ITEMS = [
  "play.cantale.world",
  "3 vies",
  "Factions et claims",
  "Cantox",
  "Zones contestées",
  "Items forgés",
  "La Liste",
];

export function RegistreStrip() {
  return (
    <div className="border-y border-iron-line/60 bg-bleu/40">
      <ul className="mx-auto flex max-w-7xl flex-wrap items-center justify-center gap-x-6 gap-y-2 px-5 py-3 text-sm text-bone sm:px-8">
        {ITEMS.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </div>
  );
}
