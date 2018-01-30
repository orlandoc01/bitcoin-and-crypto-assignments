package com.orlando;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {
    private int currentRound;
    private boolean[] followees;
    private int numOfFollowees = 1;
    final private int numRounds;
    final private HashMap<Transaction, Integer> txCounts = new HashMap<>();
    final private Set<Transaction> proposal = new HashSet<>();
    final Predicate<Candidate> isTrusted = candidate -> followees[candidate.sender];
    final Consumer<Candidate> addVote = candidate -> {
        int newCount = txCounts.getOrDefault(candidate.tx, 1) + 1;
        txCounts.put(candidate.tx, newCount);
    };

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        this.numRounds = numRounds;
        this.currentRound = 0;
    }

    public void setFollowees(boolean[] followees) {
        this.followees = followees;
        this.numOfFollowees = 0;
        for(boolean followee: followees) {
            this.numOfFollowees = followee ? this.numOfFollowees + 1 : this.numOfFollowees;
        }
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        pendingTransactions.stream().forEach(proposal::add);
    }

    public Set<Transaction> sendToFollowers() {
        return this.proposal;
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
        candidates.stream().filter(isTrusted).forEach(addVote);
        proposal.clear();
        txCounts.keySet().stream().filter(hasVotes()).forEach(proposal::add);
        currentRound++;
    }

    private Predicate<Transaction> hasVotes() {
        final int size = (numOfFollowees + 1) * (currentRound + 1);
        final double min = getRatio(this.currentRound);
        return tx -> ((double)txCounts.getOrDefault(tx, 0))/size >= min;
    }

    private double getRatio(int round) {
        return 0.0 * (round + 1) / (numRounds);
    }
}
