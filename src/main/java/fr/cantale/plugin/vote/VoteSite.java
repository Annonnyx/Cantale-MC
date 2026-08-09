package fr.cantale.plugin.vote;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Représente un site de vote configuré.
 *
 * @param displayName   nom affiché
 * @param url           URL du site
 * @param cooldownHours délai de vote en heures (détermine le nombre de caisses)
 * @param aliases       alias optionnels (noms de service Votifier)
 */
public record VoteSite(String displayName, String url, int cooldownHours, List<String> aliases) {

    public VoteSite(String displayName, String url, int cooldownHours) {
        this(displayName, url, cooldownHours, List.of());
    }

    /**
     * Retourne le nombre de Cadeaux du Roi gagnés pour un vote sur ce site.
     * Règle : 24h → 3, 3h → 2, tout autre → 1.
     * Toujours au moins 1.
     */
    public int crateCount() {
        if (cooldownHours >= 24) return 3;
        if (cooldownHours >= 3) return 2;
        return 1;
    }

    /**
     * Matching souple du nom de service Votifier contre ce site.
     * Accepte displayName, hostname de l'URL, alias, sous-domaines,
     * et variantes sans tirets / sans TLD (ex. {@code top-serveurs} ↔ {@code Top-Serveurs.net}).
     */
    public boolean matches(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) return false;

        String service = normalize(serviceName);
        if (service.isEmpty()) return false;

        List<String> candidates = new ArrayList<>();
        candidates.add(displayName);
        candidates.add(extractHost(url));
        if (aliases != null) {
            candidates.addAll(aliases);
        }

        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            String norm = normalize(candidate);
            if (norm.isEmpty()) continue;

            if (service.equals(norm)) return true;
            if (service.contains(norm) || norm.contains(service)) return true;

            // Sans TLD : "top-serveurs" ↔ "top-serveurs.net"
            String serviceBase = stripTld(service);
            String candidateBase = stripTld(norm);
            if (!serviceBase.isEmpty() && serviceBase.equals(candidateBase)) return true;

            // Alphanumérique seul : "ServeurPrive" ↔ "serveur-prive.net"
            String serviceAlnum = alnum(service);
            String candidateAlnum = alnum(norm);
            if (!serviceAlnum.isEmpty() && serviceAlnum.equals(candidateAlnum)) return true;
            if (!serviceAlnum.isEmpty() && serviceAlnum.equals(alnum(candidateBase))) return true;
            if (!alnum(serviceBase).isEmpty() && alnum(serviceBase).equals(candidateAlnum)) return true;
        }
        return false;
    }

    /** Hostname de l'URL du site (sans www.). */
    public String host() {
        return extractHost(url);
    }

    static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        s = s.replace("https://", "").replace("http://", "");
        if (s.startsWith("www.")) s = s.substring(4);
        // Coupe le chemin si une URL complète a été fournie comme serviceName
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        return s.trim();
    }

    static String extractHost(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return "";
        try {
            String withScheme = rawUrl.contains("://") ? rawUrl : "https://" + rawUrl;
            URI uri = URI.create(withScheme);
            String host = uri.getHost();
            if (host == null) return normalize(rawUrl);
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception e) {
            return normalize(rawUrl);
        }
    }

    /** Retire le dernier label TLD (.net, .com, …). */
    static String stripTld(String host) {
        if (host == null || host.isEmpty()) return "";
        int dot = host.lastIndexOf('.');
        if (dot <= 0) return host;
        return host.substring(0, dot);
    }

    static String alnum(String s) {
        if (s == null) return "";
        return s.replaceAll("[^a-z0-9]", "");
    }
}
