package fr.cantale.plugin.cache;

import fr.cantale.plugin.CantalePlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

public class RedisCache {

    private final CantalePlugin plugin;
    private JedisPool jedisPool;
    private boolean enabled;

    public RedisCache(CantalePlugin plugin) {
        this.plugin = plugin;
        this.enabled = false;
    }

    /**
     * Initialise la connexion Redis
     */
    public void init() {
        String host = plugin.getConfig().getString("redis.host", "localhost");
        int port = plugin.getConfig().getInt("redis.port", 6379);
        String password = plugin.getConfig().getString("redis.password", null);

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
     * Ferme la connexion Redis
     */
    public void shutdown() {
        if (jedisPool != null) {
            jedisPool.close();
            plugin.getLogger().info("Redis déconnecté.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
