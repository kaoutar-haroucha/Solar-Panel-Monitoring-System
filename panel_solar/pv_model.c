#include "pv_model.h"
#include <stdlib.h>

/* Constantes physiques */
#define V_NOMINAL 18.0f
#define I_NOMINAL 5.0f
#define TEMP_NOMINAL 25.0f
#define IRRADIANCE_MAX 1000.0f
#define PANEL_SURFACE 1.6f
#define EFFICIENCY_NOMINAL 0.18f   // 18 %

/* Génère un float aléatoire */
static float random_float(float min, float max)
{
    return min + (float)rand() / RAND_MAX * (max - min);
}

void pv_init(PV_Panel* panel)
{
    panel->scenario = SCENARIO_NORMAL;
    panel->state = STATE_NORMAL;

    panel->voltage = V_NOMINAL;
    panel->current = I_NOMINAL;
    panel->temperature = TEMP_NOMINAL;
    panel->irradiance = 800.0f;
    panel->power = 0.0f;
    panel->efficiency = EFFICIENCY_NOMINAL;

    panel->shading_fault = 0;
    panel->overheating_fault = 0;
    panel->degradation_fault = 0;
}

/* Génère les valeurs selon le scénario */
void pv_simulate(PV_Panel* panel)
{
    switch (panel->scenario)
    {
    case SCENARIO_NORMAL:
        panel->irradiance = random_float(700.0f, 1000.0f);
        panel->temperature = random_float(25.0f, 50.0f);
        break;

    case SCENARIO_SHADING:
        panel->irradiance = random_float(300.0f, 480.0f);
        panel->temperature = random_float(25.0f, 45.0f);
        break;

    case SCENARIO_OVERHEAT:
        panel->irradiance = random_float(700.0f, 1000.0f);
        panel->temperature = random_float(80.0f, 95.0f);
        break;

    case SCENARIO_DEGRADATION:
        panel->irradiance = random_float(700.0f, 1000.0f);
        panel->temperature = random_float(40.0f, 60.0f);
        break;
    }

    panel->current = I_NOMINAL * (panel->irradiance / IRRADIANCE_MAX);
    panel->voltage = V_NOMINAL + random_float(-1.0f, 1.0f);
}

/* Rendement variable et réaliste */
void pv_calculate_efficiency(PV_Panel* panel)
{
    float temp_factor = 1.0f - 0.004f * (panel->temperature - 25.0f);
    if (temp_factor < 0.7f)
        temp_factor = 0.7f;

    float irradiance_factor = panel->irradiance / IRRADIANCE_MAX;

    float aging_factor;
    if (panel->scenario == SCENARIO_DEGRADATION)
        aging_factor = random_float(0.70f, 0.85f);
    else
        aging_factor = random_float(0.95f, 1.0f);

    panel->efficiency = EFFICIENCY_NOMINAL *
        temp_factor *
        irradiance_factor *
        aging_factor;
}

/* Calcul de la puissance */
void pv_calculate_power(PV_Panel* panel)
{
    panel->power = panel->irradiance *
        PANEL_SURFACE *
        panel->efficiency;
}