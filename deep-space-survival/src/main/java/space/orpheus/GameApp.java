package space.orpheus;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.image.WritableImage;
import space.orpheus.model.Simulation;
import space.orpheus.model.Leaderboard;
import java.time.Instant;
import java.io.IOException;
import space.orpheus.ui.GameRenderer;
import java.util.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public final class GameApp extends Application {
    private Simulation simulation;
    private GameRenderer renderer;
    private Canvas canvas;
    private final Set<KeyCode> pressed=EnumSet.noneOf(KeyCode.class);
    private boolean manualPaused;
    private AnimationTimer animation;
    private final Leaderboard leaderboard=new Leaderboard(Path.of("data","leaderboard.properties"));
    private boolean resultSaved,smoke;

    @Override public void start(Stage stage) {
        smoke=getParameters().getRaw().contains("--smoke-test");
        simulation=new Simulation(42,!smoke);renderer=new GameRenderer(simulation.map);canvas=new Canvas(1280,800);
        StackPane root=new StackPane(canvas);root.setStyle("-fx-background-color: #080d19;");
        Scene scene=new Scene(root,1280,800);canvas.widthProperty().bind(root.widthProperty());canvas.heightProperty().bind(root.heightProperty());
        if(!smoke)try { renderer.scores=leaderboard.load(); }
        catch(IOException ex) { renderer.scoreNotice="SCORES UNAVAILABLE"; }
        scene.setOnKeyTyped(e->{
            if(simulation.snapshot().phase()==Simulation.Phase.READY&&!renderer.showScores
                    &&!e.isControlDown()&&!e.isAltDown()&&!e.isMetaDown()) {
                String typed=e.getCharacter().toUpperCase(Locale.ROOT);
                if(typed.matches("[A-Z0-9 _-]+")&&renderer.crew.length()+typed.length()<=16) {
                    renderer.crew+=typed;renderer.entryNotice="";
                } else if(typed.codePoints().noneMatch(Character::isISOControl)) {
                    renderer.entryNotice="USE 1-16 LETTERS, DIGITS, SPACES, - OR _";
                }
                e.consume();
            }
        });
        scene.setOnKeyPressed(e->{boolean fresh=pressed.add(e.getCode());if(fresh)key(e.getCode());updateMovement();e.consume();});
        scene.setOnKeyReleased(e->{pressed.remove(e.getCode());updateMovement();e.consume();});
        canvas.setOnMouseClicked(e->{double x=renderer.logicalX(canvas,e.getX()),y=renderer.logicalY(canvas,e.getY());
            List<GameRenderer.Hit> hits=renderer.hits();
            for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).contains(x,y)){action(hits.get(i).action());break;}
            canvas.requestFocus();
        });
        canvas.setOnMouseMoved(e->{renderer.pointerX=renderer.logicalX(canvas,e.getX());renderer.pointerY=renderer.logicalY(canvas,e.getY());});
        canvas.setOnMouseExited(e->{renderer.pointerX=-1;renderer.pointerY=-1;});
        stage.focusedProperty().addListener((o,old,focused)->{if(!focused){pressed.clear();simulation.movement(0,0);if(simulation.snapshot().phase()==Simulation.Phase.RUNNING)simulation.pause();}});
        stage.setTitle("ORPHEUS — Deep-Space Survival");stage.setScene(scene);stage.setMinWidth(660);stage.setMinHeight(440);
        stage.setOnCloseRequest(e->stop());stage.show();canvas.requestFocus();
        if(smoke&&getParameters().getRaw().contains("--minimum-size")) {
            stage.setWidth(stage.getMinWidth());stage.setHeight(stage.getMinHeight());
        }
        long origin=System.nanoTime();animation=new AnimationTimer(){public void handle(long now){var state=simulation.snapshot();saveResult(state);renderer.render(canvas,state,(now-origin)/1_000_000_000.0);}};animation.start();
        if(getParameters().getRaw().contains("--snapshot")) {
            PauseTransition capture=new PauseTransition(Duration.seconds(1));capture.setOnFinished(e->{try{capture("ready.png");stop();Platform.exit();}catch(Exception ex){ex.printStackTrace();Platform.exit();}});capture.play();
        }
        if(smoke) {
            PauseTransition check=new PauseTransition(Duration.seconds(1));check.setOnFinished(e->{try{
                fire(scene,KeyEvent.KEY_PRESSED,KeyCode.ENTER);fire(scene,KeyEvent.KEY_RELEASED,KeyCode.ENTER);
                require(simulation.snapshot().phase()==Simulation.Phase.READY,"blank name blocks start");
                fire(scene,KeyEvent.KEY_PRESSED,KeyCode.W);simulation.tick(1);fire(scene,KeyEvent.KEY_RELEASED,KeyCode.W);
                require(simulation.snapshot().elapsed()==0,"crew entry freezes the mission");
                javafx.event.Event.fireEvent(scene,new KeyEvent(KeyEvent.KEY_TYPED,"crew 7","",KeyCode.UNDEFINED,false,false,false,false));
                require(renderer.crew.equals("CREW 7"),"crew name entered via keyboard");
                fire(scene,KeyEvent.KEY_PRESSED,KeyCode.ENTER);fire(scene,KeyEvent.KEY_RELEASED,KeyCode.ENTER);
                require(simulation.snapshot().phase()==Simulation.Phase.RUNNING,"Enter starts mission");
                double before=simulation.snapshot().x();fire(scene,KeyEvent.KEY_PRESSED,KeyCode.D);simulation.tick(.3);fire(scene,KeyEvent.KEY_RELEASED,KeyCode.D);
                require(simulation.snapshot().x()>before,"D moves astronaut through scene input");
                fire(scene,KeyEvent.KEY_PRESSED,KeyCode.P);fire(scene,KeyEvent.KEY_RELEASED,KeyCode.P);double clock=simulation.snapshot().elapsed();simulation.tick(2);
                require(simulation.snapshot().elapsed()==clock,"P freezes simulation");
                fire(scene,KeyEvent.KEY_PRESSED,KeyCode.P);fire(scene,KeyEvent.KEY_RELEASED,KeyCode.P);
                simulation.triggerEmergency(Simulation.Emergency.FIRE);simulation.triggerEmergency(Simulation.Emergency.POWER_SURGE);simulation.triggerEmergency(Simulation.Emergency.HULL_BREACH);
                renderer.render(canvas,simulation.snapshot(),2);capture("emergencies.png");
                fire(scene,KeyEvent.KEY_PRESSED,KeyCode.H);fire(scene,KeyEvent.KEY_RELEASED,KeyCode.H);require(renderer.help,"H opens field manual");require(simulation.snapshot().phase()==Simulation.Phase.PAUSED,"manual pauses simulation");
                fire(scene,KeyEvent.KEY_PRESSED,KeyCode.H);fire(scene,KeyEvent.KEY_RELEASED,KeyCode.H);require(!renderer.help,"H closes field manual");require(simulation.snapshot().phase()==Simulation.Phase.RUNNING,"manual restores watch");
                simulation.reset();renderer.render(canvas,simulation.snapshot(),0);capture("ready.png");
                click(256,235);require(simulation.snapshot().phase()==Simulation.Phase.RUNNING,"mouse begins watch");
                renderer.render(canvas,simulation.snapshot(),0);click(560,21);require(simulation.snapshot().phase()==Simulation.Phase.PAUSED,"mouse pauses watch");
                renderer.render(canvas,simulation.snapshot(),0);click(320,213);require(simulation.snapshot().phase()==Simulation.Phase.RUNNING,"mouse resumes watch");
                simulation.triggerEmergency(Simulation.Emergency.POWER_SURGE);walkTo(26.5,14.5);simulation.interact();require(simulation.snapshot().circuit()!=null,"power terminal opens circuit");
                renderer.render(canvas,simulation.snapshot(),1);int ports=simulation.snapshot().circuit()[0][0];click(258,142);require(simulation.snapshot().circuit()[0][0]!=ports,"mouse rotates circuit tile");
                renderer.render(canvas,simulation.snapshot(),1);capture("circuit.png");
                int[][] solution={{0,2,10},{1,2,9},{1,1,6},{2,1,10},{3,1,12},{3,2,3},{4,2,10}};
                for(int[] p:solution)for(int i=0;i<4&&simulation.snapshot().circuit()[p[1]][p[0]]!=p[2];i++)click(258+p[0]*27,142+p[1]*27);
                renderer.render(canvas,simulation.snapshot(),1);capture("circuit-connected.png");click(390,309);
                require(simulation.snapshot().circuit()==null,"mouse energizes connected circuit");
                require(simulation.snapshot().systems().stream().filter(v->v.kind()==space.orpheus.model.Subsystem.Kind.POWER).findFirst().orElseThrow().online(),"UI circuit repair restores actual power bus");
                System.out.println("PASS JavaFX keyboard, mouse, movement, pause, emergency rendering, manual, circuit rotation and energize, restart, screenshots");
                stop();Platform.exit();
            }catch(Throwable ex){ex.printStackTrace();stop();Platform.exit();System.exit(1);}});check.play();
        }
    }
    private static void fire(Scene scene,javafx.event.EventType<KeyEvent> type,KeyCode code) {javafx.event.Event.fireEvent(scene,new KeyEvent(type,"","",code,false,false,false,false));}
    private void click(double logicalX,double logicalY) {
        double scale=GameRenderer.scale(canvas),x=(canvas.getWidth()-GameRenderer.WIDTH*scale)/2+logicalX*scale,y=(canvas.getHeight()-GameRenderer.HEIGHT*scale)/2+logicalY*scale;
        canvas.getOnMouseClicked().handle(new MouseEvent(MouseEvent.MOUSE_CLICKED,x,y,x,y,MouseButton.PRIMARY,1,false,false,false,false,true,false,false,false,false,false,null));
    }
    private void walkTo(double x,double y) {
        var state=simulation.snapshot();var path=simulation.map.route(new space.orpheus.model.ShipMap.Point((int)state.x(),(int)state.y()),new space.orpheus.model.ShipMap.Point((int)x,(int)y));
        for(var point:path)for(int i=0;i<80;i++){state=simulation.snapshot();double dx=point.x()+.5-state.x(),dy=point.y()+.5-state.y(),dist=Math.hypot(dx,dy);if(dist<.01)break;simulation.movement(dx,dy);simulation.tick(Math.min(.04,dist/3.1));}
        simulation.movement(0,0);
    }
    private static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    private void key(KeyCode code) {
        Simulation.Snapshot s=simulation.snapshot();
        if(renderer.showScores) {if(code==KeyCode.ESCAPE)renderer.showScores=false;return;}
        if(s.phase()==Simulation.Phase.READY) {
            if(code==KeyCode.ENTER)beginWatch();
            if(code==KeyCode.BACK_SPACE&&!renderer.crew.isEmpty()) {
                renderer.crew=renderer.crew.substring(0,renderer.crew.length()-1);renderer.entryNotice="";
            }
            return;
        }
        if(renderer.help) {if(code==KeyCode.H||code==KeyCode.ESCAPE)toggleHelp();return;}
        if(code==KeyCode.H) {toggleHelp();return;}
        if(s.phase()==Simulation.Phase.WON||s.phase()==Simulation.Phase.LOST) {if(code==KeyCode.ENTER)restart();return;}
        if(s.circuit()!=null) {
            if(code==KeyCode.ESCAPE){simulation.closeCircuit();pressed.clear();}
            else if(code==KeyCode.ENTER)simulation.commitCircuit();
            else if(code==KeyCode.P)simulation.pause();return;
        }
        switch(code) {
            case E->simulation.interact();case Q->simulation.useOxygen();case F->simulation.useCoolant();
            case P,ESCAPE->simulation.pause();case TAB->renderer.route=!renderer.route;default->{}
        }
    }
    private void action(String action) {
        Simulation.Snapshot s=simulation.snapshot();
        if(renderer.showScores) {if(action.equals("score-close"))renderer.showScores=false;return;}
        if(s.phase()==Simulation.Phase.READY) {
            if(action.equals("start"))beginWatch();
            if(action.equals("scores"))renderer.showScores=true;
            return;
        }
        if(action.equals("scores")&&(s.phase()==Simulation.Phase.WON||s.phase()==Simulation.Phase.LOST)) {
            renderer.showScores=true;return;
        }
        if(renderer.help&&!action.equals("help"))return;
        if(s.phase()==Simulation.Phase.PAUSED&&!Set.of("help","pause","restart").contains(action))return;
        if((s.phase()==Simulation.Phase.WON||s.phase()==Simulation.Phase.LOST)&&!action.equals("restart"))return;
        if(s.circuit()!=null&&s.phase()!=Simulation.Phase.PAUSED&&!action.startsWith("tile:")&&!action.startsWith("circuit-"))return;
        if(action.startsWith("tile:")){String[] p=action.split(":");simulation.rotateCircuit(Integer.parseInt(p[1]),Integer.parseInt(p[2]));return;}
        switch(action) {
            case "pause"->{pressed.clear();simulation.pause();}case "restart"->restart();case "help"->toggleHelp();
            case "route"->renderer.route=!renderer.route;case "jump"->simulation.jump();case "oxygen"->simulation.useOxygen();case "coolant"->simulation.useCoolant();
            case "circuit-close"->simulation.closeCircuit();case "circuit-commit"->simulation.commitCircuit();default->{}
        }
    }
    private void toggleHelp() {
        renderer.help=!renderer.help;pressed.clear();simulation.movement(0,0);
        if(renderer.help){manualPaused=simulation.snapshot().phase()==Simulation.Phase.RUNNING;if(manualPaused)simulation.pause();}
        else if(manualPaused&&simulation.snapshot().phase()==Simulation.Phase.PAUSED){simulation.pause();manualPaused=false;}
    }
    private void beginWatch() {
        String name=renderer.crew.strip();
        if(!Leaderboard.validName(name)) {renderer.entryNotice="ENTER A CREW NAME";return;}
        renderer.crew=name;renderer.entryNotice="";pressed.clear();simulation.start();
    }
    private void saveResult(Simulation.Snapshot state) {
        if(smoke||resultSaved||(state.phase()!=Simulation.Phase.WON&&state.phase()!=Simulation.Phase.LOST))return;
        resultSaved=true;
        try {
            renderer.scores=leaderboard.save(new Leaderboard.Entry(renderer.crew,state.score().total(),
                state.phase(),state.elapsed(),Instant.now()));
            renderer.scoreNotice="";
        } catch(IOException ex) {renderer.scoreNotice="COULD NOT SAVE SCORE";}
    }
    private void restart() {saveResult(simulation.snapshot());resultSaved=false;renderer.entryNotice="";pressed.clear();renderer.help=false;renderer.route=false;manualPaused=false;simulation.reset();}
    private void updateMovement() {
        if(renderer.help||simulation.snapshot().phase()!=Simulation.Phase.RUNNING){simulation.movement(0,0);return;}
        double dx=(pressed.contains(KeyCode.D)||pressed.contains(KeyCode.RIGHT)?1:0)-(pressed.contains(KeyCode.A)||pressed.contains(KeyCode.LEFT)?1:0);
        double dy=(pressed.contains(KeyCode.S)||pressed.contains(KeyCode.DOWN)?1:0)-(pressed.contains(KeyCode.W)||pressed.contains(KeyCode.UP)?1:0);
        simulation.movement(dx,dy);
    }
    private void capture(String name) throws Exception {
        Path folder=Path.of("artifacts");Files.createDirectories(folder);WritableImage image=canvas.snapshot(null,null);
        BufferedImage out=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<out.getHeight();y++)for(int x=0;x<out.getWidth();x++)out.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        ImageIO.write(out,"png",folder.resolve(name).toFile());
    }
    @Override public void stop() {if(animation!=null)animation.stop();if(simulation!=null){simulation.close();saveResult(simulation.snapshot());}}
    public static void main(String[] args) {launch(args);}
}
