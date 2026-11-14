package ttv.migami.jeg.vehicle.client.audio;

import net.minecraft.client.resources.sounds.SoundInstance;

/**
 * A sound whose existing channel can switch between world and listener coordinates.
 * VehicleSoundEngineMixin updates OpenAL's relative flag alongside each tick's position, without restarting it.
 */
public interface DynamicRelativeSound extends SoundInstance {}
