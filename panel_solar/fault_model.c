#include "fault_model.h"

#define IRRADIANCE_THRESHOLD 500.0f
#define TEMP_THRESHOLD 80.0f
#define EFFICIENCY_THRESHOLD 0.10f

void detect_faults(PV_Panel* panel)
{
    panel->shading_fault = 0;
    panel->overheating_fault = 0;
    panel->degradation_fault = 0;

    if (panel->irradiance < IRRADIANCE_THRESHOLD)
        panel->shading_fault = 1;

    if (panel->temperature > TEMP_THRESHOLD)
        panel->overheating_fault = 1;

    if (panel->efficiency < EFFICIENCY_THRESHOLD)
        panel->degradation_fault = 1;
}

/* Calcul de l'état global */
void compute_system_state(PV_Panel* panel)
{
    if (panel->overheating_fault || panel->degradation_fault)
        panel->state = STATE_FAULT;
    else if (panel->shading_fault)
        panel->state = STATE_WARNING;
    else
        panel->state = STATE_NORMAL;
}
