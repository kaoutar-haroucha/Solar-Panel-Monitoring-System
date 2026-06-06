package solar_panel;

// --- IMPORTS FOR JAVAFX (USER INTERFACE) ---
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

// --- IMPORTS FOR READING DATA AND RUNNING THE C PROGRAM ---
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.time.LocalDateTime;

// --- IMPORTS FOR SQLITE DATABASE ---
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * MAIN APPLICATION: Solar Panel Performance Monitoring
 * This code creates the User Interface (UI), talks to the C program to get data, 
 * and saves everything safely into an SQLite Database.
 */
public class SolarDashboard extends Application {

    // =========================================================================
    // UI COMPONENTS (TEXTS, BUTTONS, AND SHAPES)
    // =========================================================================
    private Label lblVoltage, lblCurrent, lblTemp, lblIrr; // Cards showing the numbers
    private Label lblStatus, lblFaultCount;               // System status and error counter
    private Circle statusIndicator;                        // Virtual LED (Green = Good, Red = Error)
    private TextArea logArea;                              // Text box at the bottom to show history

    // --- CHARTS (GRAPHS) ---
    private AreaChart<Number, Number> powerChart;
    private XYChart.Series<Number, Number> powerSeries = new XYChart.Series<>();
    private LineChart<Number, Number> viChart;
    private XYChart.Series<Number, Number> vSeries = new XYChart.Series<>();
    private XYChart.Series<Number, Number> iSeries = new XYChart.Series<>();

    // --- HISTORY TABLE ---
    private TableView<DataRecord> historyTable;
    // An ObservableList automatically updates the table when we add new data
    private ObservableList<DataRecord> tableData = FXCollections.observableArrayList();

    // --- SYSTEM CONTROL VARIABLES ---
    private Process cProcess;          // Holds the C program running in the background
    private int timeTick = 0;          // Time counter for the X-axis of our charts
    private int faultCounter = 0;      // Counts how many times an error happened
    private boolean isRunning = false; // Checks if the system is currently "START" or "STOP"

    // --- LAYOUT CONTAINERS (BOXES TO HOLD UI) ---
    private BorderPane root;
    private VBox dashboardView, performanceView;

    // Local link to our SQLite Database file
    private final String DB_URL = "jdbc:sqlite:solar_data.db";

    /**
     * MAIN START METHOD
     * This runs when the app opens. It builds the whole screen and starts the database.
     */
    @Override
    public void start(Stage primaryStage) {
        // Step 1: Create the database table if it doesn't exist
        initDatabase();

        root = new BorderPane();

        // =========================================================================
        // 1. BUILD THE SIDEBAR (NAVIGATION MENU)
        // =========================================================================
        VBox sidebar = new VBox(20);
        sidebar.setPrefWidth(240);
        sidebar.getStyleClass().add("sidebar"); // Connects to CSS for styling
        sidebar.setPadding(new Insets(20, 15, 20, 15));

        // Company Logo and App Name
        HBox logoBox = new HBox(10);
        logoBox.setAlignment(Pos.CENTER_LEFT);
        ImageView logoView = new ImageView();
        try {
            java.io.InputStream is = getClass().getResourceAsStream("/image.png");
            if (is != null) { 
                logoView.setImage(new Image(is)); 
                logoView.setFitHeight(55); 
                logoView.setPreserveRatio(true); 
            }
        } catch (Exception e) {
            System.err.println("Logo not found, text only.");
        }
        
        Label brandName = new Label("Solar Panel \nDashboard");
        brandName.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        logoBox.getChildren().addAll(logoView, brandName);

        // Menu Buttons (Dashboard and Performance)
        VBox menuBox = new VBox(10);
        Label menu1 = new Label("❖ Dashboard"); menu1.getStyleClass().addAll("menu-item", "menu-item-active");
        menu1.setMaxWidth(Double.MAX_VALUE);
        Label menu2 = new Label("⚡ Performance"); menu2.getStyleClass().add("menu-item");
        menu2.setMaxWidth(Double.MAX_VALUE);
        
        menuBox.getChildren().addAll(new Label("  MAIN MENU"), menu1, menu2);
        ((Label)menuBox.getChildren().get(0)).setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px; -fx-font-weight: bold;");

        // Action when clicking menus: Change the view in the center
        menu1.setOnMouseClicked(e -> { 
            menu1.getStyleClass().add("menu-item-active"); menu2.getStyleClass().remove("menu-item-active");
            root.setCenter(dashboardView); 
        });
        menu2.setOnMouseClicked(e -> { 
            menu2.getStyleClass().add("menu-item-active"); menu1.getStyleClass().remove("menu-item-active");
            root.setCenter(performanceView); 
        });

        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS); // Pushes the status card to the bottom
        
        // System Status Card (The LED at the bottom left)
        VBox infoCard = new VBox(5);
        infoCard.getStyleClass().add("panel-card");
        infoCard.setPadding(new Insets(15));
        infoCard.getChildren().addAll(
            new Label("System Status"),
            new HBox(10, statusIndicator = new Circle(6, Color.GRAY), lblStatus = new Label("IDLE"))
        );
        ((Label)infoCard.getChildren().get(0)).setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");
        lblStatus.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        
        sidebar.getChildren().addAll(logoBox, new Separator(), menuBox, spacer, infoCard);
        root.setLeft(sidebar);

        // =========================================================================
        // 2. INITIALIZE THE TWO MAIN PAGES
        // =========================================================================
        buildDashboardView();
        buildPerformanceView();

        root.setCenter(dashboardView); // Show Dashboard page by default

        // Setup the Application Window
        Scene scene = new Scene(root, 1400, 850);
        try { scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm()); } catch (Exception e) {}
        
        primaryStage.setTitle("ALTEN - Solar Panel Performance Monitoring");
        primaryStage.setScene(scene);
        // SAFETY: Force close the C program when the user clicks the "X" window button
        primaryStage.setOnCloseRequest(e -> stopMonitoring()); 
        primaryStage.show();
    }

    // =========================================================================
    // PAGE 1: DASHBOARD (REAL-TIME METRICS & CHARTS)
    // =========================================================================
    private void buildDashboardView() {
        dashboardView = new VBox(15);
        dashboardView.setPadding(new Insets(20, 25, 20, 25));

        // Top bar with START and STOP buttons
        HBox topNav = new HBox(20);
        topNav.setAlignment(Pos.CENTER_LEFT);
        Label dashTitle = new Label("Solar Panel Performance Overview");
        dashTitle.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");
        Region navSpacer = new Region(); HBox.setHgrow(navSpacer, Priority.ALWAYS);
        Button btnStart = new Button("▶ START"); btnStart.getStyleClass().add("btn-start");
        Button btnStop = new Button("⏹ STOP"); btnStop.getStyleClass().add("btn-stop");
        btnStart.setOnAction(e -> startMonitoring());
        btnStop.setOnAction(e -> stopMonitoring());
        topNav.getChildren().addAll(dashTitle, navSpacer, btnStart, btnStop);

        // Row for Metric Cards (V, I, T, E)
        HBox metricsRow = new HBox(15);
        VBox cardV = createMetricCard("⚡ Voltage", lblVoltage = new Label("0.0 V"), "#ef4444"); 
        VBox cardI = createMetricCard("⎋ Current", lblCurrent = new Label("0.0 A"), "#3fb950");
        VBox cardT = createMetricCard("☉ Temperature", lblTemp = new Label("0.0 °C"), "#f59e0b");
        VBox cardE = createMetricCard("☼ Irradiance", lblIrr = new Label("0.0 W/m²"), "#d2a8ff");
        HBox.setHgrow(cardV, Priority.ALWAYS); HBox.setHgrow(cardI, Priority.ALWAYS);
        HBox.setHgrow(cardT, Priority.ALWAYS); HBox.setHgrow(cardE, Priority.ALWAYS);

        // Static weather card for local context
        VBox weatherCard = new VBox(5);
        weatherCard.getStyleClass().add("panel-card");
        weatherCard.setPadding(new Insets(15));
        weatherCard.setPrefWidth(200);
        Label wTitle = new Label("Local Weather"); wTitle.getStyleClass().add("card-title");
        Label wTemp = new Label("⛅ 32°C"); wTemp.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");
        Label wLoc = new Label("Fes, MA"); wLoc.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");
        weatherCard.getChildren().addAll(wTitle, wTemp, wLoc);
        metricsRow.getChildren().addAll(cardV, cardI, cardT, cardE, weatherCard);

        // Row for Charts
        HBox chartsRow = new HBox(15);
        
        // Chart 1: Power Generation (Area Chart)
        VBox areaBox = new VBox(10);
        areaBox.getStyleClass().add("panel-card");
        areaBox.setPadding(new Insets(15));
        HBox.setHgrow(areaBox, Priority.ALWAYS);
        Label areaTitle = new Label("Real-time Power Generation (W)"); areaTitle.getStyleClass().add("card-title");
        NumberAxis xAxisA = new NumberAxis(); xAxisA.setAutoRanging(false); xAxisA.setTickUnit(5);
        NumberAxis yAxisA = new NumberAxis();
        powerChart = new AreaChart<>(xAxisA, yAxisA);
        powerChart.setId("powerChart"); 
        powerChart.setLegendVisible(false); powerChart.setCreateSymbols(true); 
        powerChart.getData().add(powerSeries);
        powerChart.setPrefHeight(260);
        areaBox.getChildren().addAll(areaTitle, powerChart);

        // Chart 2: Voltage & Current
        VBox viBox = new VBox(10);
        viBox.getStyleClass().add("panel-card");
        viBox.setPadding(new Insets(15));
        HBox.setHgrow(viBox, Priority.ALWAYS);
        Label viTitle = new Label("Voltage (V) & Current (A)"); viTitle.getStyleClass().add("card-title");
        NumberAxis xAxisVI = new NumberAxis(); xAxisVI.setAutoRanging(false); xAxisVI.setTickUnit(5);
        NumberAxis yAxisVI = new NumberAxis(); yAxisVI.setAutoRanging(true);
        viChart = new LineChart<>(xAxisVI, yAxisVI);
        viChart.setId("viChart"); 
        viChart.setCreateSymbols(true); viChart.setLegendVisible(true);
        vSeries.setName("Voltage (V)"); iSeries.setName("Current (A)");
        viChart.getData().add(vSeries);
        viChart.getData().add(iSeries);
        viChart.setPrefHeight(260);
        viBox.getChildren().addAll(viTitle, viChart);
        chartsRow.getChildren().addAll(areaBox, viBox);

        // Bottom box: System Logs & Alerts text area
        VBox logBox = new VBox(10);
        logBox.getStyleClass().add("panel-card");
        logBox.setPadding(new Insets(15));
        HBox logHeader = new HBox(10);
        logHeader.setAlignment(Pos.CENTER_LEFT);
        Label logTitle = new Label("System Logs & Alerts"); logTitle.getStyleClass().add("card-title");
        Region logSpacer = new Region(); HBox.setHgrow(logSpacer, Priority.ALWAYS);
        lblFaultCount = new Label("Total Anomalies: 0"); lblFaultCount.setStyle("-fx-text-fill: #da3633; -fx-font-weight: bold;");
        logHeader.getChildren().addAll(logTitle, logSpacer, lblFaultCount);
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(120);
        logBox.getChildren().addAll(logHeader, logArea);

        dashboardView.getChildren().addAll(topNav, metricsRow, chartsRow, logBox);
    }

    // =========================================================================
    // PAGE 2: PERFORMANCE (TABLE, SEARCH BAR, AND CSV EXPORT)
    // =========================================================================
    private void buildPerformanceView() {
        performanceView = new VBox(15);
        performanceView.setPadding(new Insets(20, 25, 20, 25));

        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Data History & Export");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");
        
        Region spacer = new Region(); 
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // --- TIME MACHINE SEARCH BAR ---
        // Allows filtering the table to see exactly what happened at a specific second
        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Search time (ex: 12:40) or alert...");
        searchField.setPrefWidth(300);
        searchField.setStyle("-fx-background-color: #313244; -fx-text-fill: white; -fx-prompt-text-fill: #a6adc8; -fx-border-color: #45475a; -fx-border-radius: 5;");

        Button btnExport = new Button("⤓ Export to CSV");
        btnExport.setStyle("-fx-background-color: #1f6feb; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        btnExport.setOnAction(e -> exportToCSV());
        
        header.getChildren().addAll(title, spacer, searchField, btnExport);

        // Set up the history table columns
        historyTable = new TableView<>();
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<DataRecord, String> colTime = new TableColumn<>("Timestamp");
        colTime.setCellValueFactory(new PropertyValueFactory<>("time"));
        colTime.setPrefWidth(200);

        TableColumn<DataRecord, String> colPow = new TableColumn<>("Power Generated (W)");
        colPow.setCellValueFactory(new PropertyValueFactory<>("pow"));
        colPow.setPrefWidth(200);

        TableColumn<DataRecord, String> colState = new TableColumn<>("System Status & Diagnostics");
        colState.setCellValueFactory(new PropertyValueFactory<>("state"));
        colState.setPrefWidth(500); 

        historyTable.getColumns().add(colTime);
        historyTable.getColumns().add(colPow);
        historyTable.getColumns().add(colState);

        // --- FILTERING LOGIC (SEARCH BAR) ---
        // 1. Wrap the table data inside a "FilteredList"
        FilteredList<DataRecord> filteredData = new FilteredList<>(tableData, p -> true);
        
        // 2. Listen to what the user types in the search bar
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(record -> {
                if (newValue == null || newValue.isEmpty()) { return true; } // If empty, show everything
                
                String lowerCaseFilter = newValue.toLowerCase();
                
                // Check if the typed text is found in Time, Power, OR Status
                if (record.getTime().toLowerCase().contains(lowerCaseFilter)) { return true; } 
                else if (record.getPow().toLowerCase().contains(lowerCaseFilter)) { return true; } 
                else if (record.getState().toLowerCase().contains(lowerCaseFilter)) { return true; }
                
                return false; // Hide this row if it doesn't match
            });
        });

        // 3. Keep the table sorted and updated
        SortedList<DataRecord> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(historyTable.comparatorProperty());
        historyTable.setItems(sortedData);

        performanceView.getChildren().addAll(header, historyTable);
    }

    // =========================================================================
    // UI HELPER METHODS (CARDS & TOOLTIPS)
    // =========================================================================
    
    // Creates a nice looking card for numbers (V, I, T, E)
    private VBox createMetricCard(String title, Label valLabel, String accentColor) {
        VBox card = new VBox(5);
        card.getStyleClass().add("panel-card");
        card.setPadding(new Insets(15));
        Label t = new Label(title); t.getStyleClass().add("card-title");
        valLabel.getStyleClass().add("card-value");
        Region accent = new Region();
        accent.setPrefSize(35, 3);
        accent.setStyle("-fx-background-color: " + accentColor + "; -fx-background-radius: 2;");
        card.getChildren().addAll(t, valLabel, accent);
        return card;
    }

    /**
     * Adds an interactive pop-up (Tooltip) when hovering over a chart point.
     * Engineering design: The point grows (zooms) when the mouse is over it.
     */
    private void addInteractiveTooltip(StackPane node, String tooltipText) {
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setStyle("-fx-background-color: #21262d; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-border-color: #30363d; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6px;");
        try { tooltip.setShowDelay(Duration.ZERO); } catch (NoSuchMethodError e) { }
        Tooltip.install(node, tooltip);

        node.setOnMouseEntered(e -> {
            node.setScaleX(2.0); node.setScaleY(2.0); // Make the dot 2x bigger
            node.setStyle(node.getStyle() + "-fx-cursor: hand;");
            node.toFront();
        });
        node.setOnMouseExited(e -> {
            node.setScaleX(1.0); node.setScaleY(1.0); // Go back to normal size
            node.setStyle(node.getStyle().replace("-fx-cursor: hand;", ""));
        });
    }

    // =========================================================================
    // SQLITE DATABASE: SAVING DATA LOCALLY
    // =========================================================================
    private void initDatabase() {
        // Creates the table if it does not exist yet
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS solar_history (" +
                         "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                         "timestamp TEXT NOT NULL, " +
                         "voltage REAL, " +
                         "current REAL, " +
                         "temperature REAL, " +
                         "irradiance REAL, " +
                         "power REAL, " +
                         "system_state TEXT)";
            stmt.execute(sql);
        } catch (Exception e) {
            System.err.println("Database error: " + e.getMessage());
        }
    }

    /**
     * Uses PreparedStatement to safely insert data. 
     * This protects the app against SQL injection and formatting errors.
     */
    private void saveToDatabase(String time, double v, double c, double t, double irr, double p, String state) {
        String sql = "INSERT INTO solar_history(timestamp, voltage, current, temperature, irradiance, power, system_state) VALUES(?,?,?,?,?,?,?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, time); pstmt.setDouble(2, v); pstmt.setDouble(3, c);
            pstmt.setDouble(4, t); pstmt.setDouble(5, irr); pstmt.setDouble(6, p);
            pstmt.setString(7, state);
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("SQL Insert error: " + e.getMessage());
        }
    }

    // =========================================================================
    // MULTITHREADING: RUNNING AND LISTENING TO THE C PROGRAM
    // =========================================================================
    private void startMonitoring() {
        if (isRunning) return; // Prevent clicking START multiple times
        isRunning = true;
        logArea.appendText("[" + LocalDateTime.now().toLocalTime().toString().substring(0, 8) + "] SESSION STARTED\n");
        tableData.clear();
        
        // CRITICAL: We start a background thread to listen to the C program.
        // This prevents the App from freezing while waiting for data.
        new Thread(() -> {
            try {
                // Path to the C executable file
                String path = "C:\\\\Users\\\\kharoucha\\\\source\\\\repos\\\\panel_solar\\\\x64\\\\Debug\\\\panel_solar.exe";
                cProcess = new ProcessBuilder(path).start(); // Start the C program
                BufferedReader r = new BufferedReader(new InputStreamReader(cProcess.getInputStream()));
                String line;
                while (isRunning && (line = r.readLine()) != null) {
                    final String trame = line;
                    // Platform.runLater safely sends the data back to the UI Thread
                    Platform.runLater(() -> parse(trame));
                }
            } catch (Exception e) { 
                isRunning = false; 
                Platform.runLater(() -> logArea.appendText("SYSTEM ERROR: Cannot connect to C Firmware.\n"));
            }
        }).start();
    }

    /**
     * Stop the UI updates and forcefully kill the C background process.
     * This reflects real-world operations where we stop monitoring.
     */
    private void stopMonitoring() { 
        isRunning = false; 
        if (cProcess != null) {
            cProcess.destroyForcibly(); // Hard kill the C program
        }
        statusIndicator.setFill(Color.GRAY); 
        lblStatus.setText("STOPPED");
        exportToCSV(); // Auto-save data just in case
    }

    // =========================================================================
    // SMART DATA PARSING (DECODING THE C PROGRAM STRING)
    // =========================================================================
    private void parse(String t) {
        try {
            // Ignore bad strings if they don't have Voltage (V) and Power (P)
            if (!t.contains("V=") || !t.contains("P=")) return;

            double volt = 0, curr = 0, temp = 0, irr = 0, pow = 0;
            int s_fault = 0, o_fault = 0, d_fault = 0;
            String rawState = "NORMAL";

            // SMART READING: We split the text by ";" and then by "=". 
            // This guarantees we always read the correct variable, no matter the order.
            String[] tokens = t.split(";");
            for (String token : tokens) {
                String[] kv = token.split("=");
                if (kv.length < 2) continue;
                String key = kv[0].trim();
                String val = kv[1].trim();

                switch (key) {
                    case "V": volt = Double.parseDouble(val); break;
                    case "I": curr = Double.parseDouble(val); break;
                    case "T": temp = Double.parseDouble(val); break;
                    case "E": irr = Double.parseDouble(val); break;
                    case "P": pow = Double.parseDouble(val); break;
                    case "SHADING": s_fault = Integer.parseInt(val); break;
                    case "OVERHEAT": o_fault = Integer.parseInt(val); break;
                    case "DEGRADATION": d_fault = Integer.parseInt(val); break;
                    case "STATE": rawState = val; break;
                }
            }

            String currentTime = LocalDateTime.now().toLocalTime().toString().substring(0, 8); 

            // 1. Update UI Text Labels
            lblVoltage.setText(String.format("%.1f V", volt));
            lblCurrent.setText(String.format("%.1f A", curr));
            lblTemp.setText(String.format("%.1f °C", temp));
            lblIrr.setText(String.format("%.1f W/m²", irr));

            // 2. Add points to the charts
            XYChart.Data<Number, Number> pPoint = new XYChart.Data<>(timeTick, pow);
            StackPane pNode = new StackPane(); pNode.setPrefSize(8, 8);
            pNode.setStyle("-fx-background-color: #f59e0b; -fx-background-radius: 50%;");
            pPoint.setNode(pNode);
            addInteractiveTooltip(pNode, String.format("Time: %ds\nPower: %.1f W", timeTick, pow));
            powerSeries.getData().add(pPoint);

            XYChart.Data<Number, Number> vPoint = new XYChart.Data<>(timeTick, volt);
            StackPane vNode = new StackPane(); vNode.setPrefSize(8, 8);
            vNode.setStyle("-fx-background-color: #ef4444; -fx-background-radius: 50%;"); 
            vPoint.setNode(vNode);
            addInteractiveTooltip(vNode, String.format("Time: %ds\nVoltage: %.2f V", timeTick, volt));
            vSeries.getData().add(vPoint);

            XYChart.Data<Number, Number> iPoint = new XYChart.Data<>(timeTick, curr);
            StackPane iNode = new StackPane(); iNode.setPrefSize(8, 8);
            iNode.setStyle("-fx-background-color: #3fb950; -fx-background-radius: 50%;");
            iPoint.setNode(iNode);
            addInteractiveTooltip(iNode, String.format("Time: %ds\nCurrent: %.2f A", timeTick, curr));
            iSeries.getData().add(iPoint);
            
            // Keep memory low: Only keep the last 40 points on the screen
            if (powerSeries.getData().size() > 40) {
                powerSeries.getData().remove(0); vSeries.getData().remove(0); iSeries.getData().remove(0);
            }
            
            // Move the chart X-axis like a heart monitor (ECG effect)
            NumberAxis xAxisA = (NumberAxis) powerChart.getXAxis();
            NumberAxis xAxisVI = (NumberAxis) viChart.getXAxis();
            if (timeTick > 40) { 
                xAxisA.setLowerBound(timeTick - 40); xAxisA.setUpperBound(timeTick); 
                xAxisVI.setLowerBound(timeTick - 40); xAxisVI.setUpperBound(timeTick); 
            } else { 
                xAxisA.setLowerBound(0); xAxisA.setUpperBound(40); 
                xAxisVI.setLowerBound(0); xAxisVI.setUpperBound(40); 
            }
            timeTick++;

            // 3. Diagnostics and Alert Logic
            String statusMsg = rawState;
            if (!rawState.equals("NORMAL")) {
                faultCounter++;
                lblFaultCount.setText("Total Anomalies: " + faultCounter);
                statusIndicator.setFill(Color.web("#da3633")); // Red LED
                
                String specificError = "";
                if(s_fault == 1) specificError += "[SHADING] ";
                if(o_fault == 1) specificError += "[OVERHEAT] ";
                if(d_fault == 1) specificError += "[DEGRADATION] ";
                
                statusMsg = "ALERT: " + specificError;
                lblStatus.setText("FAULT " + specificError); 
                logArea.appendText("[" + currentTime + "] " + statusMsg + " -> Power: " + String.format("%.1f", pow) + "W\n");
            } else {
                statusIndicator.setFill(Color.web("#3fb950")); // Green LED
                lblStatus.setText("ACTIVE (NORMAL)");
            }

            // 4. Add the data to the UI History Table (insert at index 0 so it's always at the top)
            tableData.add(0, new DataRecord(currentTime, String.format("%.1f", pow), statusMsg));
            if(tableData.size() > 100) tableData.remove(100); // Prevent memory overflow
            
            // 5. Save the data to the SQLite database
            saveToDatabase(currentTime, volt, curr, temp, irr, pow, statusMsg);

        } catch (Exception e) {
            System.err.println("Parsing error: " + e.getMessage());
        }
    }

    // =========================================================================
    // EXPORT TO EXCEL (CSV FILE)
    // =========================================================================
    private void exportToCSV() {
        try {
            File file = new File("export_data.csv");
            PrintWriter writer = new PrintWriter(file);
            writer.println("Timestamp,Power(W),Status Diagnostics");
            for (DataRecord record : tableData) {
                writer.println(record.getTime() + "," + record.getPow() + "," + record.getState());
            }
            writer.close();
            logArea.appendText("[" + LocalDateTime.now().toLocalTime().toString().substring(0,8) + "] DATA AUTO-EXPORTED TO CSV\n");
        } catch (Exception ex) {
            System.err.println("CSV Export failed.");
        }
    }
    
    /**
     * Helper Class (POJO): This shapes the data row for the history table.
     */
    public static class DataRecord {
        private final String time;
        private final String pow;
        private final String state;
        
        public DataRecord(String time, String pow, String state) {
            this.time = time; this.pow = pow; this.state = state;
        }
        public String getTime() { return time; }
        public String getPow() { return pow; }
        public String getState() { return state; }
    }
    // Start the Java application
    public static void main(String[] args) { launch(args); }
}