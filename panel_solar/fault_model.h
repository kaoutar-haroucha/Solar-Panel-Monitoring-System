#ifndef FAULT_MODEL_H
#define FAULT_MODEL_H

#include "pv_model.h"

void detect_faults(PV_Panel* panel);
void compute_system_state(PV_Panel* panel);

#endif