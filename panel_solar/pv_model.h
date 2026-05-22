#ifndef PV_MODEL_H
#define PV_MODEL_H

/* Scénarios de fonctionnement */
typedef enum
{
    SCENARIO_NORMAL,
    SCENARIO_SHADING,
    SCENARIO_OVERHEAT,
    SCENARIO_DEGRADATION
} PV_Scenario;

/* État global du système (dashboard Java) */
typedef enum
{
    STATE_NORMAL,
    STATE_WARNING,
    STATE_FAULT
} SystemState;

/* Structure du panneau photovoltaïque */
typedef struct
{
    float voltage;        // V
    float current;        // A
    float temperature;    // °C
    float irradiance;     // W/m²
    float power;          // W
    float efficiency;     // [0..1]

    int shading_fault;       // 0 ou 1
    int overheating_fault;  // 0 ou 1
    int degradation_fault;  // 0 ou 1

    PV_Scenario scenario; // scénario courant
    SystemState state;    // état global

} PV_Panel;

/* Fonctions du modèle PV */
void pv_init(PV_Panel* panel);
void pv_simulate(PV_Panel* panel);
void pv_calculate_efficiency(PV_Panel* panel);
void pv_calculate_power(PV_Panel* panel);

#endif#pragma once
