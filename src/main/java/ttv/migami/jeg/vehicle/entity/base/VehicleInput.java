package ttv.migami.jeg.vehicle.entity.base;

public record VehicleInput(
        boolean forward,
        boolean backward,
        boolean left,
        boolean right,
        boolean brake,
        boolean ascend,
        boolean descend,
        boolean fire,
        boolean reload,
        boolean freeLook,
        boolean switchWeapon,
        boolean previousWeapon,
        int weaponSlot,
        boolean seekTarget,
        boolean deployDecoy,
        float mouseX,
        float mouseY,
        float cruiseSpeed,
        // Helicopter hand roll: -1 left, 1 right while a double-tapped strafe key stays held, else 0.
        int barrelRoll
) {
    public static final VehicleInput EMPTY = new VehicleInput(false, false, false, false, false, false, false, false, false, false, false, false, -1, false, false, 0.0F, 0.0F);

    public VehicleInput(boolean forward, boolean backward, boolean left, boolean right, boolean brake, boolean ascend, boolean descend,
                        boolean fire, boolean reload, boolean freeLook, boolean switchWeapon, boolean previousWeapon, int weaponSlot,
                        boolean seekTarget, boolean deployDecoy, float mouseX, float mouseY, float cruiseSpeed) {
        this(forward, backward, left, right, brake, ascend, descend, fire, reload, freeLook, switchWeapon, previousWeapon, weaponSlot,
                seekTarget, deployDecoy, mouseX, mouseY, cruiseSpeed, 0);
    }

    public VehicleInput(boolean forward, boolean backward, boolean left, boolean right, boolean brake, boolean ascend, boolean descend,
                        boolean fire, boolean reload, boolean freeLook, boolean switchWeapon, boolean previousWeapon, int weaponSlot,
                        boolean seekTarget, boolean deployDecoy, float mouseX, float mouseY) {
        this(forward, backward, left, right, brake, ascend, descend, fire, reload, freeLook, switchWeapon, previousWeapon, weaponSlot,
                seekTarget, deployDecoy, mouseX, mouseY, 0.0F);
    }

    public int forwardAxis() {
        return (this.forward ? 1 : 0) - (this.backward ? 1 : 0);
    }

    public int strafeAxis() {
        return (this.left ? 1 : 0) - (this.right ? 1 : 0);
    }

    public int verticalAxis() {
        return (this.ascend ? 1 : 0) - (this.descend ? 1 : 0);
    }
}
