package me.sdmannen.orbis_terrae.atlas.selection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import me.sdmannen.orbis_terrae.atlas.geo.GeoBounds;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifest;
import me.sdmannen.orbis_terrae.atlas.sampling.ElevationSampler;
import me.sdmannen.orbis_terrae.atlas.sampling.LandMaskSampler;
import me.sdmannen.orbis_terrae.atlas.store.AtlasDirectory;
import me.sdmannen.orbis_terrae.atlas.store.AtlasLayer;

/**
 * Selects the highest-resolution available atlas independently for elevation and land-mask samples.
 *
 * <p>Atlas directories may be supplied in any resolution order. Candidate layers are ranked by their
 * angular sample area, with constructor order used as a stable tie-breaker. Missing layers, no-data
 * elevation samples, and unreadable preferred tiles fall back to the next covering atlas.
 */
public final class AtlasStack {
    private static final double EDGE_EPSILON = 1.0e-10;

    private final List<String> atlasIds;
    private final List<ElevationCandidate> elevationCandidates;
    private final List<LandMaskCandidate> landMaskCandidates;

    /** Creates a stack from one or more opened atlas directories. */
    public AtlasStack(List<AtlasDirectory> atlases) {
        Objects.requireNonNull(atlases, "atlases");
        if (atlases.isEmpty()) {
            throw new IllegalArgumentException("Atlas stack requires at least one atlas");
        }

        List<String> ids = new ArrayList<>(atlases.size());
        List<ElevationCandidate> elevations = new ArrayList<>();
        List<LandMaskCandidate> landMasks = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (int order = 0; order < atlases.size(); order++) {
            AtlasDirectory atlas = Objects.requireNonNull(atlases.get(order), "atlases contains null");
            String atlasId = atlas.manifest().atlasId();
            if (!seenIds.add(atlasId)) {
                throw new IllegalArgumentException("Duplicate atlas id in stack: " + atlasId);
            }
            ids.add(atlasId);

            AtlasLayer elevation = uniqueLayer(atlas, AtlasManifest.LayerType.ELEVATION);
            if (elevation != null) {
                LayerCoverage coverage = LayerCoverage.from(atlas.manifest().bounds(), elevation);
                elevations.add(new ElevationCandidate(
                        atlasId,
                        order,
                        coverage,
                        new ElevationSampler(atlas, elevation.id())));
            }

            AtlasLayer landMask = uniqueLayer(atlas, AtlasManifest.LayerType.LAND_MASK);
            if (landMask != null) {
                LayerCoverage coverage = LayerCoverage.from(atlas.manifest().bounds(), landMask);
                landMasks.add(new LandMaskCandidate(
                        atlasId,
                        order,
                        coverage,
                        new LandMaskSampler(atlas, landMask.id())));
            }
        }

        Comparator<RankedCandidate> ranking = Comparator
                .comparingDouble(RankedCandidate::angularSampleArea)
                .thenComparingInt(RankedCandidate::inputOrder);
        elevations.sort(ranking);
        landMasks.sort(ranking);

        this.atlasIds = List.copyOf(ids);
        this.elevationCandidates = List.copyOf(elevations);
        this.landMaskCandidates = List.copyOf(landMasks);
    }

    /** Convenience factory retaining the supplied order for equal-resolution tie-breaking. */
    public static AtlasStack of(AtlasDirectory... atlases) {
        Objects.requireNonNull(atlases, "atlases");
        return new AtlasStack(Arrays.asList(atlases.clone()));
    }

    /** Returns atlas identifiers in constructor order. */
    public List<String> atlasIds() {
        return atlasIds;
    }

    /** Samples nearest-neighbour elevation, falling back through lower-resolution atlases. */
    public Optional<ElevationSample> sampleNearestElevationMetres(double latitude, double longitude)
            throws IOException {
        GeographicCoordinate coordinate = GeographicCoordinate.normalize(latitude, longitude);
        List<IOException> failures = new ArrayList<>();

        for (ElevationCandidate candidate : elevationCandidates) {
            Optional<SampleCoordinate> projected = candidate.coverage().project(coordinate);
            if (projected.isEmpty()) {
                continue;
            }
            SampleCoordinate sample = projected.orElseThrow();
            try {
                OptionalInt value = candidate.sampler().sampleNearestMetres(
                        sample.latitude(), sample.longitude());
                if (value.isPresent()) {
                    return Optional.of(new ElevationSample(value.getAsInt(), candidate.atlasId()));
                }
            } catch (IOException exception) {
                failures.add(candidateFailure(candidate.atlasId(), "elevation", exception));
            }
        }

        throwIfFailures("No readable atlas produced a nearest elevation sample", failures);
        return Optional.empty();
    }

    /** Samples bilinear elevation, falling back through lower-resolution atlases. */
    public Optional<ElevationSample> sampleBilinearElevationMetres(double latitude, double longitude)
            throws IOException {
        GeographicCoordinate coordinate = GeographicCoordinate.normalize(latitude, longitude);
        List<IOException> failures = new ArrayList<>();

        for (ElevationCandidate candidate : elevationCandidates) {
            Optional<SampleCoordinate> projected = candidate.coverage().project(coordinate);
            if (projected.isEmpty()) {
                continue;
            }
            SampleCoordinate sample = projected.orElseThrow();
            try {
                OptionalDouble value = candidate.sampler().sampleBilinearMetres(
                        sample.latitude(), sample.longitude());
                if (value.isPresent()) {
                    return Optional.of(new ElevationSample(value.getAsDouble(), candidate.atlasId()));
                }
            } catch (IOException exception) {
                failures.add(candidateFailure(candidate.atlasId(), "elevation", exception));
            }
        }

        throwIfFailures("No readable atlas produced a bilinear elevation sample", failures);
        return Optional.empty();
    }

    /** Samples nearest-neighbour land classification from the best available covering atlas. */
    public Optional<LandMaskSample> sampleLandMask(double latitude, double longitude) throws IOException {
        GeographicCoordinate coordinate = GeographicCoordinate.normalize(latitude, longitude);
        List<IOException> failures = new ArrayList<>();

        for (LandMaskCandidate candidate : landMaskCandidates) {
            Optional<SampleCoordinate> projected = candidate.coverage().project(coordinate);
            if (projected.isEmpty()) {
                continue;
            }
            SampleCoordinate sample = projected.orElseThrow();
            try {
                boolean land = candidate.sampler().isLand(sample.latitude(), sample.longitude());
                return Optional.of(new LandMaskSample(land, candidate.atlasId()));
            } catch (IOException exception) {
                failures.add(candidateFailure(candidate.atlasId(), "land mask", exception));
            }
        }

        throwIfFailures("No readable atlas produced a land-mask sample", failures);
        return Optional.empty();
    }

    private static AtlasLayer uniqueLayer(AtlasDirectory atlas, AtlasManifest.LayerType type) {
        AtlasLayer match = null;
        for (AtlasLayer layer : atlas.layers()) {
            if (layer.type() != type) {
                continue;
            }
            if (match != null) {
                throw new IllegalArgumentException(
                        "Atlas " + atlas.manifest().atlasId() + " declares multiple "
                                + type.jsonValue() + " layers");
            }
            match = layer;
        }
        return match;
    }

    private static IOException candidateFailure(String atlasId, String layer, IOException cause) {
        return new IOException("Atlas " + atlasId + " failed while sampling " + layer, cause);
    }

    private static void throwIfFailures(String message, List<IOException> failures) throws IOException {
        if (failures.isEmpty()) {
            return;
        }
        IOException combined = new IOException(message, failures.get(0));
        for (int index = 1; index < failures.size(); index++) {
            combined.addSuppressed(failures.get(index));
        }
        throw combined;
    }

    /** Elevation result together with the atlas that supplied it. */
    public record ElevationSample(double metres, String atlasId) {
        public ElevationSample {
            if (!Double.isFinite(metres)) {
                throw new IllegalArgumentException("Elevation must be finite");
            }
            atlasId = requireAtlasId(atlasId);
        }
    }

    /** Land/water result together with the atlas that supplied it. */
    public record LandMaskSample(boolean land, String atlasId) {
        public LandMaskSample {
            atlasId = requireAtlasId(atlasId);
        }
    }

    private static String requireAtlasId(String atlasId) {
        Objects.requireNonNull(atlasId, "atlasId");
        if (atlasId.isBlank()) {
            throw new IllegalArgumentException("atlasId must not be blank");
        }
        return atlasId;
    }

    private interface RankedCandidate {
        double angularSampleArea();

        int inputOrder();
    }

    private record ElevationCandidate(
            String atlasId,
            int inputOrder,
            LayerCoverage coverage,
            ElevationSampler sampler) implements RankedCandidate {
        @Override
        public double angularSampleArea() {
            return coverage.angularSampleArea();
        }
    }

    private record LandMaskCandidate(
            String atlasId,
            int inputOrder,
            LayerCoverage coverage,
            LandMaskSampler sampler) implements RankedCandidate {
        @Override
        public double angularSampleArea() {
            return coverage.angularSampleArea();
        }
    }

    private record GeographicCoordinate(double latitude, double longitude) {
        static GeographicCoordinate normalize(double latitude, double longitude) {
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
                throw new IllegalArgumentException("Latitude and longitude must be finite");
            }
            double clampedLatitude = clamp(latitude, -90.0, 90.0);
            double wrappedLongitude = longitude % 360.0;
            if (wrappedLongitude < -180.0) {
                wrappedLongitude += 360.0;
            } else if (wrappedLongitude >= 180.0) {
                wrappedLongitude -= 360.0;
            }
            if (wrappedLongitude == -0.0) {
                wrappedLongitude = 0.0;
            }
            return new GeographicCoordinate(clampedLatitude, wrappedLongitude);
        }
    }

    private record SampleCoordinate(double latitude, double longitude) {
    }

    private record LayerCoverage(
            GeoBounds sampleBounds,
            double westEdge,
            double southEdge,
            double eastEdge,
            double northEdge,
            double angularSampleArea,
            boolean allLongitudes) {
        static LayerCoverage from(GeoBounds bounds, AtlasLayer layer) {
            double longitudeSpacing = (bounds.east() - bounds.west())
                    / (layer.definition().gridWidthSamples() - 1);
            double latitudeSpacing = (bounds.north() - bounds.south())
                    / (layer.definition().gridHeightSamples() - 1);
            double westEdge = Math.max(-180.0, bounds.west() - longitudeSpacing / 2.0);
            double eastEdge = Math.min(180.0, bounds.east() + longitudeSpacing / 2.0);
            double southEdge = Math.max(-90.0, bounds.south() - latitudeSpacing / 2.0);
            double northEdge = Math.min(90.0, bounds.north() + latitudeSpacing / 2.0);
            boolean allLongitudes = westEdge <= -180.0 + EDGE_EPSILON
                    && eastEdge >= 180.0 - EDGE_EPSILON;
            return new LayerCoverage(
                    bounds,
                    westEdge,
                    southEdge,
                    eastEdge,
                    northEdge,
                    longitudeSpacing * latitudeSpacing,
                    allLongitudes);
        }

        Optional<SampleCoordinate> project(GeographicCoordinate coordinate) {
            if (coordinate.latitude() < southEdge - EDGE_EPSILON
                    || coordinate.latitude() > northEdge + EDGE_EPSILON) {
                return Optional.empty();
            }
            if (!allLongitudes
                    && (coordinate.longitude() < westEdge - EDGE_EPSILON
                            || coordinate.longitude() > eastEdge + EDGE_EPSILON)) {
                return Optional.empty();
            }
            return Optional.of(new SampleCoordinate(
                    clamp(coordinate.latitude(), sampleBounds.south(), sampleBounds.north()),
                    clamp(coordinate.longitude(), sampleBounds.west(), sampleBounds.east())));
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
