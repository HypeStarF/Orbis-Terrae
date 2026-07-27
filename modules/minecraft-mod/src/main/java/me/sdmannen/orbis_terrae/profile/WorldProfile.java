package me.sdmannen.orbis_terrae.profile;

import java.util.List;
import java.util.Objects;

/** Immutable geographic and vertical settings locked into an Orbis Terrae world. */
public record WorldProfile(
        String id,
        HorizontalScale horizontalScale,
        int minimumY,
        int height,
        int seaLevel,
        PiecewiseLinearVerticalCurve verticalCurve) {
    public WorldProfile {
        id = validateId(id);
        Objects.requireNonNull(horizontalScale, "horizontalScale");
        Objects.requireNonNull(verticalCurve, "verticalCurve");
        if (height <= 0 || height % 16 != 0) {
            throw new IllegalArgumentException("Height must be a positive multiple of 16");
        }
        long maximumExclusive = (long) minimumY + height;
        if (maximumExclusive > Integer.MAX_VALUE || maximumExclusive < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("Dimension height exceeds integer coordinates");
        }
        if (seaLevel < minimumY || seaLevel >= maximumExclusive) {
            throw new IllegalArgumentException("Sea level must be inside the configured dimension");
        }
    }

    public int maximumY() {
        return minimumY + height - 1;
    }

    public int terrainY(double realElevationMetres) {
        long transformed = Math.round(seaLevel + verticalCurve.transform(realElevationMetres));
        return (int) Math.max(minimumY, Math.min(maximumY(), transformed));
    }

    private static String validateId(String id) {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[a-z0-9][a-z0-9_-]*")) {
            throw new IllegalArgumentException("Profile id must use lowercase path-safe characters");
        }
        return id;
    }

    /** Supported horizontal Earth scales for the first terrain prototype. */
    public enum HorizontalScale {
        GLOBAL_COMPACT("global-compact", 2_000),
        GLOBAL_SURVIVAL("global-survival", 1_000);

        private static final double EARTH_WIDTH_METRES = 40_075_017.0;
        private static final double EARTH_HEIGHT_METRES = 20_003_931.0;

        private final String id;
        private final int metresPerBlock;
        private final int projectedWidthBlocks;
        private final int projectedHeightBlocks;

        HorizontalScale(String id, int metresPerBlock) {
            this.id = id;
            this.metresPerBlock = metresPerBlock;
            this.projectedWidthBlocks = evenCeiling(EARTH_WIDTH_METRES / metresPerBlock);
            this.projectedHeightBlocks = evenCeiling(EARTH_HEIGHT_METRES / metresPerBlock);
        }

        public String id() {
            return id;
        }

        public int metresPerBlock() {
            return metresPerBlock;
        }

        public int projectedWidthBlocks() {
            return projectedWidthBlocks;
        }

        public int projectedHeightBlocks() {
            return projectedHeightBlocks;
        }

        public static HorizontalScale fromId(String id) {
            Objects.requireNonNull(id, "id");
            for (HorizontalScale scale : values()) {
                if (scale.id.equals(id)) {
                    return scale;
                }
            }
            throw new IllegalArgumentException("Unknown horizontal scale: " + id);
        }

        private static int evenCeiling(double value) {
            int blocks = Math.toIntExact((long) Math.ceil(value));
            return blocks % 2 == 0 ? blocks : Math.incrementExact(blocks);
        }
    }

    /** One point in the real-elevation to Minecraft-block transformation. */
    public record ControlPoint(double realMetres, double blocks) {
        public ControlPoint {
            if (!Double.isFinite(realMetres) || !Double.isFinite(blocks)) {
                throw new IllegalArgumentException("Vertical control points must be finite");
            }
        }
    }

    /** Strictly ordered piecewise-linear vertical transformation. */
    public static final class PiecewiseLinearVerticalCurve {
        private final List<ControlPoint> controlPoints;

        public PiecewiseLinearVerticalCurve(List<ControlPoint> controlPoints) {
            Objects.requireNonNull(controlPoints, "controlPoints");
            if (controlPoints.size() < 2) {
                throw new IllegalArgumentException("A vertical curve requires at least two control points");
            }
            List<ControlPoint> copiedPoints = List.copyOf(controlPoints);
            for (int index = 0; index < copiedPoints.size(); index++) {
                Objects.requireNonNull(copiedPoints.get(index), "controlPoint");
                if (index > 0
                        && copiedPoints.get(index - 1).realMetres()
                        >= copiedPoints.get(index).realMetres()) {
                    throw new IllegalArgumentException("Vertical control points must be strictly ordered");
                }
            }
            this.controlPoints = copiedPoints;
        }

        public List<ControlPoint> controlPoints() {
            return controlPoints;
        }

        public double transform(double realElevationMetres) {
            if (!Double.isFinite(realElevationMetres)) {
                throw new IllegalArgumentException("Elevation must be finite");
            }
            if (realElevationMetres <= controlPoints.getFirst().realMetres()) {
                return interpolate(realElevationMetres, controlPoints.get(0), controlPoints.get(1));
            }
            for (int index = 1; index < controlPoints.size(); index++) {
                ControlPoint upper = controlPoints.get(index);
                if (realElevationMetres <= upper.realMetres()) {
                    return interpolate(realElevationMetres, controlPoints.get(index - 1), upper);
                }
            }
            int lastIndex = controlPoints.size() - 1;
            return interpolate(
                    realElevationMetres,
                    controlPoints.get(lastIndex - 1),
                    controlPoints.get(lastIndex));
        }

        private static double interpolate(double input, ControlPoint lower, ControlPoint upper) {
            double fraction = (input - lower.realMetres())
                    / (upper.realMetres() - lower.realMetres());
            return lower.blocks() + fraction * (upper.blocks() - lower.blocks());
        }
    }
}
