package fr.cantale.plugin.cache;

import fr.cantale.plugin.CantalePlugin;
import fr.cantale.plugin.config.SecretResolver;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicBoolean;

public class RedisCache {

    private static final long PUBSUB_RETRY_MS = 5_000L;
    private static final long PUBSUB_JOIN_MS = 3_000L;

    private final CantalePlugin plugin;
    private JedisPool jedisPool;
    private boolean enabled;
    private final AtomicBoolean pubSubRunning = new AtomicBoolean();
    private volatile Thread pubSubThread;
    private volatile JedisPubSub pubSub;
    private volatile Jedis subscribedJedis;
    private volatile BiConsumer<String, String> pubSubHandler;

    public RedisCache(CantalePlugin plugin) {
        this.plugin = plugin;
        this.enabled = false;
    }

    /**
     * Initialise la connexion Redis
     */
    public void init() {
        SecretResolver secrets = SecretResolver.system(message -> plugin.getLogger().warning(message));
        String host = secrets.firstUsable("CANTALE_REDIS_HOST", "redis.host", false,
                plugin.getConfig().getString("redis.host", "localhost"));
        if (host.isEmpty()) {
            host = "localhost";
        }
        int port = plugin.getConfig().getInt("redis.port", 6379);
        String portEnv = System.getenv("CANTALE_REDIS_PORT");
        if (portEnv != null && !portEnv.isBlank()) {
            try {
                int parsed = Integer.parseInt(portEnv.trim());
                if (parsed > 0 && parsed <= 65535) {
                    port = parsed;
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("[Config] CANTALE_REDIS_PORT illisible, repli sur redis.port.");
            }
        }
        String password = secrets.firstUsable("CANTALE_REDIS_PASSWORD", "redis.password", false,
                plugin.getConfig().getString("redis.password", ""));
        if (password.isEmpty()) {
            password = null;
        }

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(20);
            poolConfig.setMaxIdle(10);
            poolConfig.setMinIdle(5);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestWhileIdle(true);
            poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());

            if (password != null && !password.isEmpty()) {
                jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
            } else {
                jedisPool = new JedisPool(poolConfig, host, port);
            }

            // Test connection
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
            }

            enabled = true;
            plugin.getLogger().info("Redis connecté avec succès !");
        } catch (Exception e) {
            plugin.getLogger().warning("Impossible de se connecter à Redis : " + e.getMessage());
            plugin.getLogger().warning("Cache Redis désactivé.");
            enabled = false;
        }
    }

    /**
     * Met une valeur dans le cache
     */
    public void set(String key, String value) {
        if (!enabled || jedisPool == null) return;
        
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(key, value);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur Redis set : " + e.getMessage());
        }
    }

    /**
     * Met une valeur avec TTL (en secondes)
     */
    public void setex(String key, int seconds, String value) {
        if (!enabled || jedisPool == null) return;
        
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(key, seconds, value);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur Redis setex : " + e.getMessage());
        }
    }

    /**
     * Récupère une valeur du cache
     */
    public String get(String key) {
        if (!enabled || jedisPool == null) return null;
        
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur Redis get : " + e.getMessage());
            return null;
        }
    }

    /**
     * Supprime une clé du cache
     */
    public void del(String key) {
        if (!enabled || jedisPool == null) return;
        
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur Redis del : " + e.getMessage());
        }
    }

    /**
     * Vérifie si une clé existe
     */
    public boolean exists(String key) {
        if (!enabled || jedisPool == null) return false;
        
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(key);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur Redis exists : " + e.getMessage());
            return false;
        }
    }

    /**
     * Incrémente une valeur
     */
    public long incr(String key) {
        if (!enabled || jedisPool == null) return -1;
        
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.incr(key);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur Redis incr : " + e.getMessage());
            return -1;
        }
    }

    /**
     * Décrémente une valeur
     */
    public long decr(String key) {
        if (!enabled || jedisPool == null) return -1;
        
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.decr(key);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur Redis decr : " + e.getMessage());
            return -1;
        }
    }

    /**
     * Publie sur un canal déjà connu. N'écrit aucun secret.
     */
    public void publish(String channel, String message) {
        if (!enabled || jedisPool == null || channel == null || message == null) {
            return;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(channel, message);
        } catch (Exception e) {
            plugin.getLogger().warning("[Redis] Publication impossible sur " + channel + " : " + safeMessage(e));
        }
    }

    /**
     * Worker bloquant Jedis sur {@link RedisChannels#SUBSCRIPTIONS}.
     * Redis absent : log explicite, pas de thread, pas de polling SQL de remplacement.
     */
    public void startPubSub(BiConsumer<String, String> handler) {
        this.pubSubHandler = handler;
        if (!enabled || jedisPool == null) {
            plugin.getLogger().warning("[Redis] Pub/Sub inactif : Redis est absent ou injoignable. "
                    + "Les files web_chat_outbox et web_admin_actions ne sont plus lues en polling. "
                    + "Les INSERT d'archivage restent en place. Aucun SELECT périodique de secours.");
            return;
        }
        if (!pubSubRunning.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(this::pubSubLoop, "cantale-redis-pubsub");
        thread.setDaemon(true);
        this.pubSubThread = thread;
        thread.start();
    }

    private void pubSubLoop() {
        while (pubSubRunning.get() && !Thread.currentThread().isInterrupted()) {
            JedisPubSub subscription = new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    BiConsumer<String, String> handler = pubSubHandler;
                    if (handler == null) {
                        return;
                    }
                    try {
                        handler.accept(channel, message);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Redis] Message " + channel + " ignoré : " + safeMessage(e));
                    }
                }
            };
            this.pubSub = subscription;
            try (Jedis jedis = jedisPool.getResource()) {
                this.subscribedJedis = jedis;
                plugin.getLogger().info("[Redis] Pub/Sub abonné : "
                        + RedisChannels.CHAT_WEB_TO_MC + ", "
                        + RedisChannels.CHAT_MC_TO_WEB + ", "
                        + RedisChannels.ADMIN_ACTIONS + ".");
                jedis.subscribe(subscription, RedisChannels.SUBSCRIPTIONS);
            } catch (Exception e) {
                if (!pubSubRunning.get()) {
                    break;
                }
                plugin.getLogger().warning("[Redis] Pub/Sub interrompu : " + safeMessage(e)
                        + " — nouvel essai dans 5 s, sans polling SQL.");
                if (!sleepRetry()) {
                    break;
                }
            } finally {
                this.subscribedJedis = null;
                this.pubSub = null;
            }
        }
    }

    private boolean sleepRetry() {
        try {
            Thread.sleep(PUBSUB_RETRY_MS);
            return pubSubRunning.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void stopPubSub() {
        pubSubRunning.set(false);
        JedisPubSub subscription = this.pubSub;
        if (subscription != null) {
            try {
                subscription.unsubscribe();
            } catch (Exception ignored) {
                // La fermeture de socket ci-dessous débloque subscribe().
            }
        }
        Jedis held = this.subscribedJedis;
        if (held != null) {
            try {
                held.close();
            } catch (Exception ignored) {
                // Déjà rendu au pool ou fermé par le thread d'écoute.
            }
        }
        Thread thread = pubSubThread;
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
            try {
                thread.join(PUBSUB_JOIN_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (thread.isAlive()) {
                plugin.getLogger().warning("[Redis] Le thread Pub/Sub n'a pas terminé dans les 3 s.");
            }
        }
        pubSubThread = null;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        String lower = message.toLowerCase();
        if (lower.contains("password") || lower.contains("auth")) {
            return e.getClass().getSimpleName();
        }
        return message;
    }

    /**
     * Arrête l'abonnement puis ferme le pool. Aucun thread Pub/Sub ne doit survivre.
     */
    public void shutdown() {
        stopPubSub();
        if (jedisPool != null) {
            jedisPool.close();
            jedisPool = null;
            enabled = false;
            plugin.getLogger().info("Redis déconnecté.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
