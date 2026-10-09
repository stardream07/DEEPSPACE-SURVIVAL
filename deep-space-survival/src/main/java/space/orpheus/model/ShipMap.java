package space.orpheus.model;

import java.util.*;

/** One shared topology for collisions, room labels, rendering, and route finding. */
public final class ShipMap {
    public static final int WIDTH = 32, HEIGHT = 18;
    public record Point(int x, int y) {}
    public record Room(String name, int x, int y, int width, int height) {
        public boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }
    public static final List<Room> ROOMS = List.of(
        new Room("FLIGHT DECK", 11, 1, 10, 4),
        new Room("LIFE SUPPORT", 2, 6, 9, 5),
        new Room("REACTOR", 22, 6, 8, 5),
        new Room("CARGO", 2, 12, 9, 5),
        new Room("HULL CONTROL", 12, 12, 9, 5),
        new Room("ENGINEERING", 22, 12, 8, 5));
    private final boolean[][] floor = new boolean[HEIGHT][WIDTH];
    public ShipMap() {
        for (Room r : ROOMS) fill(r.x, r.y, r.width, r.height);
        fill(14, 5, 4, 8);
        fill(10, 8, 13, 2);
        fill(10, 14, 13, 2);
    }
    private void fill(int x, int y, int w, int h) {
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) floor[j][i] = true;
    }
    public boolean walkable(double x, double y) {
        int tx = (int)Math.floor(x), ty = (int)Math.floor(y);
        return tx >= 0 && tx < WIDTH && ty >= 0 && ty < HEIGHT && floor[ty][tx];
    }
    public String roomAt(double x, double y) {
        return ROOMS.stream().filter(r -> r.contains(x, y)).map(Room::name).findFirst().orElse("ACCESS CORRIDOR");
    }
    public List<Point> route(Point start, Point end) {
        ArrayDeque<Point> queue = new ArrayDeque<>(); Map<Point, Point> parent = new HashMap<>();
        queue.add(start); parent.put(start, null);
        while (!queue.isEmpty()) {
            Point p = queue.remove(); if (p.equals(end)) break;
            for (Point q : List.of(new Point(p.x+1,p.y), new Point(p.x-1,p.y),new Point(p.x,p.y+1),new Point(p.x,p.y-1)))
                if (walkable(q.x,q.y) && !parent.containsKey(q)) { parent.put(q,p); queue.add(q); }
        }
        if (!parent.containsKey(end)) return List.of();
        List<Point> result = new ArrayList<>();
        for (Point p = end; p != null; p = parent.get(p)) result.add(p);
        Collections.reverse(result); return List.copyOf(result);
    }
}
