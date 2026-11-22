import { readFileSync } from "node:fs";
import { join } from "node:path";
import { ImageResponse } from "next/og";

export const runtime = "nodejs";

export const alt = "CANTALE — Serveur Minecraft PvP factions hardcore";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function OpengraphImage() {
  // Satori (next/og) n'accepte que TTF/OTF — pas le WOFF2 du site.
  const zina = readFileSync(join(process.cwd(), "src/assets/fonts/Zina-Regular.ttf"));

  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          background: "linear-gradient(160deg, #EEF1EE 0%, #E4E9E5 100%)",
          border: "3px solid #2B2F4A",
          padding: "56px 64px",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 20,
            color: "#555A72",
            fontSize: 24,
            letterSpacing: "0.35em",
            textTransform: "uppercase",
          }}
        >
          <span style={{ width: 12, height: 12, background: "#2B2F4A" }} />
          Serveur Minecraft — PvP Factions Hardcore
        </div>

        <div
          style={{
            display: "flex",
            flex: 1,
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <span
            style={{
              fontFamily: "Zina",
              fontSize: 190,
              lineHeight: 1,
              color: "#2B2F4A",
              letterSpacing: "0.1em",
            }}
          >
            CANTALE
          </span>
        </div>

        <div
          style={{
            display: "flex",
            alignItems: "flex-end",
            justifyContent: "space-between",
          }}
        >
          <span
            style={{
              color: "#2B2F4A",
              fontSize: 30,
            }}
          >
            {"L'effort crée les forts"}
          </span>
          <span style={{ color: "#555A72", fontSize: 22, letterSpacing: "0.12em" }}>
            CANTALE.WORLD
          </span>
        </div>
      </div>
    ),
    {
      ...size,
      fonts: [{ name: "Zina", data: zina, weight: 400, style: "normal" }],
    },
  );
}
