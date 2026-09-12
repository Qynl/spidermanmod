package com.rivalrealms.entity;

/**
 * Common input contract shared by every player-piloted Rival Realms vehicle.
 *
 * <p>Minecraft 1.21.1 simulates player-driven vehicles on the riding client
 * and streams the result to the server through vanilla vehicle movement
 * packets. That path is activated by overriding
 * {@code isLogicalSideForUpdatingMovement()}, which every piloted vehicle in
 * this mod does. The actual key states are pushed into the entity from the
 * client source set (see {@code client/VehicleInputProxy}) because
 * {@code Input} is a client-only class.</p>
 */
public interface VehicleInput {
    /** Push the pilot's current key states into the vehicle. Called every client tick while piloting. */
    void applyPilotInput(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak);
}
