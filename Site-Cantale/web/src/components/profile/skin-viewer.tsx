"use client";

import { useEffect, useRef, useState } from "react";
import { minecraftAvatarUrl, minecraftSkinUrl } from "@/lib/minecraft-skin";

const VIEWER_WIDTH = 300;
const VIEWER_HEIGHT = 420;

/**
 * Rendu 3D du skin Minecraft (skinview3d, animation idle + rotation douce).
 * Texture via mc-heads (UUID privilégié). Si WebGL est indisponible
 * ou si le chargement échoue, repli sur l'avatar 2D.
 */
export function SkinViewer({ uuid, username }: { uuid: string; username: string }) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [fallback, setFallback] = useState(false);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !uuid) {
      setFallback(true);
      return;
    }

    let cancelled = false;
    let viewer: import("skinview3d").SkinViewer | null = null;

    const init = async () => {
      try {
        const skinview3d = await import("skinview3d");
        if (cancelled || !canvasRef.current) return;

        viewer = new skinview3d.SkinViewer({
          canvas,
          width: VIEWER_WIDTH,
          height: VIEWER_HEIGHT,
        });
        viewer.camera.position.set(0, 10, 46);
        viewer.animation = new skinview3d.IdleAnimation();
        viewer.autoRotate = true;
        viewer.autoRotateSpeed = 0.6;
        viewer.zoom = 0.9;

        // UUID d'abord (stable) ; repli pseudo si le CDN ne résout pas l'UUID.
        try {
          await viewer.loadSkin(minecraftSkinUrl(uuid), { model: "auto-detect" });
        } catch {
          if (!username || username === "Inconnu") throw new Error("skin_uuid_failed");
          await viewer.loadSkin(minecraftSkinUrl(username), { model: "auto-detect" });
        }
      } catch {
        if (!cancelled) setFallback(true);
      }
    };

    init();
    return () => {
      cancelled = true;
      viewer?.dispose();
    };
  }, [uuid, username]);

  const avatarId = uuid || username;

  if (fallback) {
    return (
      <div
        className="flex items-end justify-center border border-iron-line bg-ash-deep"
        style={{ width: VIEWER_WIDTH, height: VIEWER_HEIGHT }}
      >
        {/* eslint-disable-next-line @next/next/no-img-element -- avatar externe mc-heads */}
        <img
          src={minecraftAvatarUrl(avatarId, 160)}
          alt={`Avatar Minecraft de ${username}`}
          width={160}
          height={160}
          className="mb-16 [image-rendering:pixelated]"
        />
      </div>
    );
  }

  return (
    <canvas
      ref={canvasRef}
      width={VIEWER_WIDTH}
      height={VIEWER_HEIGHT}
      aria-label={`Skin Minecraft de ${username}`}
      className="border border-iron-line bg-ash-deep"
    />
  );
}
