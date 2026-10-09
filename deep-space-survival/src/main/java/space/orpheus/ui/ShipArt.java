package space.orpheus.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import static space.orpheus.ui.Palette.*;

/** Small, reusable pixel sprites. Each cluster is authored on the logical pixel grid. */
public final class ShipArt {
    private ShipArt() {}
    private static void r(GraphicsContext g,Color c,int x,int y,int w,int h) { g.setFill(c);g.fillRect(x,y,w,h); }
    public static void astronaut(GraphicsContext g,int x,int y,int facing,int frame) {
        r(g,DARK,x-5,y+5,11,3);r(g,STEEL,x-4,y-3,3,8);r(g,WHITE,x-3,y-8,7,2);r(g,WHITE,x-4,y-6,9,5);
        r(g,EDGE,x+3,y-6,2,5);r(g,CYAN,x-3,y-5,6,3);r(g,INDIGO,x-2,y-4,5,2);
        if(facing==1)r(g,CYAN,x+2,y-5,2,3);if(facing==3)r(g,DARK,x+1,y-5,3,3);if(facing==2)r(g,EDGE,x-3,y-5,7,3);
        r(g,WHITE,x-3,y,7,5);r(g,EDGE,x-4,y,2,5);r(g,AMBER,x-2,y+1,2,2);r(g,STEEL,x+2,y+1,2,3);
        r(g,WHITE,x-5,y,2,3);r(g,WHITE,x+5,y,2,3);r(g,STEEL,x-3,y+5,3,3+(frame%2));r(g,STEEL,x+1,y+5,3,4-(frame%2));
    }
    public static void console(GraphicsContext g,int x,int y,Color status,boolean blink) {
        r(g,DARK,x-11,y+8,24,3);r(g,STEEL,x-11,y-9,23,17);r(g,EDGE,x-11,y-9,23,2);r(g,INDIGO,x-9,y-7,19,11);
        r(g,DARK,x-8,y-6,13,8);r(g,status,x-7,y-5,7,1);r(g,status,x-7,y-3,4,1);r(g,status,x-7,y,9,1);
        r(g,AMBER,x+7,y-5,2,2);r(g,blink?status:DARK,x+7,y-1,2,2);r(g,DARK,x-8,y+5,15,1);r(g,EDGE,x-8,y+7,18,1);
    }
    public static void reactor(GraphicsContext g,int x,int y,Color status,int frame) {
        r(g,DARK,x-14,y+10,30,4);r(g,STEEL,x-13,y-11,27,22);r(g,EDGE,x-10,y-13,21,2);
        r(g,INDIGO,x-9,y-10,19,20);r(g,DARK,x-5,y-10,11,18);r(g,status,x-3,y-8,7,14);r(g,WHITE,x-1,y-6,2,10);
        for(int i=-7;i<=7;i+=4) {r(g,STEEL,x-7,y+i,15,2);r(g,EDGE,x-11,y+i,3,1);r(g,EDGE,x+10,y+i,3,1);}
        r(g,frame%2==0?status:AMBER,x-12,y+8,2,2);r(g,STEEL,x-8,y+12,4,5);r(g,STEEL,x+5,y+12,4,5);
    }
    public static void crate(GraphicsContext g,int x,int y,boolean empty) {
        r(g,DARK,x-8,y+5,18,3);r(g,STEEL,x-8,y-7,17,13);r(g,empty?INDIGO:VIOLET,x-6,y-5,13,9);
        r(g,EDGE,x-8,y-7,17,1);r(g,STEEL,x-1,y-6,3,12);r(g,STEEL,x-7,y-1,15,2);
        if(!empty) {r(g,AMBER,x-5,y-3,3,2);r(g,AMBER,x+4,y+2,2,2);}
    }
    public static void oxygen(GraphicsContext g,int x,int y,Color status) {
        r(g,STEEL,x-12,y-10,25,21);r(g,EDGE,x-11,y-10,23,1);
        for(int i=-8;i<=6;i+=7) {r(g,EDGE,x+i,y-7,5,15);r(g,INDIGO,x+i+2,y-6,2,12);r(g,status,x+i,y+3,5,2);r(g,STEEL,x+i+1,y-10,3,3);}
        r(g,STEEL,x-8,y+11,3,5);r(g,STEEL,x+7,y+11,3,5);
    }
    public static void fire(GraphicsContext g,int x,int y,int frame) {
        r(g,RED,x-6,y-1,12,7);r(g,RED,x-4,y-6-frame%3,3,10);r(g,RED,x+2,y-9+(frame%3),3,12);
        r(g,AMBER,x-3,y-2,7,7);r(g,AMBER,x,y-6,3,10);r(g,WHITE,x,y+1,2,3);
    }
    public static void icon(GraphicsContext g,String kind,int x,int y,Color color) {
        switch(kind) {
            case "O2"->{r(g,color,x+2,y,5,2);r(g,color,x+1,y+2,7,8);r(g,PANEL,x+3,y+3,3,4);r(g,color,x+2,y+10,5,1);}
            case "REACTOR"->{r(g,color,x+4,y,2,11);r(g,color,x,y+4,10,2);r(g,color,x+1,y+1,2,2);r(g,color,x+7,y+1,2,2);r(g,color,x+1,y+8,2,2);r(g,color,x+7,y+8,2,2);}
            case "HULL"->{r(g,color,x,y,10,8);r(g,color,x+2,y+8,6,2);r(g,color,x+4,y+10,2,1);r(g,PANEL,x+2,y+2,6,5);}
            case "POWER"->{r(g,color,x+5,y,3,3);r(g,color,x+3,y+3,4,3);r(g,color,x+1,y+5,7,2);r(g,color,x+3,y+7,3,3);r(g,color,x+2,y+10,2,1);}
            case "SCRAP"->{r(g,color,x,y+2,9,7);r(g,PANEL,x+2,y+4,5,3);r(g,color,x+2,y,5,2);r(g,color,x+2,y+9,5,2);}
            case "WIRE"->{r(g,color,x,y,3,8);r(g,color,x+2,y+6,6,2);r(g,color,x+6,y+2,3,6);r(g,color,x+8,y+2,2,2);}
            case "COOLANT"->{r(g,color,x+3,y,4,3);r(g,color,x+1,y+3,8,8);r(g,PANEL,x+3,y+5,4,3);}
        }
    }
}
