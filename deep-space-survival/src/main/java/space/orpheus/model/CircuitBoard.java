package space.orpheus.model;

import java.util.*;

/** Reciprocal connections are traversed with BFS; DFS is provided for topology inspection. */
public final class CircuitBoard {
    public static final int SIZE=5, N=1, E=2, S=4, W=8;
    private final int[][] ports = new int[SIZE][SIZE];
    private static final int[] DX={0,1,0,-1}, DY={-1,0,1,0}, BIT={N,E,S,W}, OPP={S,W,N,E};
    public CircuitBoard(long seed) {
        Random random=new Random(seed);
        for(int y=0;y<SIZE;y++) for(int x=0;x<SIZE;x++) ports[y][x]=random.nextBoolean()?N|S:N|E;
        // Guaranteed source-to-sink route: across the middle, with a top detour.
        ports[2][0]=E|W; ports[2][1]=N|W; ports[1][1]=S|E;
        ports[1][2]=E|W; ports[1][3]=S|W; ports[2][3]=N|E; ports[2][4]=E|W;
        for(int y=0;y<SIZE;y++) for(int x=0;x<SIZE;x++) {
            int turns=random.nextInt(4); for(int i=0;i<turns;i++) rotate(x,y);
        }
        if (connected()) rotate(0,2);
    }
    public int ports(int x,int y) { return ports[y][x]; }
    public void rotate(int x,int y) { if(x>=0&&x<SIZE&&y>=0&&y<SIZE) ports[y][x]=((ports[y][x]<<1)&15)|((ports[y][x]>>3)&1); }
    public Set<Integer> energized() {
        Set<Integer> visited=new HashSet<>(); ArrayDeque<Integer> queue=new ArrayDeque<>();
        if((ports[2][0]&W)==0) return Set.of();
        queue.add(2*SIZE); visited.add(2*SIZE);
        while(!queue.isEmpty()) {
            int id=queue.remove(),x=id%SIZE,y=id/SIZE;
            for(int d=0;d<4;d++) {
                int nx=x+DX[d],ny=y+DY[d],next=ny*SIZE+nx;
                if(nx>=0&&nx<SIZE&&ny>=0&&ny<SIZE&&(ports[y][x]&BIT[d])!=0&&(ports[ny][nx]&OPP[d])!=0&&visited.add(next)) queue.add(next);
            }
        }
        return Set.copyOf(visited);
    }
    public boolean connected() { return energized().contains(2*SIZE+4)&&(ports[2][4]&E)!=0; }
    public Set<Integer> inspectDfs(int x,int y) { Set<Integer> visited=new HashSet<>(); dfs(x,y,visited); return Set.copyOf(visited); }
    private void dfs(int x,int y,Set<Integer> visited) {
        if(!visited.add(y*SIZE+x)) return;
        for(int d=0;d<4;d++) { int nx=x+DX[d],ny=y+DY[d];
            if(nx>=0&&nx<SIZE&&ny>=0&&ny<SIZE&&(ports[y][x]&BIT[d])!=0&&(ports[ny][nx]&OPP[d])!=0) dfs(nx,ny,visited);
        }
    }
    public int[][] copyPorts() { int[][] copy=new int[SIZE][]; for(int y=0;y<SIZE;y++) copy[y]=ports[y].clone(); return copy; }
}
