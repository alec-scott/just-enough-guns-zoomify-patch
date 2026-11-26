package ttv.migami.jeg.vehicle.client;

/** A living entity's render state, marked when the entity rides a vehicle's cab seat (LivingEntityRenderStateCabMixin). */
public interface CabSeatedState {
    boolean jeg$seatedInCab();

    void jeg$setSeatedInCab(boolean seated);
}
