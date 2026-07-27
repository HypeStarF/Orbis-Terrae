package me.sdmannen.orbis_terrae.profile;

import java.util.List;
import java.util.Map;

/** Built-in immutable profiles available during the Phase 2 terrain prototype. */
public final class WorldProfiles {
    public static final WorldProfile GLOBAL_COMPACT = new WorldProfile(
            "global-compact",
            WorldProfile.HorizontalScale.GLOBAL_COMPACT,
            -64,
            384,
            63,
            new WorldProfile.PiecewiseLinearVerticalCurve(List.of(
                    new WorldProfile.ControlPoint(-11_000.0, -50.0),
                    new WorldProfile.ControlPoint(0.0, 0.0),
                    new WorldProfile.ControlPoint(500.0, 45.0),
                    new WorldProfile.ControlPoint(2_000.0, 130.0),
                    new WorldProfile.ControlPoint(9_000.0, 240.0))));

    public static final WorldProfile GLOBAL_SURVIVAL = new WorldProfile(
            "global-survival",
            WorldProfile.HorizontalScale.GLOBAL_SURVIVAL,
            -64,
            384,
            63,
            new WorldProfile.PiecewiseLinearVerticalCurve(List.of(
                    new WorldProfile.ControlPoint(-11_000.0, -56.0),
                    new WorldProfile.ControlPoint(0.0, 0.0),
                    new WorldProfile.ControlPoint(500.0, 55.0),
                    new WorldProfile.ControlPoint(2_000.0, 155.0),
                    new WorldProfile.ControlPoint(9_000.0, 250.0))));

    private static final Map<String, WorldProfile> PROFILES = Map.of(
            GLOBAL_COMPACT.id(), GLOBAL_COMPACT,
            GLOBAL_SURVIVAL.id(), GLOBAL_SURVIVAL);

    private WorldProfiles() {
    }

    public static WorldProfile require(String id) {
        WorldProfile profile = PROFILES.get(id);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown world profile: " + id);
        }
        return profile;
    }

    public static List<WorldProfile> builtIn() {
        return List.of(GLOBAL_COMPACT, GLOBAL_SURVIVAL);
    }
}
