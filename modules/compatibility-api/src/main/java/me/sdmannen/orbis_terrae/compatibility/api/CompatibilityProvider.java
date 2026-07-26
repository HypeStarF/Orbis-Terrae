package me.sdmannen.orbis_terrae.compatibility.api;

/** Stable identifier contract for optional compatibility providers. */
public interface CompatibilityProvider {
    String providerId();

    String targetModId();
}
