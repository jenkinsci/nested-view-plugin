package hudson.plugins.nested_view.search;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.plugins.nested_view.NestedViewGlobalConfig;
import hudson.plugins.nested_view.NestedViewsSearch;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class HistoryItem {

    private static List<HistoryItem> history;

    private static void save() {
        NestedViewGlobalConfig.getInstance().setHistoryContent(saveToString());
        NestedViewGlobalConfig.getInstance().save();
    }

    private final String query;
    private final int size;
    private final Date date;

    @SuppressFBWarnings(value = {"EI_EXPOSE_REP2"}, justification = "date is not cared")
    public HistoryItem(String query, int size, Date date) {
        this.query = query;
        this.size = size;
        this.date = date;
    }

    private static void saveToFile(File f) {
        try {
            Files.write(f.toPath(), saveToString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            NestedViewsSearch.LOGGER.log(Level.SEVERE, "saving nested view search history failed", ex);
        }
    }

    private static String saveToString() {
        return history.stream().map(a -> a.toSave()).collect(Collectors.joining("\n"));
    }

    private static List<HistoryItem> loadFromFile(File f) {
        List<String> l = new ArrayList<>(0);
        if (f.exists()) {
            try {
                l = Files.readAllLines(f.toPath());
            } catch (Exception ex) {
                NestedViewsSearch.LOGGER.log(Level.SEVERE, "loading nested view search history failed", ex);
            }
        }
        return loadFromStrings(l);
    }

    private static List<HistoryItem> loadFromString(String l) {
        return loadFromStrings(Arrays.asList(l.split("\n")));
    }

    private static void load() {
        history = loadFromString(NestedViewGlobalConfig.getInstance().getHistoryContent());
    }

    private static List<HistoryItem> loadFromStrings(List<String> l) {
        ArrayList<HistoryItem> r = new ArrayList(NestedViewGlobalConfig.getInstance().getNestedViewHistoryCount());
        for (String s : l) {
            try {
                if (s == null || s.trim().isEmpty()) {
                    continue;
                }
                r.add(new HistoryItem(s));
            } catch (Exception ex) {
                NestedViewsSearch.LOGGER.log(Level.SEVERE, "reading nested view search history " + s + " failed", ex);
            }
        }
        return Collections.synchronizedList(r);
    }

    public static List<HistoryItem> get() {
        load();
        return Collections.unmodifiableList(history);
    }

    public static void add(HistoryItem his) {
        load();
        history.removeAll(Collections.singleton(his));
        history.add(0, his);
        shrink();
        save();
    }

    private static void shrink() {
        shrink(NestedViewGlobalConfig.getInstance().getNestedViewHistoryCount());
    }

    private static void shrink(final int to) {
        int tot = to < 0 ? 0 : to;
        while (history.size() > tot) {
            history.remove(history.size() - 1);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistoryItem that = (HistoryItem) o;
        return query.equals(that.query);
    }

    @Override
    public int hashCode() {
        return query.hashCode();
    }

    public String getQuery() {
        return query;
    }

    public String getUrl() {
        return BuildDetails.getJenkinsUrl() + "/search/?q=" + getQuery().replaceAll("#","%23");
    }

    public int getSize() {
        return size;
    }

    public String getDate() {
        return new SimpleDateFormat("HH:mm:ss dd/MM").format(date);
    }

    public String toSave() {
        return size + " " + date.getTime() + " " + query;
    }

    public HistoryItem(String saved) {
        String[] sd = saved.split(" ");
        size = Integer.parseInt(sd[0]);
        date = new Date(Long.parseLong(sd[1]));
        query = saved.replace(size + " " + date.getTime() + " ", "");

    }

}
