# 🌞 Solar Panel Monitoring System

## 📌 Overview
The **Solar Panel Monitoring System** is a hybrid application that combines **C, Java, and SQLite** to simulate, process, and visualize solar energy data.

This project demonstrates a complete pipeline:
**C Simulation → Java Dashboard → SQLite Storage**

---

## 🎯 Objectives
- Simulate solar panel energy production using C  
- Develop a graphical dashboard using JavaFX  
- Store and manage data using SQLite  
- Build a multi-language integrated system  
- Visualize energy data in a clear interface  

---

## 🏗️ System Architecture
C Simulation (panel_solar)
↓
Java Application (JavaFX Dashboard)
↓
SQLite Database (solar_data.db)
---

## 🛠 Technologies Used

### 🔹 Programming Languages
- C → Data simulation  
- Java → Application logic  

### 🔹 Tools & Frameworks
- JavaFX → Graphical User Interface  
- SQLite → Database  
- JDBC → Database connectivity  
- Eclipse IDE → Java development  

---

## 📂 Project Structure


Solar-Panel-Monitoring-System/
│
├── panel_solar/              # C project
│
├── solar_panel/              # Java project
│   ├── src/solar_panel/
│   │   ├── SolarDashboard.java
│   │   ├── style.css
│   │   ├── image.png
│   │
│   ├── export_data.csv
│   └── solar_data.db
│
├── images/
│   └── dashboard.png
│
├── README.md
└── .gitignore

---

## ⚙️ Features
- ✅ Solar data simulation using C  
- ✅ JavaFX interactive dashboard  
- ✅ Energy monitoring (kWh)  
- ✅ SQLite database storage  
- ✅ Styled user interface  
- ✅ Data export to CSV  

---

## 📸 Dashboard Preview

images/dashboard.png

---

## 🚀 Setup & Run

### 1. Clone repository
```bash
git clone https://github.com/kaoutar-haroucha/Solar-Panel-Monitoring-System.git
2. Run C project

Open panel_solar
Compile and run

3. Run Java project

Import solar_panel into Eclipse
Run:

SolarDashboard.java

🧪 How It Works

C program generates solar data
Java application displays data (JavaFX)
SQLite stores the data


👩‍💻 Author
Kaoutar Haroucha
adam ouahid

📄 License
Educational project

