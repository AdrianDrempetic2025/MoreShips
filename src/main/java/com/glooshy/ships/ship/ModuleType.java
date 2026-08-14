package com.glooshy.ships.ship;

/**
 * Installable module types (RQCA-08). V1 ships the type system + items;
 * each type's capability (cargo storage, cannon firing, seat occupancy,
 * helm steering) arrives in its own later slice per RQCA-11.
 */
public enum ModuleType {
    HELM,
    SEAT,
    CARGO,
    CANNON
}
