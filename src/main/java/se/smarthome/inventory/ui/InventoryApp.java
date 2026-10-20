package se.smarthome.inventory.ui;

import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import se.smarthome.inventory.domain.Item;
import se.smarthome.inventory.domain.ItemObject;
import se.smarthome.inventory.domain.Location;
import se.smarthome.inventory.persistence.InventoryDatabase;
import se.smarthome.inventory.persistence.ItemRepository;

import java.nio.file.Path;
import java.util.*;

public final class InventoryApp extends Application {
    private final TreeView<Item> tree = new TreeView<>();
    private final TableView<Item> table = new TableView<>();
    private final TextField search = new TextField();
    private final CheckBox recursive = new CheckBox("Visa även undernivåer");
    private final Label heading = new Label("Alla föremål");
    private final Label count = new Label();
    private final Label details = new Label("Välj ett föremål för att se dess placering.");
    private final Button edit = new Button("Redigera / flytta");
    private final Button delete = new Button("Ta bort");
    private ItemRepository repository;
    private List<Item> items = List.of();
    private Stage stage;
    private boolean refreshing;
    private final BorderPane root = new BorderPane();
    private final VBox searchPanel = new VBox(22);
    private final VBox resultPanel = new VBox(16);
    private final StackPane homePanel = new StackPane();
    private SplitPane browsePanel;
    private boolean browsing;
    private String activeQuery = "";

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        try {
            repository = new ItemRepository(new InventoryDatabase(databasePath()));
            buildWindow();
            reload(null);
            stage.show();
        } catch (Exception ex) {
            showError("Programmet kunde inte startas", ex);
            javafx.application.Platform.exit();
        }
    }

    private Path databasePath() {
        // Override only for isolated developer testing; regular users use LocalAppData.
        String override = System.getProperty("inventory.database");
        if (override != null) return Path.of(override);
        String local = System.getenv("LOCALAPPDATA");
        return (local == null ? Path.of(System.getProperty("user.home"), ".smart-home-inventory")
            : Path.of(local, "SmartHomeInventory")).resolve("inventory.db");
    }

    private void buildWindow() {
        Button add = new Button("+");
        add.setId("add-button");
        add.setAccessibleText("Lägg till föremål eller plats");
        add.setTooltip(new Tooltip("Lägg till med texten som namn"));
        add.getStyleClass().add("add-button");
        add.setOnAction(event -> openEditor(null));
        search.setPromptText("Sök efter namn, plats eller beskrivning…");
        search.setId("home-search");
        search.setAccessibleText("Sök efter föremål");
        search.setOnAction(event -> runSearch());
        search.textProperty().addListener((observable, before, after) -> {
            if (after.isBlank() && !browsing) showHome();
        });
        Button submit = new Button("Sök");
        submit.setId("search-button");
        submit.getStyleClass().add("search-button");
        submit.setOnAction(event -> runSearch());
        HBox searchRow = new HBox(8, search, submit, add);
        searchRow.setAlignment(Pos.CENTER);
        searchRow.getStyleClass().add("search-bar");
        HBox.setHgrow(search, Priority.ALWAYS);
        Button menu = new Button("☰  Meny");
        menu.setId("menu-button");
        menu.setAccessibleText("Öppna menyn för att bläddra i inventariet");
        menu.getStyleClass().add("menu-button");
        menu.setOnAction(event -> showBrowse());
        searchPanel.getChildren().addAll(searchRow, menu);
        searchPanel.setAlignment(Pos.CENTER);
        searchPanel.setMaxWidth(760);
        searchPanel.setPadding(new Insets(32));

        tree.setCellFactory(view -> new TreeCell<>() {
            @Override protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null ? "Alla föremål" : item.getName());
                setGraphic(null);
            }
        });
        tree.setId("browse-tree");
        tree.setAccessibleText("Bläddra bland platser och föremål");
        table.setId("results-table");
        table.setAccessibleText("Föremålslista");
        tree.getSelectionModel().selectedItemProperty().addListener((observable, before, after) -> {
            if (!refreshing) {
                updateTable();
                updateDetails();
            }
        });
        Label browse = new Label("DINA PLATSER OCH FÖREMÅL");
        browse.getStyleClass().add("section-title");
        VBox sidebar = new VBox(12, browse, tree);
        sidebar.setPadding(new Insets(18, 14, 18, 18));
        sidebar.setMinWidth(180);
        sidebar.setPrefWidth(250);
        VBox.setVgrow(tree, Priority.ALWAYS);

        addColumn("Namn", 190, Item::getName);
        addColumn("Typ", 95, item -> item instanceof Location ? "Plats" : "Föremål");
        addColumn("Placering", 330, item -> item.getParent() == null ? "På översta nivån"
            : InventoryViewModel.path(item.getParent()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Här är det tomt. Klicka på + för att lägga till ett föremål."));
        table.getSelectionModel().selectedItemProperty().addListener((observable, before, after) -> updateDetails());
        table.setRowFactory(view -> {
            TableRow<Item> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) openEditor(row.getItem());
            });
            return row;
        });
        recursive.setSelected(true);
        recursive.setOnAction(event -> updateTable());
        heading.getStyleClass().add("view-title");
        heading.setWrapText(true);
        count.getStyleClass().add("muted");
        HBox filters = new HBox(18, count, recursive);
        edit.setOnAction(event -> { if (selectedItem() != null) openEditor(selectedItem()); });
        delete.setOnAction(event -> deleteSelected());
        details.setWrapText(true);
        HBox actions = new HBox(10, edit, delete);
        resultPanel.getChildren().addAll(heading, filters, table, details, actions);
        resultPanel.setPadding(new Insets(24));
        resultPanel.getStyleClass().add("results-panel");
        VBox.setVgrow(table, Priority.ALWAYS);
        browsePanel = new SplitPane(sidebar);
        browsePanel.setDividerPositions(0.28);
        Scene scene = new Scene(root, 1050, 700);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("inventory.css")).toExternalForm());
        scene.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.F) {
                if (browsing) showHome();
                search.requestFocus();
            }
        });
        stage.setTitle("Hemregistret – Smart Home Inventory");
        stage.setMinWidth(820);
        stage.setMinHeight(560);
        stage.setScene(scene);
        showHome();
    }

    private void showHome() {
        browsing = false;
        activeQuery = "";
        detachResults();
        root.setTop(null);
        homePanel.getChildren().setAll(searchPanel);
        searchPanel.setMaxWidth(760);
        root.setCenter(homePanel);
        updateTable();
    }

    private void detachResults() {
        browsePanel.getItems().remove(resultPanel);
        root.setCenter(null);
    }

    private void runSearch() {
        if (search.getText().isBlank()) { showHome(); return; }
        browsing = false;
        activeQuery = search.getText().strip();
        detachResults();
        root.setTop(null);
        homePanel.getChildren().clear();
        // Remove the shared search panel from its previous parent before reusing it.
        searchPanel.setMaxWidth(Double.MAX_VALUE);
        root.setTop(searchPanel);
        root.setCenter(resultPanel);
        recursive.setVisible(false);
        recursive.setManaged(false);
        updateTable();
    }

    private void showBrowse() {
        browsing = true;
        detachResults();
        root.setTop(null);
        Button back = new Button("← Till startsidan");
        back.setId("back-button");
        back.setOnAction(event -> showHome());
        Button add = new Button("+ Lägg till");
        add.getStyleClass().add("primary");
        add.setOnAction(event -> openEditor(null));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox navigation = new HBox(16, back, spacer, add);
        navigation.setPadding(new Insets(24, 24, 0, 24));
        root.setTop(navigation);
        browsePanel.getItems().add(resultPanel);
        root.setCenter(browsePanel);
        recursive.setVisible(true);
        recursive.setManaged(true);
        updateTable();
    }

    private void addColumn(String title, int width, java.util.function.Function<Item, String> value) {
        TableColumn<Item, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        table.getColumns().add(column);
    }

    private Item currentContainer() {
        TreeItem<Item> node = tree.getSelectionModel().getSelectedItem();
        return node == null ? null : node.getValue();
    }

    private Item selectedItem() {
        Item row = table.getSelectionModel().getSelectedItem();
        return row == null && browsing ? currentContainer() : row;
    }

    private void reload(Long selectedId) throws Exception {
        Set<Long> expanded = new HashSet<>();
        collectExpanded(tree.getRoot(), expanded);
        items = repository.findAll();
        refreshing = true;
        try {
            TreeItem<Item> root = new TreeItem<>();
            root.setExpanded(true);
            Map<Long, TreeItem<Item>> nodes = new LinkedHashMap<>();
            items.stream().sorted(Comparator.comparing(Item::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(item -> nodes.put(item.getId(), new TreeItem<>(item)));
            for (var node : nodes.values()) {
                node.setExpanded(expanded.contains(node.getValue().getId()));
                Item parent = node.getValue().getParent();
                (parent == null ? root : nodes.get(parent.getId())).getChildren().add(node);
            }
            tree.setRoot(root);
            TreeItem<Item> selected = selectedId == null ? root : nodes.getOrDefault(selectedId, root);
            for (TreeItem<Item> node = selected; node != null; node = node.getParent()) node.setExpanded(true);
            tree.getSelectionModel().select(selected);
        } finally {
            refreshing = false;
        }
        updateTable();
        updateDetails();
    }

    private void collectExpanded(TreeItem<Item> node, Set<Long> expanded) {
        if (node == null) return;
        if (node.isExpanded() && node.getValue() != null) expanded.add(node.getValue().getId());
        node.getChildren().forEach(child -> collectExpanded(child, expanded));
    }

    private void updateTable() {
        if (refreshing) return;
        Item container = browsing ? currentContainer() : null;
        heading.setText(browsing ? (container == null ? "Alla föremål" : InventoryViewModel.path(container))
            : "Sökresultat för ”" + activeQuery + "”");
        List<Item> matches = InventoryViewModel.filter(items, container, browsing ? recursive.isSelected() : true,
            browsing ? "" : activeQuery);
        table.setItems(FXCollections.observableArrayList(matches));
        count.setText(matches.size() + " visas · " + items.size() + " totalt");
        table.setPlaceholder(new Label(items.isEmpty()
            ? "Här är det tomt. Klicka på + för att lägga till ett föremål."
            : "Inga föremål matchar. Prova en annan sökning eller öppna menyn."));
        updateDetails();
    }

    private void updateDetails() {
        Item item = selectedItem();
        edit.setDisable(item == null);
        delete.setDisable(item == null);
        details.setText(item == null ? "Välj ett föremål för att se dess placering."
            : "Valt: " + InventoryViewModel.path(item) + (item.getDescription().isBlank() ? "" : "\n" + item.getDescription()));
    }

    private record Placement(Item item) {
        @Override public String toString() {
            return item == null ? "Översta nivån" : InventoryViewModel.path(item) + " (#" + item.getId() + ")";
        }
    }

    private void openEditor(Item existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle(existing == null ? "Lägg till föremål eller plats" : "Redigera / flytta");
        ButtonType save = new ButtonType("Spara", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Avbryt");
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setId("editor-cancel");
        TextField name = new TextField(existing == null ? (browsing ? "" : search.getText().strip()) : existing.getName());
        name.setId("editor-name");
        name.setPromptText("Till exempel Sovrum, Verktygslåda eller Laddare");
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("Föremål", "Plats"));
        type.setValue(existing instanceof Location ? "Plats" : "Föremål");
        type.setDisable(existing != null);
        ComboBox<Placement> placement = new ComboBox<>();
        placement.getItems().add(new Placement(null));
        Item initialParent = existing == null ? (browsing ? currentContainer() : null) : existing.getParent();
        Placement initial = placement.getItems().getFirst();
        for (Item item : items.stream().sorted(Comparator.comparing(InventoryViewModel::path)).toList()) {
            if (existing != null && (item.getId().equals(existing.getId()) || InventoryViewModel.isWithin(item, existing))) continue;
            Placement option = new Placement(item);
            placement.getItems().add(option);
            if (initialParent != null && initialParent.getId().equals(item.getId())) initial = option;
        }
        placement.setValue(initial);
        placement.setMaxWidth(Double.MAX_VALUE);
        TextArea description = new TextArea(existing == null ? "" : existing.getDescription());
        description.setPrefRowCount(3);
        description.setWrapText(true);
        Label error = new Label();
        error.setStyle("-fx-text-fill: #b42318;");
        error.setWrapText(true);
        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(12);
        form.setPadding(new Insets(18));
        addFormRow(form, 0, "Namn *", name);
        addFormRow(form, 1, "Typ", type);
        addFormRow(form, 2, "Placering", placement);
        addFormRow(form, 3, "Beskrivning", description);
        form.add(error, 0, 4, 2, 1);
        name.setPrefWidth(400);
        GridPane.setHgrow(name, Priority.ALWAYS);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("inventory.css").toExternalForm());
        dialog.getDialogPane().lookupButton(save).getStyleClass().add("primary");
        dialog.getDialogPane().lookupButton(save).setId("editor-save");
        dialog.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                // Edit a copy. Failed validation never mutates the displayed snapshot.
                Item draft = existing == null
                    ? ("Plats".equals(type.getValue()) ? new Location(name.getText()) : new ItemObject(name.getText()))
                    : (existing instanceof Location ? new Location(existing.getId(), name.getText())
                        : new ItemObject(existing.getId(), name.getText()));
                draft.setDescription(description.getText());
                draft.moveTo(placement.getValue().item());
                Item saved = repository.save(draft);
                reload(saved.getParent() == null ? null : saved.getParent().getId());
                table.getItems().stream().filter(item -> item.getId().equals(saved.getId())).findFirst()
                    .ifPresent(item -> { table.getSelectionModel().select(item); table.scrollTo(item); });
            } catch (Exception ex) {
                event.consume();
                error.setText("Kunde inte spara: " + ex.getMessage());
            }
        });
        dialog.setOnShown(event -> name.requestFocus());
        dialog.showAndWait();
    }

    private void addFormRow(GridPane form, int row, String text, Control field) {
        Label label = new Label(text);
        label.setLabelFor(field);
        field.setAccessibleText(text);
        form.addRow(row, label, field);
    }

    private void deleteSelected() {
        Item item = selectedItem();
        if (item == null) return;
        if (items.stream().anyMatch(child -> child.getParent() != null && child.getParent().getId().equals(item.getId()))) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Flytta eller ta bort innehållet först. Föremålet innehåller andra föremål.", ButtonType.OK);
            alert.initOwner(stage);
            alert.setHeaderText("Behållaren har innehåll");
            alert.showAndWait();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Vill du ta bort ”" + item.getName() + "”? Det går inte att ångra.", ButtonType.OK, ButtonType.CANCEL);
        confirm.initOwner(stage);
        confirm.setHeaderText("Ta bort föremål");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK)).setText("Ta bort");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Avbryt");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                repository.delete(item.getId());
                reload(item.getParent() == null ? null : item.getParent().getId());
            } catch (Exception ex) {
                showError("Kunde inte ta bort föremålet", ex);
            }
        }
    }

    private void showError(String title, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (stage != null && stage.isShowing()) alert.initOwner(stage);
        alert.setHeaderText(title);
        alert.setContentText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
        alert.showAndWait();
    }
}
