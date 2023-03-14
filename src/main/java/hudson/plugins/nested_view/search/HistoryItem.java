package hudson.plugins.nested_view.search;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.plugins.nested_view.NestedViewsSearch;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class HistoryItem {

    private static final int MAX_HISTORY = 500;
    private static final File historyCache = new File(Jenkins.get().root, ".nested-view-serch.cache");
    private static final List<HistoryItem> history = HistoryItem.load();

    private final String query;
    private final int size;
    private final Date date;

    @SuppressFBWarnings(value = {"EI_EXPOSE_REP2"}, justification = "date is not cared")
    public HistoryItem(String query, int size, Date date) {
        this.query = query;
        this.size = size;
        this.date = date;
    }

    public static void save() {
        try {
            saveImp();
        } catch (Exception ex) {
            NestedViewsSearch.LOGGER.log(Level.SEVERE, "saving nested view search history failed", ex);
        }
    }

    public static void saveImp() throws IOException {
        Files.write(historyCache.toPath(), history.stream().map(a -> a.toSave()).collect(Collectors.toList()));
    }

    public static List<HistoryItem> load() {
        List<String> l = new ArrayList<>(0);
        if (historyCache.exists()) try {
            l = Files.readAllLines(historyCache.toPath());
        } catch (Exception ex) {
            NestedViewsSearch.LOGGER.log(Level.SEVERE, "loading nested view search history failed", ex);
        }
        ArrayList<HistoryItem> r = new ArrayList(MAX_HISTORY);
        for (String s : l) {
            try {
                r.add(new HistoryItem(s));
            } catch (Exception ex) {
                NestedViewsSearch.LOGGER.log(Level.SEVERE, "reading nested view search history " + s + " failed", ex);
            }
        }
        return Collections.synchronizedList(r);
    }

    public static List<HistoryItem> get() {
        return Collections.unmodifiableList(history);
    }

    public static void add(HistoryItem his) {
        history.removeAll(Collections.singleton(his));
        history.add(0, his);
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
        save();
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
        return BuildDetails.getJenkinsUrl() + "/search/?q=" + getQuery();
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
