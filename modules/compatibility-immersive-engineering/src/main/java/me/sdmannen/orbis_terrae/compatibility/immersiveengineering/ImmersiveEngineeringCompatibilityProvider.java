package me.sdmannen.orbis_terrae.compatibility.immersiveengineering;

import me.sdmannen.orbis_terrae.compatibility.api.CompatibilityProvider;

/** Placeholder provider; no Immersive Engineering classes are linked during Phase 0. */
public final class ImmersiveEngineeringCompatibilityProvider implements CompatibilityProvider {
    @Override
    public String providerId() {
        return "orbis_terrae:immersive_engineering";
    }

    @Override
    public String targetModId() {
        return "immersiveengineering";
    }
}
