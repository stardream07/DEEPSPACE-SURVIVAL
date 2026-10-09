package space.orpheus.model;

import java.util.Map;

/** Factory-created system strategies own their material requirements and simulation effects. */
public abstract class Subsystem {
    public enum Kind { OXYGEN, REACTOR, HULL, POWER, NAVIGATION }
    public final Kind kind;
    public final String name;
    public final double x, y;
    private double integrity = 100;
    protected Subsystem(Kind kind, String name, double x, double y) { this.kind=kind; this.name=name; this.x=x; this.y=y; }
    public double integrity() { return integrity; }
    public boolean online() { return integrity >= 70; }
    public void damage(double amount) { integrity = Math.max(0, integrity - amount); }
    public void restore() { integrity = 100; }
    public abstract Map<Simulation.Resource,Integer> repairCost();
    public abstract void apply(Simulation.Environment env, double dt);
    public static Subsystem create(Kind kind) {
        return switch (kind) {
            case OXYGEN -> new Subsystem(kind,"O2 SCRUBBER",4.5,7.5) {
                public Map<Simulation.Resource,Integer> repairCost() { return Map.of(Simulation.Resource.SCRAP,2,Simulation.Resource.WIRE,1); }
                public void apply(Simulation.Environment e,double dt) { e.oxygen += (online() ? .12 : -.30)*dt; }
            };
            case REACTOR -> new Subsystem(kind,"COOLANT PUMP",26.5,7.5) {
                public Map<Simulation.Resource,Integer> repairCost() { return Map.of(Simulation.Resource.SCRAP,1,Simulation.Resource.COOLANT,1); }
                public void apply(Simulation.Environment e,double dt) { e.temperature += (online() ? (e.temperature>42 ? -.35 : .06) : .55)*dt; }
            };
            case HULL -> new Subsystem(kind,"BULKHEAD SEAL",15.5,14.5) {
                public Map<Simulation.Resource,Integer> repairCost() { return Map.of(Simulation.Resource.SCRAP,3); }
                public void apply(Simulation.Environment e,double dt) { if (!online()) { e.hull-=.07*dt; e.oxygen-=.12*dt; } }
            };
            case POWER -> new Subsystem(kind,"POWER BUS",26.5,14.5) {
                public Map<Simulation.Resource,Integer> repairCost() { return Map.of(Simulation.Resource.WIRE,2); }
                public void apply(Simulation.Environment e,double dt) { e.power+=(online() ? (e.power<88 ? .25 : 0) : -.22)*dt; }
            };
            case NAVIGATION -> new Subsystem(kind,"NAV COMPUTER",16.5,2.5) {
                public Map<Simulation.Resource,Integer> repairCost() { return Map.of(Simulation.Resource.WIRE,1,Simulation.Resource.SCRAP,1); }
                public void apply(Simulation.Environment e,double dt) { }
            };
        };
    }
}
