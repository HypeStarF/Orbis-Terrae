package me.sdmannen.orbis_terrae.atlas.store;

import java.io.IOException;

/** Indicates that an atlas directory or tile cannot be used safely. */
public final class AtlasAccessException extends IOException {
    private static final long serialVersionUID = 1L;

    public AtlasAccessException(String message) {
        super(message);
    }

    public AtlasAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
