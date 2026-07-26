package me.sdmannen.orbis_terrae.atlas.compiler;

/** Phase 0 command-line entry point. GIS import commands arrive in Phase 1. */
public final class AtlasCompilerMain {
    private AtlasCompilerMain() {
    }

    public static void main(String[] args) {
        if (args.length == 1 && "--version".equals(args[0])) {
            System.out.println("Orbis Terrae Atlas Compiler 0.0.1-phase0");
            return;
        }
        System.out.println("Atlas compiler scaffold is installed. Phase 1 commands are not implemented.");
    }
}
