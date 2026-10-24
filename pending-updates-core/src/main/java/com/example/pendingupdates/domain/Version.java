package com.example.pendingupdates.domain;

/** A template version: a positive integer, monotonic per template (linear, no branches). */
public record Version(int value) implements Comparable<Version> {

    public Version {
        if (value <= 0) {
            throw new IllegalArgumentException("version must be positive: " + value);
        }
    }

    public boolean isNewerThan(Version other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Version other) {
        return Integer.compare(value, other.value);
    }
}
