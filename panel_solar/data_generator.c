#include "data_generator.h"
#include <stdio.h>

/* Conversion état → texte */
static const char* state_to_string(SystemState state)
{
    switch (state)
    {
    case STATE_NORMAL:  return "NORMAL";
    case STATE_WARNING: return "WARNING";
    case STATE_FAULT:   return "FAULT";
    default:            return "UNKNOWN";
    }
}

void generate_data_frame(const PV_Panel* panel)
{
    // 1. Sauvegarde dans le fichier texte (Optionnel mais gardé pour vous)
    FILE* file = fopen("C:/Users/kharoucha/source/repos/solar/x64/Debug/data.txt", "a");

    if (file != NULL)
    {
        fprintf(file,
            "V=%.2f;I=%.2f;T=%.2f;E=%.2f;P=%.2f;R=%.2f;"
            "SHADING=%d;OVERHEAT=%d;DEGRADATION=%d;"
            "STATE=%s\n",
            panel->voltage, panel->current, panel->temperature,
            panel->irradiance, panel->power, panel->efficiency,
            panel->shading_fault, panel->overheating_fault,
            panel->degradation_fault, state_to_string(panel->state)
        );
        fclose(file);
    }

    // 2. Affichage console (C'est ÇA que Java va écouter !)
    printf(
        "V=%.2f;I=%.2f;T=%.2f;E=%.2f;P=%.2f;R=%.2f;"
        "SHADING=%d;OVERHEAT=%d;DEGRADATION=%d;"
        "STATE=%s\n",
        panel->voltage, panel->current, panel->temperature,
        panel->irradiance, panel->power, panel->efficiency,
        panel->shading_fault, panel->overheating_fault,
        panel->degradation_fault, state_to_string(panel->state)
    );

    // 3. Forcer l'envoi de la trame vers Java immédiatement
    fflush(stdout);
}