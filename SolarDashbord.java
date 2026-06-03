package solar_panel;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.time.LocalDateTime;

// NOUVEAUX IMPORTS POUR LA BASE DE DONNÉES SQLITE
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

public class SolarDashbord extends Application {

    // --- Composants UI ---
    private Label lblVoltage, lblCurrent, lblTemp, lblIrr;
    private Label lblStatus, lblFaultCount;
    private Circle statusIndicator;
    private TextArea logArea;
    
    // --- Graphiques ---
    private AreaChart<Number, Number> powerChart;
    private XYChart.Series<Number, Number> powerSeries = new XYChart.Series<>();
    private LineChart<Number, Number> viChart;
    private XYChart.Series<Number, Number> vSeries = new XYChart.Series<>();
    private XYChart.Series<Number, Number> iSeries = new XYChart.Series<>();
    
    // --- Tableau Historique ---
    private TableView<DataRecord> historyTable;
    private ObservableList<DataRecord> tableData = FXCollections.observableArrayList();
    
    // --- Variables Système ---
    private Process cProcess;
    private int timeTick = 0;
    private int faultCounter = 0;
    private boolean isRunning = false;

    // --- Layouts ---
    private BorderPane root;
    private VBox dashboardView, performanceView, settingsView;
    private CheckBox chkAutoExport;

    // L'URL locale de la base de données SQLite
    private final String DB_URL = "jdbc:sqlite:solar_data.db";

    @Override
    public void start(Stage primaryStage) {
        // Initialisation de la base de données au démarrage
        initDatabase();

        root = new BorderPane();

        // ==========================================
        // 1. BARRE LATÉRALE (SIDEBAR)
        // ==========================================
        VBox sidebar = new VBox(20);
        sidebar.setPrefWidth(240);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(20, 15, 20, 15));

        HBox logoBox = new HBox(10);
        logoBox.setAlignment(Pos.CENTER_LEFT);
        ImageView logoView = new ImageView();
        try {
            java.io.InputStream is = getClass().getResourceAsStream("/alten1.png");
            if (is != null) { logoView.setImage(new Image(is)); logoView.setFitHeight(35); logoView.setPreserveRatio(true); }
        } catch (Exception e) {}
        Label brandName = new Label("Solar Monitoring");
        brandName.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        logoBox.getChildren().addAll(logoView, brandName);

        VBox menuBox = new VBox(10);
        Label menu1 = new Label("\u2756 Dashboard"); menu1.getStyleClass().addAll("menu-item", "menu-item-active");
        menu1.setMaxWidth(Double.MAX_VALUE);
        Label menu2 = new Label("\u26A1 Performance"); menu2.getStyleClass().add("menu-item");
        menu2.setMaxWidth(Double.MAX_VALUE);
        Label menu3 = new Label("\u2699 Settings"); menu3.getStyleClass().add("menu-item");
        menu3.setMaxWidth(Double.MAX_VALUE);
        
        menuBox.getChildren().addAll(new Label("  MAIN MENU"), menu1, menu2, menu3);
        ((Label)menuBox.getChildren().get(0)).setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px; -fx-font-weight: bold;");

        menu1.setOnMouseClicked(e -> { setActiveMenu(menu1, menu2, menu3); root.setCenter(dashboardView); });
        menu2.setOnMouseClicked(e -> { setActiveMenu(menu2, menu1, menu3); root.setCenter(performanceView); });
        menu3.setOnMouseClicked(e -> { setActiveMenu(menu3, menu1, menu2); root.setCenter(settingsView); });

        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        
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

        // ==========================================
        // 2. CRÉATION DES VUES
        // ==========================================
        buildDashboardView();
        buildPerformanceView();
        buildSettingsView();

        root.setCenter(dashboardView); 

        Scene scene = new Scene(root, 1400, 850);
        try { scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm()); } catch (Exception e) {}
        
        primaryStage.setTitle("ALTEN Solar Monitoring");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> stopMonitoring());
        primaryStage.show();
    }

    private void setActiveMenu(Label active, Label inactive1, Label inactive2) {
        active.getStyleClass().add("menu-item-active");
        inactive1.getStyleClass().remove("menu-item-active");
        inactive2.getStyleClass().remove("menu-item-active");
    }

    // ==========================================
    // VUE 1 : DASHBOARD PRINCIPAL
    // ==========================================
    private void buildDashboardView() {
        dashboardView = new VBox(15);
        dashboardView.setPadding(new Insets(20, 25, 20, 25));

        HBox topNav = new HBox(20);
        topNav.setAlignment(Pos.CENTER_LEFT);
        Label dashTitle = new Label("Performance Overview");
        dashTitle.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");
        Region navSpacer = new Region(); HBox.setHgrow(navSpacer, Priority.ALWAYS);
        Button btnStart = new Button("\u25B6 START"); btnStart.getStyleClass().add("btn-start");
        Button btnStop = new Button("\u23F9 STOP"); btnStop.getStyleClass().add("btn-stop");
        btnStart.setOnAction(e -> startMonitoring());
        btnStop.setOnAction(e -> stopMonitoring());
        topNav.getChildren().addAll(dashTitle, navSpacer, btnStart, btnStop);

        HBox metricsRow = new HBox(15);
        VBox cardV = createMetricCard("\u26A1 Voltage", lblVoltage = new Label("0.0 V"), "#ef4444"); // Mis à jour en rouge !
        VBox cardI = createMetricCard("\u238B Current", lblCurrent = new Label("0.0 A"), "#3fb950");
        VBox cardT = createMetricCard("\u2609 Temperature", lblTemp = new Label("0.0 \u00B0C"), "#f59e0b");
        VBox cardE = createMetricCard("\u263C Irradiance", lblIrr = new Label("0.0 W/m\u00B2"), "#d2a8ff");
        HBox.setHgrow(cardV, Priority.ALWAYS); HBox.setHgrow(cardI, Priority.ALWAYS);
        HBox.setHgrow(cardT, Priority.ALWAYS); HBox.setHgrow(cardE, Priority.ALWAYS);

        VBox weatherCard = new VBox(5);
        weatherCard.getStyleClass().add("panel-card");
        weatherCard.setPadding(new Insets(15));
        weatherCard.setPrefWidth(200);
        Label wTitle = new Label("Local Weather"); wTitle.getStyleClass().add("card-title");
        Label wTemp = new Label("\u26C5 28\u00B0C"); wTemp.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");
        Label wLoc = new Label("Meknes, MA"); wLoc.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");
        weatherCard.getChildren().addAll(wTitle, wTemp, wLoc);
        metricsRow.getChildren().addAll(cardV, cardI, cardT, cardE, weatherCard);

        HBox chartsRow = new HBox(15);
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

    // ==========================================
    // VUE 2 : PAGE PERFORMANCE
    // ==========================================
    private void buildPerformanceView() {
        performanceView = new VBox(15);
        performanceView.setPadding(new Insets(20, 25, 20, 25));

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Data History & Export");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnExport = new Button("\u2913 Export to CSV");
        btnExport.setStyle("-fx-background-color: #1f6feb; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        btnExport.setOnAction(e -> exportToCSV());
        header.getChildren().addAll(title, spacer, btnExport);

        historyTable = new TableView<>();
        historyTable.setItems(tableData);
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
        performanceView.getChildren().addAll(header, historyTable);
    }

    // ==========================================
    // VUE 3 : PAGE SETTINGS
    // ==========================================
    private void buildSettingsView() {
        settingsView = new VBox(25);
        settingsView.setPadding(new Insets(40));
        settingsView.setAlignment(Pos.TOP_LEFT);

        Label title = new Label("System Configuration");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold;");

        VBox formContainer = new VBox(25);
        formContainer.getStyleClass().add("panel-card");
        formContainer.setPadding(new Insets(30));
        formContainer.setMaxWidth(650);

        GridPane form = new GridPane();
        form.setVgap(25); form.setHgap(30);
        form.setAlignment(Pos.CENTER_LEFT);

        Label lblPort = new Label("Serial Connection Port:"); 
        lblPort.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 14px; -fx-font-weight: bold;");
        ComboBox<String> portCombo = new ComboBox<>(FXCollections.observableArrayList("COM1", "COM2", "COM3 (Active)"));
        portCombo.getSelectionModel().select(2);
        portCombo.setStyle("-fx-background-color: #0d1117; -fx-border-color: #30363d; -fx-border-radius: 6; -fx-pref-width: 250;");

        Label lblTempThresh = new Label("Overheat Threshold (\u00B0C):"); 
        lblTempThresh.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 14px; -fx-font-weight: bold;");
        TextField txtTemp = new TextField("80.0");
        txtTemp.setStyle("-fx-background-color: #0d1117; -fx-text-fill: white; -fx-border-color: #30363d; -fx-border-radius: 6; -fx-padding: 10; -fx-pref-width: 250;");

        Label lblAutoExport = new Label("Data Management:");
        lblAutoExport.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 14px; -fx-font-weight: bold;");
        chkAutoExport = new CheckBox("Auto-Export to CSV when monitoring stops");
        chkAutoExport.setSelected(true);
        chkAutoExport.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

        Label lblSound = new Label("Alert Notifications:");
        lblSound.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 14px; -fx-font-weight: bold;");
        ComboBox<String> soundCombo = new ComboBox<>(FXCollections.observableArrayList("\uD83D\uDD0A Sound Enabled", "\uD83D\uDD07 Muted"));
        soundCombo.getSelectionModel().select(0);
        soundCombo.setStyle("-fx-background-color: #0d1117; -fx-border-color: #30363d; -fx-border-radius: 6; -fx-pref-width: 250;");

        form.add(lblPort, 0, 0); form.add(portCombo, 1, 0);
        form.add(lblTempThresh, 0, 1); form.add(txtTemp, 1, 1);
        form.add(lblAutoExport, 0, 2); form.add(chkAutoExport, 1, 2);
        form.add(lblSound, 0, 3); form.add(soundCombo, 1, 3);

        HBox buttonBox = new HBox(20);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        
        Button btnSave = new Button("Save Configuration");
        btnSave.setStyle("-fx-background-color: #238636; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 25; -fx-background-radius: 6; -fx-cursor: hand;");
        Label lblSaveMsg = new Label();
        lblSaveMsg.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        btnSave.setOnAction(e -> {
            try {
                Double.parseDouble(txtTemp.getText());
                lblSaveMsg.setText("\u2713 Settings saved successfully!");
                lblSaveMsg.setStyle("-fx-text-fill: #3fb950; -fx-font-weight: bold;");
            } catch (NumberFormatException ex) {
                lblSaveMsg.setText("\u274C Error: Please enter valid numbers only.");
                lblSaveMsg.setStyle("-fx-text-fill: #da3633; -fx-font-weight: bold;");
            }
        });

        buttonBox.getChildren().addAll(btnSave, lblSaveMsg);
        formContainer.getChildren().addAll(form, new Separator(), buttonBox);
        settingsView.getChildren().addAll(title, formContainer);
    }

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

    // ==========================================
    // TOOLTIPS INTERACTIFS
    // ==========================================
    private void addInteractiveTooltip(StackPane node, String tooltipText) {
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setStyle("-fx-background-color: #21262d; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-border-color: #30363d; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6px;");
        try { tooltip.setShowDelay(Duration.ZERO); } catch (NoSuchMethodError e) { }
        Tooltip.install(node, tooltip);

        node.setOnMouseEntered(e -> {
            node.setScaleX(2.0); node.setScaleY(2.0);
            node.setStyle(node.getStyle() + "-fx-cursor: hand;");
            node.toFront();
        });
        node.setOnMouseExited(e -> {
            node.setScaleX(1.0); node.setScaleY(1.0);
            node.setStyle(node.getStyle().replace("-fx-cursor: hand;", ""));
        });
    }

    // ==========================================
    // SQLITE: GESTION DE LA BASE DE DONNÉES
    // ==========================================
    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            // Création de la table si elle n'existe pas
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
            System.out.println("SQLite Database initialized successfully.");
            
        } catch (Exception e) {
            System.err.println("Database Initialization Error: " + e.getMessage());
        }
    }

    private void saveToDatabase(String time, double v, double c, double t, double irr, double p, String state) {
        String sql = "INSERT INTO solar_history(timestamp, voltage, current, temperature, irradiance, power, system_state) VALUES(?,?,?,?,?,?,?)";
        
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, time);
            pstmt.setDouble(2, v);
            pstmt.setDouble(3, c);
            pstmt.setDouble(4, t);
            pstmt.setDouble(5, irr);
            pstmt.setDouble(6, p);
            pstmt.setString(7, state);
            
            pstmt.executeUpdate(); // Exécute l'insertion
            
        } catch (Exception e) {
            // Silencieux dans l'interface, mais loggué dans la console en cas d'erreur
            System.err.println("DB Insert Error: " + e.getMessage());
        }
    }

    // ==========================================
    // LOGIQUE DE FLUX DE DONNÉES (C PROCESS)
    // ==========================================
    private void startMonitoring() {
        if (isRunning) return;
        isRunning = true;
        logArea.appendText("[" + LocalDateTime.now().toLocalTime().toString().substring(0, 8) + "] SESSION STARTED\n");
        tableData.clear();
        
        new Thread(() -> {
            try {
                String path = "C:\\\\Users\\\\kharoucha\\\\source\\\\repos\\\\panel_solar\\\\x64\\\\Debug\\\\panel_solar.exe";
                cProcess = new ProcessBuilder(path).start();
                BufferedReader r = new BufferedReader(new InputStreamReader(cProcess.getInputStream()));
                String line;
                while (isRunning && (line = r.readLine()) != null) {
                    final String trame = line;
                    Platform.runLater(() -> parse(trame));
                }
            } catch (Exception e) { 
                isRunning = false; 
                Platform.runLater(() -> logArea.appendText("SYSTEM ERROR: Cannot connect to C Firmware.\n"));
            }
        }).start();
    }

    private void parse(String t) {
        try {
            String[] p = t.split(";");
            double volt = Double.parseDouble(p[0].split("=")[1]);
            double curr = Double.parseDouble(p[1].split("=")[1]);
            double temp = Double.parseDouble(p[2].split("=")[1]);
            double irr  = Double.parseDouble(p[3].split("=")[1]); // Capture de l'irradiance pour la BDD
            double pow  = Double.parseDouble(p[4].split("=")[1]);
            
            int s_fault = Integer.parseInt(p[6].split("=")[1]);
            int o_fault = Integer.parseInt(p[7].split("=")[1]);
            int d_fault = Integer.parseInt(p[8].split("=")[1]);
            String rawState = p[9].split("=")[1].trim();
            String currentTime = LocalDateTime.now().toLocalTime().toString().substring(0, 8); 

            // 1. Mise à jour des Widgets (Cartes)
            lblVoltage.setText(String.format("%.1f V", volt));
            lblCurrent.setText(String.format("%.1f A", curr));
            lblTemp.setText(String.format("%.1f \u00B0C", temp));
            lblIrr.setText(String.format("%.1f W/m\u00B2", irr));

            // 2. Construction dynamique des Points AVEC TOOLTIPS
            XYChart.Data<Number, Number> pPoint = new XYChart.Data<>(timeTick, pow);
            StackPane pNode = new StackPane(); pNode.setPrefSize(8, 8);
            pNode.setStyle("-fx-background-color: #f59e0b; -fx-background-radius: 50%;");
            pPoint.setNode(pNode);
            addInteractiveTooltip(pNode, String.format("Time: %ds\nPower: %.1f W", timeTick, pow));
            powerSeries.getData().add(pPoint);

            XYChart.Data<Number, Number> vPoint = new XYChart.Data<>(timeTick, volt);
            StackPane vNode = new StackPane(); vNode.setPrefSize(8, 8);
            vNode.setStyle("-fx-background-color: #ef4444; -fx-background-radius: 50%;"); // ROUGE pour le graphe
            vPoint.setNode(vNode);
            addInteractiveTooltip(vNode, String.format("Time: %ds\nVoltage: %.2f V", timeTick, volt));
            vSeries.getData().add(vPoint);

            XYChart.Data<Number, Number> iPoint = new XYChart.Data<>(timeTick, curr);
            StackPane iNode = new StackPane(); iNode.setPrefSize(8, 8);
            iNode.setStyle("-fx-background-color: #3fb950; -fx-background-radius: 50%;");
            iPoint.setNode(iNode);
            addInteractiveTooltip(iNode, String.format("Time: %ds\nCurrent: %.2f A", timeTick, curr));
            iSeries.getData().add(iPoint);
            
            if (powerSeries.getData().size() > 40) {
                powerSeries.getData().remove(0); vSeries.getData().remove(0); iSeries.getData().remove(0);
            }
            
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

            // 3. Traitement du Statut
            String statusMsg = rawState;
            if (!rawState.equals("NORMAL")) {
                faultCounter++;
                lblFaultCount.setText("Total Anomalies: " + faultCounter);
                statusIndicator.setFill(Color.web("#da3633")); 
                
                String specificError = "";
                if(s_fault == 1) specificError += "[SHADING] ";
                if(o_fault == 1) specificError += "[OVERHEAT] ";
                if(d_fault == 1) specificError += "[DEGRADATION] ";
                
                statusMsg = "ALERT: " + specificError;
                lblStatus.setText("FAULT " + specificError); 
                logArea.appendText("[" + currentTime + "] " + statusMsg + " -> Power: " + String.format("%.1f", pow) + "W\n");
            } else {
                statusIndicator.setFill(Color.web("#3fb950")); 
                lblStatus.setText("ACTIVE (NORMAL)");
            }

            // 4. Alimentation du tableau à 3 colonnes
            tableData.add(0, new DataRecord(currentTime, String.format("%.1f", pow), statusMsg));
            if(tableData.size() > 100) tableData.remove(100); 
            
            // 5. SAUVEGARDE DANS LA BASE DE DONNÉES SQLITE
            saveToDatabase(currentTime, volt, curr, temp, irr, pow, statusMsg);

        } catch (Exception e) {}
    }

    private void stopMonitoring() { 
        isRunning = false; 
        if (cProcess != null) cProcess.destroy(); 
        statusIndicator.setFill(Color.GRAY); 
        lblStatus.setText("STOPPED");
        if (chkAutoExport != null && chkAutoExport.isSelected() && !tableData.isEmpty()) {
            exportToCSV();
        }
    }

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
        } catch (Exception ex) {}
    }
    
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

    public static void main(String[] args) { launch(args); }
}