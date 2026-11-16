package ttv.migami.jeg.item;

import ttv.migami.jeg.init.ModEntities;

/**
 * The 81 mm mortar, carried: right-click the ground to set it up facing where you look. It takes two seconds to set
 * the baseplate and lay the bipod before it can fire; sneak-clicking it with an empty hand packs it up again.
 */
public class MortarItem extends DeployableWeaponItem {
    public MortarItem(Properties properties) {
        super(properties, ModEntities.MORTAR);
    }
}
