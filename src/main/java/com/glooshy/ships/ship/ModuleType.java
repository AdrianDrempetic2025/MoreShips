package com.glooshy.ships.ship;

/**
 * Installable module types (RQCA-08).
 *
 * <p>Steering is NOT a module — the helm is part of the ship itself: whoever
 * rides the ship entity pilots it (center-turn, RQCA-15).
 *
 * <p>SEAT: right-click to sit (travels with the ship). CARGO: right-click
 * opens the cargo hold. CANNON: armament — firing arrives in a later slice
 * (RQCA-18..20).
 */
public enum ModuleType {
    SEAT,
    CARGO,
    CANNON,
    /** Boosts speed (CON-10: module weight/propulsion affects ship speed). */
    ENGINE,
    /** Adds bonus max HP while fitted (CON-11: module-driven statistics). */
    HEALTH
}
