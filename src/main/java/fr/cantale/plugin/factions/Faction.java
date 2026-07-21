package fr.cantale.plugin.factions;

import java.util.*;

public class Faction {
    private int id;
    private String name;
    private String tag;
    private String description;
    private UUID leaderUuid;
    private long createdAt;
    private double balance;
    private int power;
    private String type; // faction, country, empire
    private Set<UUID> members;
    private Map<UUID, FactionRank> memberRanks;
    private List<Claim> claims;
    private Double spawnX;
    private Double spawnY;
    private Double spawnZ;
    private String spawnWorld;
    private long secretUntil;

    public Faction(int id, String name, String tag, String description, UUID leaderUuid, long createdAt, double balance, int power, String type) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.description = description;
        this.leaderUuid = leaderUuid;
        this.createdAt = createdAt;
        this.balance = balance;
        this.power = power;
        this.type = type;
        this.members = new HashSet<>();
        this.memberRanks = new HashMap<>();
        this.claims = new ArrayList<>();
    }

    public Faction(String name, String tag, String description, UUID leaderUuid) {
        this(0, name, tag, description, leaderUuid, System.currentTimeMillis() / 1000, 0.0, 0, "faction");
        this.members.add(leaderUuid);
        this.memberRanks.put(leaderUuid, FactionRank.LEADER);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeaderUuid(UUID leaderUuid) {
        this.leaderUuid = leaderUuid;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public int getPower() {
        return power;
    }

    public void setPower(int power) {
        this.power = power;
    }

    public void addPower(int amount) {
        this.power += amount;
    }

    public void removePower(int amount) {
        this.power = Math.max(0, this.power - amount);
    }

    // Claims max = pouvoir / 10, minimum 5 pour permettre quelques claims dès la création
    public int getMaxClaims() {
        return Math.max(5, power / 10);
    }

    // Fhomes pool = pouvoir / 20, minimum 1
    public int getMaxFhomes() {
        return Math.max(1, power / 20);
    }

    // Warps : 1 à 60, puis +1 tous les +30
    public int getMaxWarps() {
        if (power < 60) return 1;
        return 1 + (power - 60) / 30;
    }

    // Tiers de faction
    public String getTier() {
        if (type.equals("empire")) return "Empire";
        if (type.equals("country")) return "Pays";
        if (getMemberCount() >= 100) return "Empire";
        if (getMemberCount() >= 50) return "Pays";
        return "Faction";
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public void addMember(UUID uuid, FactionRank rank) {
        members.add(uuid);
        memberRanks.put(uuid, rank);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        memberRanks.remove(uuid);
    }

    public FactionRank getMemberRank(UUID uuid) {
        return memberRanks.getOrDefault(uuid, FactionRank.RECRUIT);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isLeader(UUID uuid) {
        return leaderUuid.equals(uuid);
    }

    public boolean isOfficier(UUID uuid) {
        return getMemberRank(uuid).isAtLeast(FactionRank.OFFICER);
    }

    public Map<UUID, FactionRank> getMemberRanks() {
        return memberRanks;
    }

    public int getMemberCount() {
        return members.size();
    }

    public List<Claim> getClaims() {
        return claims;
    }

    public void addClaim(Claim claim) {
        claims.add(claim);
    }

    public void removeClaim(Claim claim) {
        claims.remove(claim);
    }

    public int getClaimCount() {
        return claims.size();
    }

    public boolean isCountry() {
        return "country".equals(type);
    }

    public boolean isEmpire() {
        return "empire".equals(type);
    }

    public void checkPromotion() {
        int count = getMemberCount();
        if (count >= 100 && !isEmpire()) {
            this.type = "empire";
        } else if (count >= 50 && !isCountry() && !isEmpire()) {
            this.type = "country";
        }
    }

    public Double getSpawnX() {
        return spawnX;
    }

    public void setSpawnX(Double spawnX) {
        this.spawnX = spawnX;
    }

    public Double getSpawnY() {
        return spawnY;
    }

    public void setSpawnY(Double spawnY) {
        this.spawnY = spawnY;
    }

    public Double getSpawnZ() {
        return spawnZ;
    }

    public void setSpawnZ(Double spawnZ) {
        this.spawnZ = spawnZ;
    }

    public String getSpawnWorld() {
        return spawnWorld;
    }

    public void setSpawnWorld(String spawnWorld) {
        this.spawnWorld = spawnWorld;
    }

    public long getSecretUntil() {
        return secretUntil;
    }

    public void setSecretUntil(long secretUntil) {
        this.secretUntil = secretUntil;
    }

    public boolean isSecret() {
        return System.currentTimeMillis() / 1000 < secretUntil;
    }
}
