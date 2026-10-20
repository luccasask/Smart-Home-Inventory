package se.smarthome.inventory.domain;

/** Common base for places and physical objects. */
public abstract class Item {
    private final Long id;
    private String name;
    private String description = "";
    private Item parent;

    protected Item(String name) {
        this(null, name);
    }

    protected Item(Long id, String name) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("ID måste vara positivt.");
        }
        this.id = id;
        rename(name);
    }

    /** Null until persisted. SQLite is responsible for generating IDs. */
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Namn måste anges.");
        }
        this.name = name.strip();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description.strip();
    }

    /** Null means that this item is at the root of the inventory. */
    public Item getParent() {
        return parent;
    }

    /** Validate the whole ancestor chain before changing the relation. */
    public void moveTo(Item newParent) {
        for (Item ancestor = newParent; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor == this || (id != null && id.equals(ancestor.id))) {
                throw new IllegalArgumentException("Ett objekt får inte placeras under sig självt eller sitt innehåll.");
            }
        }
        parent = newParent;
    }
}
