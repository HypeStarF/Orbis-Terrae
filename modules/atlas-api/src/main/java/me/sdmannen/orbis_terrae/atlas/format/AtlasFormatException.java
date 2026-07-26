package me.sdmannen.orbis_terrae.atlas.format;

import java.io.IOException;

public final class AtlasFormatException extends IOException {
    private static final long serialVersionUID = 1L;

    public AtlasFormatException(String message) {
        super(message);
    }
}
