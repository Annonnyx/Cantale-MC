import net from "node:net";

/**
 * Publication Redis (RESP) vers le plugin Minecraft.
 * Canaux alignés sur `RedisChannels` côté Java.
 * Variables : CANTALE_REDIS_HOST, CANTALE_REDIS_PORT, CANTALE_REDIS_PASSWORD.
 * Aucun secret n'est écrit dans les logs. Si Redis est absent, l'INSERT SQL reste l'archive
 * et ce module n'invente pas de polling.
 */

const CHAT_WEB_TO_MC = "cantale:chat:web2mc";
const ADMIN_ACTIONS = "cantale:admin:actions";

let missingHostWarned = false;
let lastFailureAt = 0;

function readEnv(name: string): string | null {
  const value = process.env[name];
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function encode(parts: string[]): Buffer {
  const buffers: Buffer[] = [Buffer.from(`*${parts.length}\r\n`)];
  for (const part of parts) {
    const body = Buffer.from(part, "utf8");
    buffers.push(Buffer.from(`$${body.length}\r\n`));
    buffers.push(body);
    buffers.push(Buffer.from("\r\n"));
  }
  return Buffer.concat(buffers);
}

export type WebChatPublish = {
  id: number;
  playerUuid: string;
  playerName: string;
  message: string;
  factionId: number | null;
};

export type AdminActionPublish = {
  id: number;
  type: string;
  targetUuid: string | null;
  targetName: string | null;
  payload: string | null;
  actorDiscordId: string;
};

export function publishWebChat(body: WebChatPublish): Promise<void> {
  return publish(CHAT_WEB_TO_MC, JSON.stringify(body));
}

export function publishAdminAction(body: AdminActionPublish): Promise<void> {
  return publish(ADMIN_ACTIONS, JSON.stringify(body));
}

function publish(channel: string, message: string): Promise<void> {
  const host = readEnv("CANTALE_REDIS_HOST");
  if (!host) {
    if (!missingHostWarned) {
      missingHostWarned = true;
      console.warn(
        "[redis] CANTALE_REDIS_HOST absent : message archivé en SQL, non publié. Pas de repli polling.",
      );
    }
    return Promise.resolve();
  }
  const portRaw = readEnv("CANTALE_REDIS_PORT");
  const port = portRaw ? Number.parseInt(portRaw, 10) : 6379;
  const password = readEnv("CANTALE_REDIS_PASSWORD");
  const commands: string[][] = [];
  if (password) commands.push(["AUTH", password]);
  commands.push(["PUBLISH", channel, message]);
  commands.push(["QUIT"]);

  return new Promise((resolve) => {
    const socket = net.connect({
      host,
      port: Number.isFinite(port) && port > 0 ? port : 6379,
    });
    let settled = false;
    const finish = (warn?: string) => {
      if (settled) return;
      settled = true;
      if (warn) {
        const now = Date.now();
        if (now - lastFailureAt > 30_000) {
          lastFailureAt = now;
          console.warn(`[redis] ${warn}`);
        }
      }
      socket.destroy();
      resolve();
    };
    socket.setTimeout(800);
    socket.on("timeout", () => finish(`publication ${channel} expirée`));
    socket.on("error", (error) => finish(`publication ${channel} impossible : ${error.message}`));
    socket.on("connect", () => {
      for (const command of commands) socket.write(encode(command));
    });
    socket.on("data", (chunk) => {
      const text = chunk.toString("utf8");
      if (text.startsWith("-ERR") || text.includes("\r\n-ERR")) {
        finish(`Redis a refusé la publication sur ${channel}`);
      }
    });
    socket.on("close", () => finish());
  });
}
