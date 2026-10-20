package se.smarthome.inventory.ui;

import javafx.application.Application;

/** A plain entry point also works when JavaFX is on the classpath. */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        Application.launch(InventoryApp.class, args);
    }
}
