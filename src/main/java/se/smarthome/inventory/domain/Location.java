package se.smarthome.inventory.domain;

/** A physical place, for example a home or a room. */
public final class Location extends Item {
    public Location(String name) {
        super(name);
    }

    /** Reconstruct a persisted location. */
    public Location(long id, String name) {
        super(id, name);
    }
}
