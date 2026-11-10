package space.orpheus.model;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class Leaderboard {
    public record Entry(String name,int score,Simulation.Phase outcome,double seconds,Instant completed) {
        public Entry {
            if(!validName(name)||score<0||!Double.isFinite(seconds)||seconds<0||completed==null
                    ||(outcome!=Simulation.Phase.WON&&outcome!=Simulation.Phase.LOST))
                throw new IllegalArgumentException("Invalid mission result");
        }
    }

    private static final int LIMIT=10;
    private static final Comparator<Entry> RANK=Comparator.comparingInt(Entry::score).reversed()
        .thenComparingDouble(Entry::seconds).thenComparing(Entry::completed);
    private final Path file;

    public Leaderboard(Path file) { this.file=file.toAbsolutePath(); }

    public static boolean validName(String name) { return name.matches("[A-Z0-9 _-]{1,16}")&&!name.isBlank(); }

    public List<Entry> load() throws IOException {
        List<Entry> entries=new ArrayList<>();
        List<String> lines;
        try { lines=Files.readAllLines(file); }
        catch(NoSuchFileException ex) { return List.of(); }
        for(String line:lines) {
            Properties properties=new Properties();
            try {
                properties.load(new java.io.StringReader(line));
                for(String value:properties.stringPropertyNames()) {
                    String[] fields=properties.getProperty(value).split("[|]",-1);
                    if(fields.length==5)entries.add(new Entry(fields[0],Integer.parseInt(fields[1]),
                        Simulation.Phase.valueOf(fields[2]),Double.parseDouble(fields[3]),Instant.parse(fields[4])));
                }
            } catch(IllegalArgumentException|java.time.DateTimeException ex) {
                // Skip a malformed row without hiding other results.
            }
        }
        return ranked(entries);
    }

    public List<Entry> save(Entry entry) throws IOException {
        List<Entry> entries=new ArrayList<>(load());
        entries.add(entry);
        List<Entry> ranked=ranked(entries);
        List<String> lines=new ArrayList<>();
        for(Entry row:ranked)lines.add("run."+lines.size()+"="+row.name()+"|"+row.score()+"|"+row.outcome()
            +"|"+row.seconds()+"|"+row.completed());
        Files.createDirectories(file.getParent());
        Path temporary=Files.createTempFile(file.getParent(),"leaderboard-",".tmp");
        try {
            Files.write(temporary,lines);
            try { Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ex) { Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        return ranked;
    }

    private static List<Entry> ranked(List<Entry> entries) {
        return entries.stream().sorted(RANK).limit(LIMIT).toList();
    }
}
