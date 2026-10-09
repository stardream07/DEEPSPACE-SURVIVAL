package space.orpheus;

import space.orpheus.model.*;
import java.util.*;
import static space.orpheus.model.Subsystem.Kind.*;

/** Deterministic integration tests through public game commands, without a JavaFX runtime. */
public final class SimulationTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        topology();circuits();pauseAndCollision();repairAndCancellation();emergencies();wholeMission();failureAndRestart();scheduler();
        System.out.println("PASS "+assertions+" assertions: topology, BFS/DFS circuits, collisions, pause, repair transactions, all emergencies, three-stage Earth return, loss/restart, background clock");
    }
    private static void check(boolean condition,String message) {assertions++;if(!condition)throw new AssertionError(message);}
    private static void topology() {
        ShipMap map=new ShipMap();
        for(ShipMap.Room room:ShipMap.ROOMS)check(!map.route(new ShipMap.Point(16,8),new ShipMap.Point(room.x()+1,room.y()+1)).isEmpty(),room.name()+" reachable");
        check(!map.walkable(-.1,8),"negative boundary blocked");check(map.route(new ShipMap.Point(16,8),new ShipMap.Point(0,0)).isEmpty(),"void unreachable");
    }
    private static void circuits() {
        for(int seed=0;seed<80;seed++) {
            CircuitBoard board=new CircuitBoard(seed);check(!board.connected(),"board initially disconnected");solve(board);
            check(board.connected(),"generated puzzle has solution "+seed);check(board.inspectDfs(0,2).equals(board.energized()),"DFS and BFS agree");
            board.rotate(0,2);check(!board.connected(),"disconnecting source removes power");
        }
    }
    private static void solve(CircuitBoard board) {
        int[][] path={{0,2,CircuitBoard.E|CircuitBoard.W},{1,2,CircuitBoard.N|CircuitBoard.W},{1,1,CircuitBoard.S|CircuitBoard.E},{2,1,CircuitBoard.E|CircuitBoard.W},{3,1,CircuitBoard.S|CircuitBoard.W},{3,2,CircuitBoard.N|CircuitBoard.E},{4,2,CircuitBoard.E|CircuitBoard.W}};
        for(int[] p:path)for(int i=0;i<4&&board.ports(p[0],p[1])!=p[2];i++)board.rotate(p[0],p[1]);
    }
    private static void pauseAndCollision() {
        try(Simulation sim=new Simulation(7,false)) {
            sim.tick(10);check(sim.snapshot().elapsed()==0,"ready clock frozen");sim.start();sim.movement(0,-1);sim.tick(20);
            check(sim.map.walkable(sim.snapshot().x(),sim.snapshot().y()),"long movement cannot tunnel through wall");check(sim.snapshot().y()>1,"outer wall collision");
            sim.pause();double clock=sim.snapshot().elapsed(),o2=sim.snapshot().oxygen();sim.tick(100);check(sim.snapshot().elapsed()==clock&&sim.snapshot().oxygen()==o2,"pause freezes hazards");
        }
    }
    private static void repairAndCancellation() {
        try(Simulation sim=new Simulation(7,false)) {
            sim.start();walk(sim,4.5,14.5);int scrap=sim.snapshot().inventory().get(Simulation.Resource.SCRAP);sim.interact();
            check(sim.snapshot().inventory().get(Simulation.Resource.SCRAP)==scrap+4,"salvage gains materials");sim.interact();check(sim.snapshot().inventory().get(Simulation.Resource.SCRAP)==scrap+4,"crate collected once");
            walk(sim,4.5,7.5);sim.interact();sim.tick(1);check(sim.snapshot().repairProgress()>0,"repair timed");int before=sim.snapshot().inventory().get(Simulation.Resource.SCRAP);
            sim.movement(1,0);sim.tick(.1);sim.movement(0,0);check(sim.snapshot().repairProgress()==0,"movement interrupts repair");check(sim.snapshot().inventory().get(Simulation.Resource.SCRAP)==before,"cancel retains materials");
            sim.interact();sim.tick(4.1);check(system(sim,OXYGEN).online(),"repair restores subsystem");check(sim.snapshot().inventory().get(Simulation.Resource.SCRAP)==before-2,"repair consumes exact cost");
            check(sim.snapshot().hazards().stream().noneMatch(h->h.system()==OXYGEN),"repair clears corresponding alert");
        }
    }
    private static void emergencies() {
        for(Simulation.Emergency type:Simulation.Emergency.values())try(Simulation sim=new Simulation(5,false)) {
            sim.start();var before=sim.snapshot();sim.triggerEmergency(type);var after=sim.snapshot();check(after.hazards().stream().anyMatch(h->h.type()==type),type+" alert exists");
            switch(type) {
                case OXYGEN_LEAK->check(after.oxygen()<before.oxygen(),"leak loses oxygen");
                case REACTOR_OVERHEAT->check(after.temperature()>before.temperature(),"overheat increases temperature");
                case HULL_BREACH->check(after.hull()<before.hull(),"impact damages hull");
                case POWER_SURGE->check(after.power()<before.power(),"surge drains power");
                case FIRE->{check(after.temperature()>before.temperature(),"fire heats reactor");walk(sim,26.5,7.5);double health=sim.snapshot().health();sim.tick(2);check(sim.snapshot().health()<health,"fire injures nearby crew");}
            }
        }
        try(Simulation sim=new Simulation(1,false)) {sim.start();double o2=sim.snapshot().oxygen();sim.useOxygen();check(sim.snapshot().oxygen()>o2&&sim.snapshot().inventory().get(Simulation.Resource.O2)==0,"oxygen canister consumed");double temp=sim.snapshot().temperature();sim.useCoolant();check(sim.snapshot().temperature()<temp,"coolant reduces temperature");}
    }
    private static Simulation.SystemView system(Simulation sim,Subsystem.Kind kind) {return sim.snapshot().systems().stream().filter(s->s.kind()==kind).findFirst().orElseThrow();}
    private static void walk(Simulation sim,double targetX,double targetY) {
        var snap=sim.snapshot();List<ShipMap.Point> path=sim.map.route(new ShipMap.Point((int)snap.x(),(int)snap.y()),new ShipMap.Point((int)targetX,(int)targetY));
        check(!path.isEmpty(),"walking target reachable");
        for(ShipMap.Point point:path) {
            double px=point.x()+.5,py=point.y()+.5;
            for(int i=0;i<80;i++) {snap=sim.snapshot();double dx=px-snap.x(),dy=py-snap.y(),dist=Math.hypot(dx,dy);if(dist<.01)break;
                sim.movement(dx,dy);sim.tick(Math.min(.04,dist/3.1));
                if(i==79)throw new AssertionError("Walk stalled at "+snap.x()+","+snap.y()+" to "+px+","+py+" phase="+snap.phase());
            }
        }
        sim.movement(0,0);
    }
    private static void repair(Simulation sim,Subsystem.Kind kind) {
        var system=system(sim,kind);walk(sim,system.x(),system.y());sim.interact();
        if(sim.snapshot().circuit()!=null) {
            int[][] path={{0,2,CircuitBoard.E|CircuitBoard.W},{1,2,CircuitBoard.N|CircuitBoard.W},{1,1,CircuitBoard.S|CircuitBoard.E},{2,1,CircuitBoard.E|CircuitBoard.W},{3,1,CircuitBoard.S|CircuitBoard.W},{3,2,CircuitBoard.N|CircuitBoard.E},{4,2,CircuitBoard.E|CircuitBoard.W}};
            for(int[] p:path)for(int i=0;i<4&&sim.snapshot().circuit()[p[1]][p[0]]!=p[2];i++)sim.rotateCircuit(p[0],p[1]);
            sim.commitCircuit();
        }else sim.tick(4.1);
        check(system(sim,kind).online(),kind+" repaired with public commands");
    }
    private static void wholeMission() {
        try(Simulation sim=new Simulation(42,false)) {
            sim.start();check(!sim.jump(),"cannot skip stage");walk(sim,4.5,14.5);sim.interact();repair(sim,OXYGEN);repair(sim,HULL);
            check(sim.snapshot().jumpReady(),"stage one ready");walk(sim,16.5,2.5);sim.interact();check(sim.snapshot().level()==2,"flight deck advances sector two");
            walk(sim,4.5,14.5);sim.interact();repair(sim,REACTOR);repair(sim,POWER);check(sim.snapshot().jumpReady(),"stage two ready");
            walk(sim,16.5,2.5);sim.interact();check(sim.snapshot().level()==3,"flight deck advances sector three");
            walk(sim,4.5,14.5);sim.interact();repair(sim,HULL);repair(sim,REACTOR);repair(sim,NAVIGATION);
            check(sim.snapshot().jumpReady(),"Earth vector ready");sim.interact();check(sim.snapshot().phase()==Simulation.Phase.WON,"three-stage mission winnable");
            double t=sim.snapshot().elapsed();sim.tick(5);check(sim.snapshot().elapsed()==t,"won state stops clock");
        }
    }
    private static void failureAndRestart() {
        try(Simulation sim=new Simulation(8,false)) {sim.start();sim.tick(400);check(sim.snapshot().phase()==Simulation.Phase.LOST,"unattended damaged ship loses");sim.reset();check(sim.snapshot().level()==1&&sim.snapshot().phase()==Simulation.Phase.READY&&sim.snapshot().health()==100,"restart resets world");}
    }
    private static void scheduler() throws InterruptedException {
        try(Simulation sim=new Simulation(42,true)) {sim.start();Thread.sleep(320);check(sim.snapshot().elapsed()>=.2,"background simulation ticks");sim.pause();double t=sim.snapshot().elapsed();Thread.sleep(180);check(sim.snapshot().elapsed()==t,"background pause is stable");}
    }
}
