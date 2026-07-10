package fr.cantale.plugin.factions.country;

import java.util.*;

public class Empire extends Country {
    private final Set<Integer> memberCountries;
    private final Map<Integer, Integer> revolutionVotes;
    private boolean revolutionActive;

    public Empire(String name, String tag, String description, UUID leader) {
        super(name, tag, description, leader);
        this.memberCountries = new HashSet<>();
        this.revolutionVotes = new HashMap<>();
        this.revolutionActive = false;
        setType("empire");
    }

    public void addCountry(int countryId) {
        if (getMemberCount() >= 100) {
            memberCountries.add(countryId);
        }
    }

    public void startRevolution() {
        if (!revolutionActive) {
            revolutionActive = true;
            revolutionVotes.clear();
        }
    }

    public void voteRevolution(UUID voter, int choice) {
        if (revolutionActive) {
            revolutionVotes.put(choice, revolutionVotes.getOrDefault(choice, 0) + 1);
        }
    }

    public boolean checkRevolutionSuccess() {
        if (!revolutionActive) return false;
        int total = revolutionVotes.values().stream().mapToInt(Integer::intValue).sum();
        int forRevolution = revolutionVotes.getOrDefault(1, 0);
        return forRevolution > (total * 0.6);
    }

    public void endRevolution() {
        revolutionActive = false;
        revolutionVotes.clear();
    }

    public boolean canFormAlliance(Empire other) {
        return (getMemberCount() + other.getMemberCount()) >= 150;
    }

    public boolean isRevolutionActive() { return revolutionActive; }
    public Set<Integer> getMemberCountries() { return Set.copyOf(memberCountries); }
}
