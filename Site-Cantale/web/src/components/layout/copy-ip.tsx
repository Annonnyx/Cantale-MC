"use client";

import { useState } from "react";

const SERVER_IP = "play.cantale.world";

export function CopyIp({
  className = "",
  primary = false,
}: {
  className?: string;
  primary?: boolean;
}) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(SERVER_IP);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  };

  return (
    <button
      type="button"
      onClick={copy}
      className={`pressable group inline-flex w-fit items-center gap-3 border px-4 py-2.5 ${
        primary
          ? "border-action bg-action hover:border-action"
          : "border-iron-line bg-iron hover:border-bone"
      } ${className}`}
      aria-live="polite"
    >
      <span className={`font-tech text-xs tracking-[0.14em] ${primary ? "text-white" : "text-bone"}`}>
        {SERVER_IP}
      </span>
      <span className={`font-tech text-[10px] uppercase tracking-[0.2em] ${primary ? "text-white" : "text-steel-light"}`}>
        {copied ? "Copié" : "Copier"}
      </span>
    </button>
  );
}
