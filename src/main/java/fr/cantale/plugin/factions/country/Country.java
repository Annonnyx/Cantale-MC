package fr.cantale.plugin.factions.country;

import fr.cantale.plugin.factions.Faction;
import java.util.*;

public class Country extends Faction {
    private final Set<Integer> alliedFactions;
    private final Set<UUID> governmentMembers;
    private double taxRate;

    public Country(String name, String tag, String description, UUID leader) {
        super(name, tag, description, leader);
        this.alliedFactions = new HashSet<>();
        this.governmentMembers = new HashSet<>();
        this.taxRate = 0.05;
        setType("country");
    }

    public void addAlly(int factionId) {
        alliedFactions.add(factionId);
    }

    public void removeAlly(int factionId) {
        alliedFactions.remove(factionId);
    }

    public boolean isAllied(int factionId) {
        return alliedFactions.contains(factionId);
    }

    public void addGovernmentMember(UUID uuid) {
        governmentMembers.add(uuid);
    }

    public boolean isGovernmentMember(UUID uuid) {
        return governmentMembers.contains(uuid);
    }

    public double getTaxRate() { return taxRate; }
    public void setTaxRate(double rate) { this.taxRate = Math.min(rate, 0.20); }

    public Set<Integer> getAlliedFactions() { return Set.copyOf(alliedFactions); }
}
