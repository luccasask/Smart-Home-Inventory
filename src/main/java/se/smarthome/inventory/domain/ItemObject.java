package se.smarthome.inventory.domain;

/** A physical object that can also act as a container. */
public final class ItemObject extends Item {
    public ItemObject(String name) {
        super(name);
    }

    /** Reconstruct a persisted object. */
    public ItemObject(long id, String name) {
        super(id, name);
    }
}
