package space.orpheus.model;

import java.util.*;
import java.util.concurrent.*;
import static space.orpheus.model.Subsystem.Kind.*;

/** The only mutable world owner. UI commands and scheduled ticks share this monitor. */
public final class Simulation implements AutoCloseable {
    public enum Phase { READY, RUNNING, PAUSED, LOST, WON }
    public enum Resource { SCRAP, WIRE, COOLANT, O2 }
    public enum Emergency { OXYGEN_LEAK, REACTOR_OVERHEAT, HULL_BREACH, POWER_SURGE, FIRE }
    public static final class Environment { public double oxygen=86,temperature=48,hull=92,power=84; }
    public record SystemView(Subsystem.Kind kind,String name,double x,double y,double integrity,boolean online,Map<Resource,Integer> cost) {}
    public record HazardView(Emergency type,Subsystem.Kind system,String message,double x,double y,double age) {}
    public record CrateView(int id,double x,double y,boolean collected) {}
    public record LogEntry(double time,String message,boolean warning) {}
    public record Snapshot(Phase phase,int level,double elapsed,double oxygen,double temperature,double hull,double power,
        double health,double x,double y,boolean moving,int facing,String room,Map<Resource,Integer> inventory,
        List<SystemView> systems,List<HazardView> hazards,List<CrateView> crates,List<LogEntry> log,
        String hint,String objective,boolean jumpReady,double repairProgress,String repairName,
        int[][] circuit,Set<Integer> energized,boolean circuitConnected,String circuitName,int repairs) {}
    private record Crate(int id,double x,double y) {}
    private static final class Hazard {
        final Emergency type; final Subsystem.Kind system; final String message; final double x,y; double age;
        Hazard(Emergency type,Subsystem.Kind system,String message,double x,double y) { this.type=type;this.system=system;this.message=message;this.x=x;this.y=y; }
    }
    public final ShipMap map=new ShipMap();
    private final EnumMap<Subsystem.Kind,Subsystem> systems=new EnumMap<>(Subsystem.Kind.class);
    private final EnumMap<Resource,Integer> inventory=new EnumMap<>(Resource.class);
    private final List<Hazard> hazards=new ArrayList<>();
    private final Deque<LogEntry> log=new ArrayDeque<>();
    private final List<Crate> crates=List.of(new Crate(0,4.5,14.5),new Crate(1,8.5,14.5),new Crate(2,19.5,14.5));
    private final Set<Integer> collected=new HashSet<>();
    private final Random random;
    private final ScheduledExecutorService scheduler;
    private Environment env;
    private Phase phase;
    private int level,facing,repairs;
    private double elapsed,levelTime,nextEmergency,x,y,health,inputX,inputY,repairTime;
    private Subsystem repairing,circuitSystem;
    private CircuitBoard circuit;
    private String notice="";
    private double noticeUntil;
    public Simulation() { this(42L,true); }
    public Simulation(long seed,boolean realtime) {
        random=new Random(seed); reset();
        scheduler=realtime ? Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"orpheus-simulation");t.setDaemon(true);return t;}) : null;
        if(scheduler!=null) scheduler.scheduleAtFixedRate(()-> { try { tick(.1); } catch(Exception e) { e.printStackTrace(); } },100,100,TimeUnit.MILLISECONDS);
    }
    public synchronized void reset() {
        env=new Environment(); phase=Phase.READY;level=1;elapsed=0;levelTime=0;nextEmergency=65;x=16.5;y=8.5;health=100;
        inputX=inputY=0;facing=0;repairs=0;repairTime=0;repairing=null;circuit=null;circuitSystem=null;
        hazards.clear();collected.clear();log.clear();systems.clear();inventory.clear();notice="";noticeUntil=0;
        for(Subsystem.Kind k:Subsystem.Kind.values()) systems.put(k,Subsystem.create(k));
        inventory.put(Resource.SCRAP,3);inventory.put(Resource.WIRE,2);inventory.put(Resource.COOLANT,1);inventory.put(Resource.O2,1);
        systems.get(OXYGEN).damage(65);systems.get(HULL).damage(50);
        hazards.add(new Hazard(Emergency.OXYGEN_LEAK,OXYGEN,"SCRUBBER OFFLINE / O2 FALLING",4.5,7.5));
        hazards.add(new Hazard(Emergency.HULL_BREACH,HULL,"BULKHEAD SEAL COMPROMISED",15.5,14.5));
        addLog("DISTRESS WAKEUP: 1 CREW SIGNATURE",true);addLog("SALVAGE CARGO. RESTORE LIFE SUPPORT.",false);
    }
    public synchronized void start() { if(phase==Phase.READY) { phase=Phase.RUNNING;addLog("WATCH STARTED. YOU HAVE THE SHIP.",false); } }
    public synchronized void pause() {
        if(phase==Phase.RUNNING) { phase=Phase.PAUSED;inputX=inputY=0; }
        else if(phase==Phase.PAUSED) phase=Phase.RUNNING;
    }
    public synchronized void movement(double dx,double dy) { inputX=dx;inputY=dy; }
    public synchronized void tick(double dt) {
        if(phase!=Phase.RUNNING||dt<=0) return;
        elapsed+=dt;levelTime+=dt;
        if(circuit==null) move(dt);
        for(Subsystem system:systems.values()) system.apply(env,dt);
        for(Hazard h:hazards) {
            h.age+=dt;
            if(h.type==Emergency.FIRE) { env.temperature+=.14*dt;if(distance(x,y,h.x,h.y)<2)health-=2.3*dt; }
        }
        if(env.oxygen<25) health-=(25-env.oxygen)*.035*dt;
        if(env.temperature>85) { env.hull-=(env.temperature-85)*.015*dt; if(map.roomAt(x,y).equals("REACTOR"))health-=.8*dt; }
        if(env.power<15) env.oxygen-=.2*dt;
        if(repairing!=null) {
            if(distance(x,y,repairing.x,repairing.y)>1.8) { repairing=null;repairTime=0;message("REPAIR INTERRUPTED / MATERIALS RETAINED"); }
            else { repairTime+=dt;if(repairTime>=4) completeRepair(repairing); }
        }
        if(levelTime>=nextEmergency) { Emergency[] options=Emergency.values(); triggerEmergency(options[random.nextInt(options.length)]);nextEmergency=levelTime+Math.max(28,65-level*9); }
        env.oxygen=clamp(env.oxygen,0,100);env.hull=clamp(env.hull,0,100);env.power=clamp(env.power,0,100);env.temperature=clamp(env.temperature,0,120);health=clamp(health,0,100);
        if(health<=0||env.hull<=0||env.oxygen<=0||env.temperature>=120) {
            phase=Phase.LOST;inputX=inputY=0;repairing=null;circuit=null;
            addLog(env.hull<=0?"HULL COLLAPSE / SIGNAL LOST":env.temperature>=120?"REACTOR MELTDOWN / SIGNAL LOST":"LIFE SIGNS LOST / MISSION FAILED",true);
        }
    }
    private void move(double dt) {
        double len=Math.hypot(inputX,inputY);if(len==0)return;
        if(repairing!=null) { repairing=null;repairTime=0;message("REPAIR INTERRUPTED / MATERIALS RETAINED"); }
        double speed=env.power<15?2.0:3.1,dx=inputX/len*speed*dt,dy=inputY/len*speed*dt;
        facing=Math.abs(inputX)>Math.abs(inputY)?(inputX>0?1:3):(inputY>0?0:2);
        // Substeps avoid clipping through walls if a caller uses a long deterministic tick.
        int steps=Math.max(1,(int)Math.ceil(Math.max(Math.abs(dx),Math.abs(dy))/.15));
        for(int i=0;i<steps;i++) { if(fits(x+dx/steps,y))x+=dx/steps;if(fits(x,y+dy/steps))y+=dy/steps; }
    }
    private boolean fits(double px,double py) { return map.walkable(px-.22,py-.22)&&map.walkable(px+.22,py-.22)&&map.walkable(px-.22,py+.22)&&map.walkable(px+.22,py+.22); }
    public synchronized void interact() {
        if(phase==Phase.READY) { start();return; }
        if(phase!=Phase.RUNNING||circuit!=null||repairing!=null)return;
        for(Crate c:crates) if(!collected.contains(c.id)&&distance(x,y,c.x,c.y)<1.8) {
            collected.add(c.id);give(Resource.SCRAP,4);give(Resource.WIRE,3);give(Resource.COOLANT,1);give(Resource.O2,1);
            addLog("SALVAGE: +4 SCRAP +3 WIRE +1 COOLANT +1 O2",false);message("SUPPLY CRATE SALVAGED");return;
        }
        Subsystem s=nearest();
        if(s==null) { message("MOVE CLOSER TO A SYSTEM OR SUPPLY CRATE");return; }
        if(s.kind==NAVIGATION&&s.online()&&jumpReady()) { advance();return; }
        if(s.integrity()>=99) { message(s.name+" / NOMINAL");return; }
        if(!canAfford(s)) { message("INSUFFICIENT MATERIALS / CHECK CARGO");return; }
        if(s.kind==POWER||s.kind==NAVIGATION) { circuitSystem=s;circuit=new CircuitBoard(random.nextLong());message("ROTATE TILES. CONNECT SOURCE TO SINK."); }
        else { repairing=s;repairTime=0;addLog("REPAIR STARTED: "+s.name,false); }
    }
    public synchronized void rotateCircuit(int cx,int cy) { if(phase==Phase.RUNNING&&circuit!=null)circuit.rotate(cx,cy); }
    public synchronized void commitCircuit() {
        if(phase!=Phase.RUNNING||circuit==null)return;
        if(!circuit.connected()) { message("OPEN CIRCUIT / SOURCE MUST REACH SINK");return; }
        if(!canAfford(circuitSystem)) { message("INSUFFICIENT MATERIALS");return; }
        Subsystem s=circuitSystem;circuit=null;circuitSystem=null;completeRepair(s);
    }
    public synchronized void closeCircuit() { circuit=null;circuitSystem=null; }
    public synchronized void useOxygen() {
        if(phase!=Phase.RUNNING)return;
        if(env.oxygen>=98) { message("OXYGEN RESERVE FULL");return; }
        if(use(Resource.O2,1)) { env.oxygen=Math.min(100,env.oxygen+25);message("O2 CANISTER / +25% RESERVE");addLog("EMERGENCY OXYGEN INJECTED",false); } else message("NO O2 CANISTERS / SALVAGE CARGO");
    }
    public synchronized void useCoolant() {
        if(phase!=Phase.RUNNING)return;
        if(env.temperature<=40) { message("REACTOR TEMPERATURE NOMINAL");return; }
        if(use(Resource.COOLANT,1)) { env.temperature=Math.max(30,env.temperature-25);message("COOLANT INJECTED / -25C");addLog("COOLANT INJECTED. PUMP STILL NEEDS REPAIR.",false); } else message("NO COOLANT / SALVAGE CARGO");
    }
    public synchronized void triggerEmergency(Emergency emergency) {
        if(phase!=Phase.RUNNING)return;
        Subsystem.Kind k=switch(emergency) { case OXYGEN_LEAK->OXYGEN;case REACTOR_OVERHEAT,FIRE->REACTOR;case HULL_BREACH->HULL;case POWER_SURGE->POWER; };
        Subsystem s=systems.get(k);s.damage(45);
        String text=switch(emergency) {
            case OXYGEN_LEAK->{env.oxygen-=5;yield "O2 LEAK / SCRUBBER FAILURE";}
            case REACTOR_OVERHEAT->{env.temperature+=12;yield "THERMAL RUNAWAY / COOLANT FAILURE";}
            case HULL_BREACH->{env.hull-=9;yield "MICROMETEOR IMPACT / HULL BREACH";}
            case POWER_SURGE->{env.power-=16;yield "BUS SHORT / EMERGENCY LIGHTING";}
            case FIRE->{env.temperature+=8;yield "REACTOR FIRE / KEEP YOUR DISTANCE";}
        };
        if(hazards.stream().noneMatch(h->h.type==emergency))hazards.add(new Hazard(emergency,k,text,s.x,s.y));
        addLog(text,true);message(text);
    }
    private void completeRepair(Subsystem s) {
        if(!canAfford(s)) { repairing=null;repairTime=0;message("REPAIR ABORTED / INSUFFICIENT MATERIALS");return; }
        s.repairCost().forEach((r,n)->use(r,n));s.restore();hazards.removeIf(h->h.system==s.kind);repairs++;
        switch(s.kind) { case OXYGEN->env.oxygen=Math.min(100,env.oxygen+10);case REACTOR->env.temperature=Math.max(42,env.temperature-18);case HULL->env.hull=Math.min(100,env.hull+12);case POWER->env.power=Math.min(100,env.power+25);default->{} }
        repairing=null;repairTime=0;addLog(s.name+" RESTORED / SYSTEM ONLINE",false);message(s.name+" RESTORED");
    }
    private boolean canAfford(Subsystem s) { return s.repairCost().entrySet().stream().allMatch(e->inventory.get(e.getKey())>=e.getValue()); }
    private void advance() {
        if(level==3) { phase=Phase.WON;inputX=inputY=0;addLog("EARTH SIGNAL ACQUIRED. WELCOME HOME.",false);return; }
        level++;levelTime=0;nextEmergency=55;collected.clear();give(Resource.O2,1);give(Resource.COOLANT,1);
        if(level==2) { systems.get(REACTOR).damage(65);systems.get(POWER).damage(65);triggerEmergency(Emergency.REACTOR_OVERHEAT);triggerEmergency(Emergency.POWER_SURGE);addLog("STAGE 02 / CROSS THE DEBRIS FIELD",true); }
        else { systems.get(NAVIGATION).damage(65);triggerEmergency(Emergency.FIRE);triggerEmergency(Emergency.HULL_BREACH);addLog("STAGE 03 / REBUILD THE EARTH VECTOR",true); }
        message("SECTOR TRANSIT COMPLETE / CARGO RESTOCKED");
    }
    private boolean jumpReady() {
        if(env.oxygen<30||env.hull<25||env.temperature>=85||env.power<25)return false;
        return switch(level) { case 1->systems.get(OXYGEN).online()&&systems.get(HULL).online();case 2->systems.get(REACTOR).online()&&systems.get(POWER).online();default->systems.values().stream().allMatch(Subsystem::online); };
    }
    public synchronized boolean jump() {
        if(phase!=Phase.RUNNING||!jumpReady()||distance(x,y,systems.get(NAVIGATION).x,systems.get(NAVIGATION).y)>1.8) { message("RESTORE SYSTEMS AND RETURN TO FLIGHT DECK");return false; }
        advance();return true;
    }
    private Subsystem nearest() { return systems.values().stream().filter(s->distance(x,y,s.x,s.y)<1.8).min(Comparator.comparingDouble(s->distance(x,y,s.x,s.y))).orElse(null); }
    private String hint() {
        if(elapsed<noticeUntil)return notice;
        if(repairing!=null)return "REPAIRING "+repairing.name+" / "+(int)(repairTime/4*100)+"% / STAY AT TERMINAL";
        for(Crate c:crates)if(!collected.contains(c.id)&&distance(x,y,c.x,c.y)<1.8)return "[E] SALVAGE SUPPLY CRATE";
        Subsystem s=nearest();if(s!=null) {
            if(s.kind==NAVIGATION&&jumpReady()&&s.online())return level==3?"[E] ENGAGE EARTH RETURN":"[E] JUMP TO NEXT SECTOR";
            if(s.integrity()<99) {
                StringJoiner cost=new StringJoiner(" + ");
                for(Resource r:Resource.values())if(s.repairCost().containsKey(r))cost.add(s.repairCost().get(r)+" "+(r==Resource.COOLANT?"CLNT":r.name()));
                return "[E] "+(s.kind==POWER||s.kind==NAVIGATION?"REWIRE ":"REPAIR ")+s.name+" / "+cost;
            }
            return s.name+" / NOMINAL";
        }
        return "WASD MOVE / E INTERACT / Q OXYGEN / F COOLANT";
    }
    private String objective() { return switch(level) {case 1->"RESTORE LIFE SUPPORT";case 2->"STABILIZE THE REACTOR";default->"FIND THE WAY HOME";}; }
    private void message(String s) { notice=s;noticeUntil=elapsed+4; }
    private void give(Resource r,int n) { inventory.merge(r,n,Integer::sum); }
    private boolean use(Resource r,int n) { if(inventory.get(r)<n)return false;inventory.put(r,inventory.get(r)-n);return true; }
    private void addLog(String s,boolean warning) { log.addFirst(new LogEntry(elapsed,s,warning));while(log.size()>30)log.removeLast(); }
    private static double distance(double x,double y,double px,double py) { return Math.hypot(x-px,y-py); }
    private static double clamp(double value,double lo,double hi) { return Math.max(lo,Math.min(hi,value)); }
    public synchronized Snapshot snapshot() {
        return new Snapshot(phase,level,elapsed,env.oxygen,env.temperature,env.hull,env.power,health,x,y,
            phase==Phase.RUNNING&&circuit==null&&(inputX!=0||inputY!=0),facing,map.roomAt(x,y),Map.copyOf(inventory),
            systems.values().stream().map(s->new SystemView(s.kind,s.name,s.x,s.y,s.integrity(),s.online(),s.repairCost())).toList(),
            hazards.stream().map(h->new HazardView(h.type,h.system,h.message,h.x,h.y,h.age)).toList(),
            crates.stream().map(c->new CrateView(c.id,c.x,c.y,collected.contains(c.id))).toList(),List.copyOf(log),
            hint(),objective(),jumpReady(),repairTime/4,repairing==null?"":repairing.name,
            circuit==null?null:circuit.copyPorts(),circuit==null?Set.of():circuit.energized(),circuit!=null&&circuit.connected(),circuitSystem==null?"":circuitSystem.name,repairs);
    }
    @Override public void close() { if(scheduler!=null)scheduler.shutdownNow(); }
}
