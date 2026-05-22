#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <windows.h>

#include "pv_model.h"
#include "fault_model.h"
#include "data_generator.h"

int main(void)
{
    PV_Panel panel;
    srand((unsigned int)time(NULL));

    pv_init(&panel);

    printf("=== Simulation panneau photovoltaique ===\n\n");

    while (1)
    {
        int r = rand() % 100;

        if (r < 60)
            panel.scenario = SCENARIO_NORMAL;
        else if (r < 80)
            panel.scenario = SCENARIO_SHADING;
        else if (r < 90)
            panel.scenario = SCENARIO_OVERHEAT;
        else
            panel.scenario = SCENARIO_DEGRADATION;

        pv_simulate(&panel);
        pv_calculate_efficiency(&panel);
        pv_calculate_power(&panel);

        detect_faults(&panel);
        compute_system_state(&panel);

        generate_data_frame(&panel);

        Sleep(1000);
    }

    return 0;
}