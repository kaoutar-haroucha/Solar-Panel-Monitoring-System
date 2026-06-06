🌞 Solar Panel Monitoring System
📌 Overview
The Solar Panel Monitoring System is a hybrid software project that integrates C, Java, and SQLite to simulate, process, and visualize solar energy data.
The system workflow is designed as follows:

C program simulates solar panel data
Java application (JavaFX) processes and displays the data
SQLite database stores the data for persistence

This project demonstrates a complete pipeline from data simulation → processing → visualization → storage.

🎯 Objectives

Simulate solar panel energy production using C
Develop a graphical dashboard using JavaFX
Store and manage data using SQLite
Build a multi-language integrated system
Visualize energy metrics in a user-friendly interface


🏗️ System Architecture
+----------------------+
|   C Simulation       |
|   (panel_solar)      |
+----------+-----------+
           |
           v
+------------------------------+
| Java Application             |
| JavaFX Dashboard             |
+----------+-------------------+
           |
           v
+----------------------+
| SQLite Database      |
| (solar_data.db)      |
+----------------------+


🛠 Technologies Used
🔹 Programming Languages

C → Data simulation
Java → Application logic

🔹 Tools & Frameworks

JavaFX → Graphical User Interface
SQLite → Database
JDBC → Database connectivity
Eclipse IDE → Java development


📂 Project Structure
Solar-Panel-Monitoring-System/
│
├── panel_solar/              # C project (simulation)
│
├── solar_panel/              # Java project (Eclipse)
│   ├── src/solar_panel/
│   │   ├── SolarDashboard.java
│   │   ├── style.css
│   │   ├── image.png
│   │
│   ├── export_data.csv
│   └── solar_data.db
│
├── images/
│   └── dashboard.png         # Dashboard screenshot
│
├── README.md
└── .gitignore


⚙️ Features
✅ Solar data simulation using C
✅ JavaFX interactive dashboard
✅ Energy monitoring (kWh)
✅ Data visualization (UI + styling)
✅ SQLite database storage
✅ CSV export functionality

📸 Dashboard Preview
Below is a preview of the JavaFX dashboard:
images/dashboard.png

🔍 Description

Data is generated using the C simulation
Java processes and displays the data
SQLite stores the data
The dashboard shows energy values and UI components


🚀 Installation & Setup
1. Clone the repository
Shellgit clone https://github.com/kaoutar-haroucha/Solar-Panel-Monitoring-System.gitAfficher plus de lignes

2. Run C Simulation

Open panel_solar
Compile and run the program


3. Import Java Project into Eclipse

Open Eclipse
Go to:
File → Import → Existing Projects into Workspace


Select solar_panel


4. Configure JavaFX
Add VM options in Run Configuration:
Shell--module-path "PATH_TO_FX/lib" --add-modules javafx.controls,javafx.fxmlAfficher plus de lignes

5. Run the Application
Run:
SolarDashboard.java


🧪 How It Works

The C program generates solar data
The Java application reads and processes the data
The JavaFX dashboard visualizes the data
The SQLite database stores the data


🔮 Future Improvements

📊 Real-time data updates
🌐 Web-based dashboard
📡 IoT integration (real sensors)
🌙 Dark mode UI
🔔 Alert system


👩‍💻 Author
Kaoutar Haroucha
adam ouahid

📄 License
This project is for educational purposes only.
