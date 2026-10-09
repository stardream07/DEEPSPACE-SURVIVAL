package space.orpheus.ui;

import javafx.scene.canvas.*;
import javafx.scene.image.*;
import javafx.scene.paint.Color;
import space.orpheus.model.*;
import java.util.*;
import static space.orpheus.ui.Palette.*;
import static space.orpheus.model.Subsystem.Kind.*;

public final class GameRenderer {
    public static final int WIDTH=640,HEIGHT=400,MX=33,MY=94,TILE=13;
    private final Canvas buffer=new Canvas(WIDTH,HEIGHT);
    private final WritableImage pixels=new WritableImage(WIDTH,HEIGHT);
    private final GraphicsContext g=buffer.getGraphicsContext2D();
    private final ShipMap map;
    private final int[][] stars=new int[95][3];
    public boolean help=false,route=false;
    public double pointerX=-1,pointerY=-1;
    public record Hit(String action,int x,int y,int w,int h) { public boolean contains(double px,double py) {return px>=x&&py>=y&&px<x+w&&py<y+h;} }
    private final List<Hit> hits=new ArrayList<>();
    public GameRenderer(ShipMap map) { this.map=map;Random r=new Random(83);for(int[] s:stars){s[0]=r.nextInt(456);s[1]=r.nextInt(227);s[2]=r.nextInt(6);} }
    public List<Hit> hits() { return List.copyOf(hits); }
    public void render(Canvas canvas,Simulation.Snapshot s,double time) {
        hits.clear();r(VOID,0,0,WIDTH,HEIGHT);header(s);metrics(s);world(s,time);sidebar(s,time);bottom(s);
        if(s.circuit()!=null)circuit(s);
        if(s.phase()==Simulation.Phase.PAUSED)pauseOverlay();
        if(s.phase()==Simulation.Phase.WON||s.phase()==Simulation.Phase.LOST)ending(s);
        if(help)helpOverlay();
        buffer.snapshot(null,pixels);
        GraphicsContext target=canvas.getGraphicsContext2D();target.setImageSmoothing(false);target.setFill(VOID);target.fillRect(0,0,canvas.getWidth(),canvas.getHeight());
        double scale=scale(canvas),ox=(canvas.getWidth()-WIDTH*scale)/2,oy=(canvas.getHeight()-HEIGHT*scale)/2;
        target.drawImage(pixels,ox,oy,WIDTH*scale,HEIGHT*scale);
    }
    public static double scale(Canvas c) { double fit=Math.min(c.getWidth()/WIDTH,c.getHeight()/HEIGHT);return fit>=1?Math.floor(fit):fit; }
    public double logicalX(Canvas c,double x) { double scale=scale(c);return (x-(c.getWidth()-WIDTH*scale)/2)/scale; }
    public double logicalY(Canvas c,double y) { double scale=scale(c);return (y-(c.getHeight()-HEIGHT*scale)/2)/scale; }
    private void r(Color c,int x,int y,int w,int h) {g.setFill(c);g.fillRect(x,y,w,h);}
    private void t(String text,int x,int y,Color c) {PixelFont.draw(g,text,x,y,c,1);}
    private void title(String text,int x,int y,Color c) {PixelFont.draw(g,text,x,y,c,2);}
    private void panel(int x,int y,int w,int h) {r(STEEL,x,y,w,h);r(PANEL,x+1,y+1,w-2,h-2);}
    private void button(String action,String label,int x,int y,int w,Color c) {boolean hover=pointerX>=x&&pointerX<x+w&&pointerY>=y&&pointerY<y+19;r(hover?WHITE:c,x,y,w,19);r(hover?INDIGO:PANEL,x+1,y+1,w-2,17);t(label,x+(w-PixelFont.width(label,1))/2,y+6,hover?WHITE:c);hits.add(new Hit(action,x,y,w,19));}
    private void header(Simulation.Snapshot s) {
        r(PANEL,12,11,23,24);r(CYAN,17,15,3,13);r(CYAN,27,15,3,13);r(CYAN,20,13,7,3);r(CYAN,20,27,7,3);r(VOID,21,17,5,9);
        title("ORPHEUS",44,11,WHITE);t("DEEP-SPACE SURVIVAL",45,31,MUTED);
        t("A.S.V. ORPHEUS / CREW: 01",229,13,MUTED);t("RETURN VECTOR: EARTH",229,27,CYAN);
        t("SECTOR",408,13,MUTED);title("0"+s.level(),450,10,AMBER);t("/ 03",478,18,MUTED);
        button("pause",s.phase()==Simulation.Phase.PAUSED?"RESUME":"PAUSE",527,12,67,MUTED);button("help","?",603,12,25,CYAN);
        r(STEEL,12,41,616,1);
    }
    private void metrics(Simulation.Snapshot s) {
        meter("O2","OXYGEN",s.oxygen(),String.format(Locale.ROOT,"%02.0f%%",s.oxygen()),12,CYAN,s.oxygen()<30);
        meter("REACTOR","REACTOR",s.temperature(),String.format(Locale.ROOT,"%02.0fC",s.temperature()),168,AMBER,s.temperature()>80);
        meter("HULL","HULL",s.hull(),String.format(Locale.ROOT,"%02.0f%%",s.hull()),324,GREEN,s.hull()<35);
        meter("POWER","POWER",s.power(),String.format(Locale.ROOT,"%02.0f%%",s.power()),480,VIOLET,s.power()<25);
    }
    private void meter(String icon,String label,double value,String number,int x,Color color,boolean critical) {
        panel(x,48,148,34);Color c=critical?RED:color;ShipArt.icon(g,icon,x+9,55,c);t(label,x+25,54,MUTED);t(number,x+112,54,c);
        r(DARK,x+25,68,111,5);int filled=(int)Math.min(22,Math.max(0,value/100*22));
        for(int i=0;i<22;i++)r(i<filled?c:INDIGO,x+25+i*5,68,3,5);
    }
    private void world(Simulation.Snapshot s,double time) {
        panel(12,91,460,240);r(VOID,13,92,458,238);
        for(int[] star:stars) {int py=96+star[1];r(star[2]==0?STEEL:INDIGO,17+star[0],py,star[2]==0?2:1,1);}
        planet(s.level(),428,115);
        if(s.level()==2) {
            for(int i=0;i<7;i++) {
                int dx=21+(int)((time*9+i*67)%430),dy=112+(i*43)%202;
                r(STEEL,dx,dy,3+i%2,2);r(INDIGO,dx-4,dy+1,4,1);
            }
        }
        // Angular hull silhouette and wall tiles follow the actual collision topology.
        for(int y=0;y<ShipMap.HEIGHT;y++)for(int x=0;x<ShipMap.WIDTH;x++)if(map.walkable(x,y)) {
            int px=MX+x*TILE,py=MY+y*TILE;r(STEEL,px-3,py-3,TILE+6,TILE+6);r(EDGE,px-2,py-3,TILE+4,1);
        }
        for(int y=0;y<ShipMap.HEIGHT;y++)for(int x=0;x<ShipMap.WIDTH;x++)if(map.walkable(x,y)) {
            int px=MX+x*TILE,py=MY+y*TILE;String room=map.roomAt(x+.5,y+.5);
            Color floor=room.equals("ACCESS CORRIDOR")?INDIGO:FLOOR;r(floor,px,py,TILE,TILE);
            r(PANEL,px,py,TILE,1);r(PANEL,px,py,1,TILE);r(STEEL,px+2,py+2,1,1);
            if((x+y)%4==0)r(INDIGO,px+4,py+8,5,1);
            if(!map.walkable(x,y-1)) {r(EDGE,px,py-3,TILE,1);r(INDIGO,px,py-1,TILE,1);}
            if(!map.walkable(x,y+1))r(DARK,px,py+TILE-2,TILE,2);
        }
        roomDetails(s,time);
        if(route)drawRoute(s);
        for(Simulation.CrateView c:s.crates())ShipArt.crate(g,wx(c.x()),wy(c.y()),c.collected());
        for(Simulation.SystemView system:s.systems()) {
            int x=wx(system.x()),y=wy(system.y());Color status=system.online()?CYAN:RED;boolean blink=(int)(time*2)%2==0;
            switch(system.kind()) {case REACTOR->ShipArt.reactor(g,x,y,status,(int)(time*3));case OXYGEN->ShipArt.oxygen(g,x,y,status);default->ShipArt.console(g,x,y,status,blink);}
            if(!system.online()) {r(DARK,x-5,y-20,10,10);t("!",x-2,y-19,blink?AMBER:RED);}
            if(Math.hypot(s.x()-system.x(),s.y()-system.y())<1.8) {r(AMBER,x-15,y+16,30,1);}
        }
        for(Simulation.HazardView h:s.hazards()) {
            if(h.type()==Simulation.Emergency.FIRE)ShipArt.fire(g,wx(h.x())-18,wy(h.y())+4,(int)(time*5));
            if(h.type()==Simulation.Emergency.HULL_BREACH) {int x=wx(h.x())+22,y=wy(h.y())+13;r(DARK,x,y,2,9);r(DARK,x+2,y+6,4,2);r(DARK,x+5,y+6,2,8);r(RED,x+8,y+8,1,1);}
            if(h.type()==Simulation.Emergency.POWER_SURGE&&(int)(time*4)%5==0)r(AMBER,wx(h.x())+13,wy(h.y())-6,3,1);
        }
        int step=s.moving()?(int)(time*7):0;ShipArt.astronaut(g,wx(s.x()),wy(s.y()),s.facing(),step);
        // Power loss affects room illumination, not just the meter.
        if(s.power()<25) {g.setGlobalAlpha(.30);r(VOID,14,105,456,221);g.setGlobalAlpha(1);for(ShipMap.Room room:ShipMap.ROOMS)r(RED,MX+room.x()*TILE+4,MY+room.y()*TILE+4,3,2);}
        t("DECK 01 / "+s.room(),21,97,MUTED);t(route?"ROUTE ASSIST ON":"[TAB] ROUTE ASSIST",340,321,route?CYAN:MUTED);
        if(s.repairProgress()>0) {int px=wx(s.x())-17,py=wy(s.y())+13;r(DARK,px,py,35,5);r(AMBER,px+1,py+1,(int)(33*s.repairProgress()),3);}
        if(s.phase()==Simulation.Phase.READY) {panel(145,214,187,46);t("EMERGENCY WAKEUP",165,224,AMBER);t("THE SHIP IS YOURS NOW.",165,238,WHITE);}
    }
    private void roomDetails(Simulation.Snapshot s,double time) {
        for(ShipMap.Room room:ShipMap.ROOMS) {
            int x=MX+room.x()*TILE,y=MY+room.y()*TILE,w=room.width()*TILE,h=room.height()*TILE;
            t(room.name(),x+5,y+h-10,MUTED);
            r(STEEL,x+3,y+7,3,h-24);r(EDGE,x+4,y+7,1,h-24);
            for(int i=0;i<3;i++) {r(DARK,x+w-10,y+9+i*6,7,3);r(STEEL,x+w-9,y+9+i*6,5,1);}
            r(DARK,x+9,y+4,16,3);r(s.power()<25?RED:CYAN,x+11,y+4,9,1);
        }
        // Flight deck glazing and a shared instrument rail echo the supplied cockpit.
        int bx=MX+11*TILE,by=MY+TILE;r(DARK,bx+8,by+3,114,10);
        for(int i=0;i<4;i++){r(INDIGO,bx+10+i*27,by+4,23,7);r(STEEL,bx+34+i*27,by+3,3,10);}
        r(CYAN,bx+64,by+6,1,1);r(VIOLET,bx+39,by+5,2,1);
        for(int i=0;i<4;i++) {int px=bx+13+i*25;r(STEEL,px,by+29,15,8);r(DARK,px+2,by+30,11,3);r(i%2==0?AMBER:GREEN,px+3,by+31,4,1);}
        // Bunks, supply racks, bundled conduits; no random repeated noise.
        int cx=MX+2*TILE,cy=MY+12*TILE;
        for(int i=0;i<3;i++){r(STEEL,cx+70,cy+6+i*11,22,8);r(VIOLET,cx+72,cy+8+i*11,18,4);r(EDGE,cx+73,cy+7+i*11,3,2);}
        int ex=MX+22*TILE,ey=MY+12*TILE;r(STEEL,ex+42,ey+6,30,8);r(DARK,ex+45,ey+8,23,4);r(VIOLET,ex+47,ey+9,17,1);
        for(int i=0;i<5;i++){r(STEEL,ex+56+i*6,ey+24,4,21);r(INDIGO,ex+57+i*6,ey+26,2,16);}
        int hx=MX+12*TILE,hy=MY+12*TILE;r(STEEL,hx+64,hy+6,32,22);r(INDIGO,hx+66,hy+8,28,18);r(EDGE,hx+67,hy+9,26,1);
        for(int i=0;i<4;i++)r(STEEL,hx+69,hy+12+i*3,19,1);
        // Corridor direction markings, repeated at a single consistent pixel scale.
        for(int y=6;y<12;y+=2){int px=MX+15*TILE,py=MY+y*TILE;r(AMBER,px+4,py+3,3,1);r(AMBER,px+5,py+2,1,3);}
        r(STEEL,MX+11*TILE-1,MY+8*TILE,2,26);r(STEEL,MX+22*TILE-1,MY+8*TILE,2,26);
    }
    private void planet(int level,int x,int y) {
        Color lit=level==1?VIOLET:level==2?AMBER:CYAN;
        for(int yy=-13;yy<=13;yy++)for(int xx=-13;xx<=13;xx++)if(xx*xx+yy*yy<=169) {
            Color c=xx+yy*.6<-3?lit:INDIGO;
            if((xx*3+yy*7)%11==0&&c==lit)c=STEEL;
            if(level==3&&c==lit&&(Math.floorMod(xx*7+yy*3,23)<8))c=GREEN;
            if(xx>6)c=DARK;r(c,x+xx,y+yy,1,1);
        }
        r(INDIGO,x-21,y+3,10,1);r(INDIGO,x+12,y-4,10,1);
    }
    private void drawRoute(Simulation.Snapshot s) {
        Simulation.SystemView target=s.systems().stream().filter(v->!v.online()).findFirst().orElse(s.systems().stream().filter(v->v.kind()==NAVIGATION).findFirst().orElseThrow());
        List<ShipMap.Point> path=map.route(new ShipMap.Point((int)s.x(),(int)s.y()),new ShipMap.Point((int)target.x(),(int)target.y()));
        for(ShipMap.Point p:path)r(CYAN,MX+p.x()*TILE+6,MY+p.y()*TILE+6,2,2);
    }
    private int wx(double x) {return MX+(int)(x*TILE);}
    private int wy(double y) {return MY+(int)(y*TILE);}
    private void sidebar(Simulation.Snapshot s,double time) {
        panel(482,91,146,240);t("MISSION / 0"+s.level(),491,100,AMBER);r(STEEL,490,114,130,1);
        String[] names={"THE LONG SILENCE","DEBRIS CROSSING","HOMEWARD BOUND"};t(names[s.level()-1],491,122,WHITE);
        PixelFont.wrap(g,s.objective(),491,138,21,MUTED);
        Subsystem.Kind[] objectives=s.level()==1?new Subsystem.Kind[]{OXYGEN,HULL}:s.level()==2?new Subsystem.Kind[]{REACTOR,POWER}:new Subsystem.Kind[]{NAVIGATION,REACTOR,HULL};
        int yy=169;
        for(Subsystem.Kind k:objectives) {
            Simulation.SystemView v=s.systems().stream().filter(sys->sys.kind()==k).findFirst().orElseThrow();r(v.online()?GREEN:STEEL,491,yy,7,7);r(PANEL,492,yy+1,5,5);
            if(v.online()){r(GREEN,493,yy+3,2,2);r(GREEN,495,yy+1,1,3);}t(v.name(),504,yy,v.online()?GREEN:MUTED);yy+=13;
        }
        r(STEEL,490,yy+2,130,1);yy+=12;
        t(s.hazards().isEmpty()?"SYSTEMS NOMINAL":"! ACTIVE EMERGENCIES",491,yy,s.hazards().isEmpty()?GREEN:RED);yy+=14;
        for(int i=0;i<Math.min(3,s.hazards().size());i++) {String name=s.hazards().get(i).type().name().replace('_',' ');t(name,491,yy,AMBER);yy+=12;}
        if(s.hazards().size()>3)t("+"+(s.hazards().size()-3)+" ADDITIONAL",491,yy,RED);
        t("SUIT",491,284,MUTED);r(DARK,527,284,58,5);r(s.health()<30?RED:GREEN,527,284,(int)(58*s.health()/100),5);t(String.format(Locale.ROOT,"%02.0f",s.health()),595,283,WHITE);
        if(s.phase()==Simulation.Phase.READY)button("start","BEGIN WATCH >",490,303,130,AMBER);
        else if(s.jumpReady())button("jump",s.level()==3?"RETURN TO EARTH":"JUMP AVAILABLE",490,303,130,GREEN);
        else button("route",route?"HIDE ROUTE [TAB]":"SHOW ROUTE [TAB]",490,303,130,CYAN);
    }
    private void bottom(Simulation.Snapshot s) {
        r(PANEL,12,335,616,16);t(s.hint(),20,340,s.repairProgress()>0?AMBER:CYAN);
        t("CARGO",14,358,MUTED);
        Simulation.Resource[] resources=Simulation.Resource.values();Color[] colors={MUTED,AMBER,CYAN,GREEN};
        for(int i=0;i<4;i++) {int x=55+i*59;panel(x,355,55,27);ShipArt.icon(g,resources[i].name(),x+5,362,colors[i]);t(""+s.inventory().get(resources[i]),x+21,360,WHITE);t(resources[i].name().equals("COOLANT")?"CLNT":resources[i].name(),x+21,370,MUTED);
            if(resources[i]==Simulation.Resource.O2)hits.add(new Hit("oxygen",x,355,55,27));if(resources[i]==Simulation.Resource.COOLANT)hits.add(new Hit("coolant",x,355,55,27));
        }
        r(STEEL,302,355,1,30);t("SHIP LOG",314,357,MUTED);
        for(int i=0;i<Math.min(2,s.log().size());i++) {Simulation.LogEntry entry=s.log().get(i);String message=entry.message();if(message.length()>43)message=message.substring(0,42)+">";t(message,314,369+i*10,entry.warning()?AMBER:CYAN);}
        r(STEEL,12,387,616,1);t("WASD MOVE   E INTERACT   Q O2   F COOLANT",14,392,MUTED);
        String status=s.phase()==Simulation.Phase.READY?"AWAITING CREW":s.phase().name();String clock=String.format(Locale.ROOT,"%02d:%02d",(int)s.elapsed()/60,(int)s.elapsed()%60);t(status+" / "+clock,470,392,MUTED);
    }
    private void circuit(Simulation.Snapshot s) {
        shade();panel(159,78,322,254);t("CIRCUIT REWIRE / "+s.circuitName(),172,91,WHITE);
        t("SHIP CLOCK RUNNING / ROTATE EACH TILE",172,105,AMBER);
        t(String.format(Locale.ROOT,"O2 %02.0f%% / CORE %02.0fC / HULL %02.0f%%",s.oxygen(),s.temperature(),s.hull()),172,117,s.oxygen()<30||s.temperature()>80?RED:MUTED);
        int ox=246,oy=130,tile=27;
        for(int y=0;y<5;y++)for(int x=0;x<5;x++) {
            boolean on=s.energized().contains(y*5+x);Color c=on?CYAN:STEEL;int px=ox+x*tile,py=oy+y*tile;
            boolean hover=pointerX>=px&&pointerX<px+25&&pointerY>=py&&pointerY<py+25;
            r(hover?AMBER:STEEL,px,py,25,25);r(DARK,px+1,py+1,23,23);int ports=s.circuit()[y][x],cx=px+12,cy=py+12;
            if((ports&CircuitBoard.N)!=0)r(c,cx-1,py+2,3,11);if((ports&CircuitBoard.E)!=0)r(c,cx,cy-1,12,3);
            if((ports&CircuitBoard.S)!=0)r(c,cx-1,cy,3,12);if((ports&CircuitBoard.W)!=0)r(c,px+2,cy-1,11,3);
            r(on?WHITE:EDGE,cx-2,cy-2,5,5);hits.add(new Hit("tile:"+x+":"+y,px,py,25,25));
        }
        t("SOURCE",183,184,CYAN);r(CYAN,224,195,21,3);r(s.circuitConnected()?CYAN:RED,380,195,22,3);t("SINK",410,184,s.circuitConnected()?CYAN:RED);
        t(s.circuitConnected()?"CONTINUITY CONFIRMED":"OPEN CIRCUIT / FIND A CONNECTED PATH",174,277,s.circuitConnected()?GREEN:MUTED);
        button("circuit-close","CANCEL [ESC]",172,299,126,MUTED);button("circuit-commit","ENERGIZE [ENTER]",310,299,157,s.circuitConnected()?GREEN:STEEL);
    }
    private void shade() {g.setGlobalAlpha(.86);r(VOID,0,0,WIDTH,HEIGHT);g.setGlobalAlpha(1);}
    private void pauseOverlay() {shade();panel(188,135,264,130);title("WATCH PAUSED",211,153,WHITE);t("ALL SIMULATION SYSTEMS SUSPENDED",211,182,MUTED);button("pause","RESUME [P]",211,204,218,CYAN);button("restart","RESTART MISSION",211,232,218,MUTED);}
    private void ending(Simulation.Snapshot s) {
        shade();panel(144,103,352,187);boolean win=s.phase()==Simulation.Phase.WON;
        title(win?"WELCOME HOME":"SIGNAL LOST",172,125,win?CYAN:RED);
        PixelFont.wrap(g,win?"EARTH RECEIVED YOUR TRANSMISSION. ONE SHIP. ONE SURVIVOR. YOU MADE IT.":"THE ORPHEUS HAS FALLEN SILENT. YOUR SHIP LOG WILL BE REMEMBERED.",172,157,49,MUTED);
        t("SECTORS: "+s.level()+" / 3    REPAIRS: "+s.repairs(),172,198,WHITE);t("WATCH TIME: "+(int)s.elapsed()+" SECONDS",172,215,AMBER);
        button("restart","BEGIN A NEW WATCH [ENTER]",172,247,296,CYAN);
    }
    private void helpOverlay() {
        shade();panel(138,77,364,254);title("CREW FIELD MANUAL",158,94,CYAN);
        String[] lines={"WASD / ARROWS   MOVE THROUGH THE SHIP","E               SALVAGE OR REPAIR","Q               INJECT +25% OXYGEN","F               INJECT COOLANT (-25C)","TAB             TOGGLE ROUTE ASSIST","P / ESC         PAUSE THE SIMULATION","ENTER           START / ENERGIZE CIRCUIT","", "REPAIR COSTS: O2 2 SCRAP + 1 WIRE", "HULL 3 SCRAP / REACTOR 1 SCRAP + 1 CLNT", "BUS 2 WIRE / NAV 1 SCRAP + 1 WIRE", "", "REPAIRS TAKE 4S. MOVING CANCELS THEM.","FIRE HURTS NEARBY CREW. REPAIR ITS PUMP.","RESTORE SYSTEMS, THEN E AT FLIGHT DECK.","ROTATE CIRCUIT TILES TO CONNECT BOTH ENDS."};
        for(int i=0;i<lines.length;i++)t(lines[i],158,122+i*11,i>=8?MUTED:WHITE);
        button("help","CLOSE MANUAL [H]",158,304,324,CYAN);
    }
    public WritableImage pixels() {return pixels;}
}
