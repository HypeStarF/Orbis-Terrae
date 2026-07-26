package me.sdmannen.orbis_terrae.compatibility.mekanism;

import me.sdmannen.orbis_terrae.compatibility.api.CompatibilityProvider;

/** Placeholder provider; no Mekanism classes are linked during Phase 0. */
public final class MekanismCompatibilityProvider implements CompatibilityProvider {
    @Override
    public String providerId() {
        return "orbis_terrae:mekanism";
    }

    @Override
    public String targetModId() {
        return "mekanism";
    }
}
