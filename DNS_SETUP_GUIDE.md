# 🌐 Guide Configuration DNS — Cantale

## Domaines acquis

| Domaine | Usage | Sous-domaine Minecraft |
|---------|-------|------------------------|
| `cantale.world` | **Site web principal** + MC | `play.cantale.world` |
| `cantale.fun` | **Site web mirror** + MC | `play.cantale.fun` |
| `cantale.store` | **Boutique** uniquement | `play.cantale.store` |

---

## 1. Configuration DNS (chez ton registrar)

### Enregistrements A

| Type | Nom | Valeur | TTL |
|------|-----|--------|-----|
| A | `@` (cantale.world) | IP de ton VPS/site | 3600 |
| A | `www` | IP de ton VPS/site | 3600 |
| A | `play` | IP de ton serveur MC | 3600 |

| Type | Nom | Valeur | TTL |
|------|-----|--------|-----|
| A | `@` (cantale.fun) | IP de ton VPS/site | 3600 |
| A | `www` | IP de ton VPS/site | 3600 |
| A | `play` | IP de ton serveur MC | 3600 |

| Type | Nom | Valeur | TTL |
|------|-----|--------|-----|
| A | `@` (cantale.store) | IP de ton VPS/site | 3600 |
| A | `www` | IP de ton VPS/site | 3600 |
| A | `play` | IP de ton serveur MC | 3600 |

> **Note** : Si ton hébergeur donne un CNAME pour le site (ex: Vercel/Netlify), remplace les A par des CNAME.

---

## 2. Redirections Boutique

`cantale.store` doit pointer vers la boutique. Deux options :

### Option A : Site statique hébergé sur le même serveur
```nginx
# /etc/nginx/sites-available/cantale.store
server {
  listen 443 ssl http2;
  server_name cantale.store www.cantale.store;
  
  # Si boutique = page statique sur le même serveur
  root /var/www/cantale-store;
  index boutique.html;
  
  ssl_certificate /etc/letsencrypt/live/cantale.store/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/cantale.store/privkey.pem;
}
```

### Option B : Redirection vers Stripe/Tebex/etc
```nginx
server {
  listen 443 ssl http2;
  server_name cantale.store www.cantale.store;
  
  ssl_certificate /etc/letsencrypt/live/cantale.store/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/cantale.store/privkey.pem;
  
  location / {
    return 301 https://ton-shop.tebex.io$request_uri;
  }
}
```

---

## 3. SSL / HTTPS (Certbot)

```bash
# Installer les certificats pour tous les domaines
sudo certbot --nginx \
  -d cantale.world -d www.cantale.world -d play.cantale.world \
  -d cantale.fun -d www.cantale.fun -d play.cantale.fun \
  -d cantale.store -d www.cantale.store -d play.cantale.store

# Renouvellement automatique activé par défaut
sudo certbot renew --dry-run
```

---

## 4. Nginx complet (sites web + API)

```nginx
# /etc/nginx/sites-available/cantale

# --- HTTP → HTTPS ---
server {
  listen 80;
  server_name cantale.world www.cantale.world cantale.fun www.cantale.fun cantale.store www.cantale.store;
  return 301 https://$host$request_uri;
}

# --- Site principal (cantale.world) ---
server {
  listen 443 ssl http2;
  server_name cantale.world www.cantale.world;

  ssl_certificate /etc/letsencrypt/live/cantale.world/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/cantale.world/privkey.pem;

  root /var/www/cantale-site;
  index index.html;

  # API backend proxy
  location /api/ {
    proxy_pass http://localhost:3000/api/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
  }

  location / {
    try_files $uri $uri/ /index.html;
  }
}

# --- Mirror (cantale.fun) → même contenu ---
server {
  listen 443 ssl http2;
  server_name cantale.fun www.cantale.fun;

  ssl_certificate /etc/letsencrypt/live/cantale.fun/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/cantale.fun/privkey.pem;

  root /var/www/cantale-site;
  index index.html;

  location /api/ {
    proxy_pass http://localhost:3000/api/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
  }

  location / {
    try_files $uri $uri/ /index.html;
  }
}

# --- Boutique (cantale.store) ---
server {
  listen 443 ssl http2;
  server_name cantale.store www.cantale.store;

  ssl_certificate /etc/letsencrypt/live/cantale.store/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/cantale.store/privkey.pem;

  root /var/www/cantale-store;
  index index.html;

  location / {
    try_files $uri $uri/ =404;
  }
}
```

---

## 5. Serveur Minecraft (server.properties)

```properties
# L'IP affichée dans la liste des serveurs
server-ip=0.0.0.0
server-port=25565

# Le mot de passe/query (optionnel)
enable-query=true
query.port=25565
motd=\u00a76CANTALE \u00a77— \u00a7cTrois vies. C'est tout.
```

**Dans le plugin Cantale** (`config.yml`) :
```yaml
# URL du site web (pour les messages in-game)
web-url: "https://cantale.world"
shop-url: "https://cantale.store"
mc-ip: "play.cantale.world"
```

---

## 6. Récapitulatif des URLs

| Service | URL principale | URL mirror |
|---------|---------------|------------|
| Site web | `https://cantale.world` | `https://cantale.fun` |
| IP MC | `play.cantale.world` | `play.cantale.fun` |
| Boutique | `https://cantale.store` | — |
| API | `https://cantale.world/api/` | `https://cantale.fun/api/` |

---

## 7. Checklist

- [ ] Enregistrer les 3 domaines chez le registrar
- [ ] Ajouter les enregistrements A/CNAME
- [ ] Attendre la propagation DNS (5-60 min)
- [ ] Configurer Nginx
- [ ] Générer les certificats SSL
- [ ] Tester `https://cantale.world`
- [ ] Tester `https://cantale.fun`
- [ ] Tester `https://cantale.store`
- [ ] Tester connexion MC sur `play.cantale.world`
- [ ] Tester connexion MC sur `play.cantale.fun`
- [ ] Vérifier que les cookies cross-domaine fonctionnent (`.cantale.world`)
