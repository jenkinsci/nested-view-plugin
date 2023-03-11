package hudson.plugins.nested_view;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.AbstractProject;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.search.Search;
import hudson.search.SearchIndex;
import hudson.search.SearchItem;
import hudson.search.SearchResult;
import hudson.search.SuggestedItem;
import jenkins.model.Jenkins;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NestedViewsSearch extends Search {

    static class Query {

        private static final int MIN_LENGTH = 2;

        private final String original;
        private final String withoutArguments;

        private boolean multiline;
        private boolean searchByNvr;
        private boolean finalFilter;
        private boolean projectInfo;
        private int stats = -1;
        private int builds = -1;
        private int last = -1;

        private String where = "vnj"; //v,n,j
        private String how = "c"; //c,s,e,r,R,q,Q
        private String bool = ""; //a,o,""
        private String part = "f"; //p,f
        private boolean invert = false;

        public Query(boolean search, String ooriginal) {
            if (search) {
                NestedViewsSearchFactory.resetTmpSkip();
            }
            this.original = ooriginal.trim();
            String query = null;
            try {
                Pattern getQuery = Pattern.compile("-.*:");
                Matcher m = getQuery.matcher(original);
                m.find();
                query = m.group();
            } catch (Exception ex) {
                //no query found is ok
            }
            if (query != null) {
                withoutArguments = original.replace(query, "").trim();
                if (withoutArguments.contains(".*")) {
                    how = "r";
                }
                if (query.contains("X") && search) {
                    String l = query.replaceAll(".*X", "");
                    int n = 1;
                    if (l.length() > 0) {
                        try {
                            l = l.substring(0, 1);
                            n = Integer.parseInt(l);
                        } catch (Exception ex) {
                            //ok
                        }
                    }
                    NestedViewsSearchFactory.setTmpSkip(n);
                }
                if ((query.contains("D") || query.contains("d")) && search) {
                    if (query.contains("D")) {
                        finalFilter = true;
                    }
                    searchByNvr = true;
                    bool = "o";
                    if (builds <= 0) { //maybe it was already set
                        builds = 10;
                    }
                }
                if (query.contains("m") && search) {
                    multiline = true;
                }
                if (query.contains("P") && search) {
                    projectInfo = true;
                }
                if (query.contains("S") && search) {
                    stats = getNumber(query, "S", 10);
                }
                if (query.contains("B") && search) {
                    builds = getNumber(query, "B", 10);
                }
                if (query.contains("L") && search) {
                    last = getNumber(query, "L", 0);
                    if (last == 0) { //it can be set also by user
                        last = 1234567;
                    }
                } else {
                    last = -1;
                }
                if (query.contains("j") || query.contains("v") || query.contains("n") || query.contains("w")) {
                    where = "";
                }
                if (query.contains("j")) {
                    where += "j";
                }
                if (query.contains("v")) {
                    where += "v";
                }
                if (query.contains("n")) {
                    where += "n";
                }
                if (query.contains("w")) {
                    where += "vn";
                }
                if (query.contains("c")) {
                    how = "c";
                }
                if (query.contains("e")) {
                    how = "e";
                }
                if (query.contains("s")) {
                    how = "s";
                }
                if (query.contains("r")) {
                    how = "r";
                }
                if (query.contains("R")) {
                    how = "R";
                }
                if (query.contains("q")) {
                    how = "q";
                }
                if (query.contains("Q")) {
                    how = "Q";
                }
                if (query.contains("a")) {
                    bool = "a";
                }
                if (query.contains("o")) {
                    bool = "o";
                }
                if (query.contains("f")) {
                    part = "f";
                }
                if (query.contains("p")) {
                    part = "p";
                }
                if (query.contains("!")) {
                    invert = true;
                }
            } else {
                withoutArguments = original;
                if (withoutArguments.contains(".*")) {
                    how = "r";
                }
            }
        }

        public String getWithoutArguments() {
            return withoutArguments;
        }

        public String[] getWithoutArgumentsSplit() {
            return withoutArguments.split("\\s+");
        }


        private int getNumber(String query, String switcher, int n) {
            String l = query.replaceAll(".*" + switcher, "");
            l = l.replaceAll("[^0-9].*", "");
            try {
                n = Integer.parseInt(l);
            } catch (Exception ex) {
                //ok
            }
            return n;
        }

        public boolean isMultiline() {
            return multiline;
        }

        public boolean isProjectInfo() {
            return projectInfo;
        }

        public int getStats() {
            return stats;
        }

        public int getBuilds() {
            return builds;
        }

        public int getLast() {
            return last;
        }

        public String getHow() {
            return how;
        }

        public boolean isSearchByNvr() {
            return searchByNvr;
        }

        public boolean isFinalFilter() {
            return finalFilter;
        }

        public boolean isInvert() {
            return invert;
        }

        public boolean isNonTrivial(boolean suggesting) {
            final String loriginal;
            if (original == null) {
                loriginal = "";
            } else {
                loriginal = original.trim();
            }
            final String lwithout;
            if (withoutArguments == null) {
                lwithout = "";
            } else {
                lwithout = withoutArguments.trim();
            }
            return !loriginal.equals(".*")
                    && loriginal.length() >= MIN_LENGTH
                    && !lwithout.equals(".*")
                    && lwithout.length() >= MIN_LENGTH;
        }
    }

    static class NamableWithClass {
        private final String name;
        private final String fullPath;
        private Object item;

        private NamableWithClass(Object item, String name, String fullPath) {
            this.item = item;
            this.name = name;
            this.fullPath = fullPath;
        }

        public String getName() {
            return name;
        }

        public String getFullPath() {
            return fullPath;
        }

        public String getUsefulName() {
            if (item instanceof AbstractProject) {
                return name;
            } else {
                if (item instanceof NestedView) {
                    return fullPath + "/";
                } else {
                    return fullPath;
                }
            }
        }

        public String getUrl() {
            String rootUrl = Jenkins.get().getRootUrl();
            if (rootUrl.endsWith("/")) {
                rootUrl = rootUrl.substring(0, rootUrl.length() - 1);
            }
            if (item instanceof AbstractProject) {
                return rootUrl + "/job/" + name;
            } else {
                return rootUrl + getFullPath().replace("/", "/view/");
            }
        }

        public boolean matches(Query query) {
            if (query.isInvert()) {
                return !matchesImpl(query);
            } else {
                return matchesImpl(query);
            }
        }

        private boolean matchesImpl(Query query) {
            String nameOrPath = getFullPath();
            if (query.part.equals("p")) {
                nameOrPath = getName();
            }
            boolean clazzPass = false;
            if (query.where.contains("j") && (item instanceof AbstractProject)) {
                clazzPass = true;
            }
            if (query.where.contains("w") && (item instanceof View || item instanceof NestedView)) {
                clazzPass = true;
            }
            if (query.where.contains("n") && (item instanceof NestedView)) {
                clazzPass = true;
            }
            if (query.where.contains("v") && (item instanceof View) && !(item instanceof NestedView)) {
                clazzPass = true;
            }
            if (!clazzPass) {
                return false;
            }
            if (query.bool.equals("a")) {
                String[] parts = query.getWithoutArgumentsSplit();
                for (String part : parts) {
                    if (!matchSingle(nameOrPath, part, query)) {
                        return false;
                    }
                }
                return true;
            } else if (query.bool.equals("o")) {
                String[] parts = query.getWithoutArgumentsSplit();
                for (String part : parts) {
                    if (matchSingle(nameOrPath, part, query)) {
                        return true;
                    }
                }
                return false;
            } else {
                return matchSingle(nameOrPath, query.withoutArguments, query);
            }
        }

        private boolean matchSingle(String nameOrPath, String queryOrPart, Query query) {
            return matchSingle(nameOrPath, queryOrPart, query.how);
        }

        public static boolean matchSingle(String nameOrPath, String queryOrPart, String how) {
            if (how.equals("s")) {
                return nameOrPath.startsWith(queryOrPart);
            } else if (how.equals("e")) {
                return nameOrPath.endsWith(queryOrPart);
            } else if (how.equals("r")) {
                return nameOrPath.matches(queryOrPart);
            } else if (how.equals("R")) {
                return nameOrPath.matches(".*" + queryOrPart + ".*");
            } else if (how.equals("q")) {
                return nameOrPath.equalsIgnoreCase(queryOrPart);
            } else if (how.equals("Q")) {
                return nameOrPath.equals(queryOrPart);
            } else {
                return nameOrPath.contains(queryOrPart);
            }
        }

        public Optional<AbstractProject> getProject() {
            if (item instanceof AbstractProject) {
                return Optional.of((AbstractProject) item);
            } else {
                return Optional.empty();
            }
        }
    }

    private static transient volatile List<NamableWithClass> allCache = new ArrayList(0);
    private static transient volatile int allTTL = 0;
    private static transient volatile Date lastRefresh = new Date(0);
    private static final long refreshTimeout = 10l * 60l * 1000l;
    private static final int refreshAmount = 20;

    @SuppressFBWarnings(value = {"ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD"}, justification = "allTTL and allCache are used to cache base foundation of search. Cache is shared between insntances")
    public NestedViewsSearch() {
        Date currentSearch = new Date();
        long timeDiff = currentSearch.getTime() - lastRefresh.getTime();
        allTTL--;
        if (allTTL <= 0 || timeDiff > refreshTimeout) {
            allTTL = refreshAmount;
            lastRefresh = currentSearch;
            List<NamableWithClass> all = new ArrayList(1000);
            Jenkins j = Jenkins.get();
            for (TopLevelItem ti : j.getItems()) {
                if (ti instanceof AbstractProject) {
                    all.add(new NamableWithClass(ti, ti.getName(), ti.getName()));
                }
            }
            addViewsRecursively(j.getViews(), "/", all);
            allCache = all;
        }
    }

    private void addViewsRecursively(Collection<View> views, String s, List<NamableWithClass> all) {
        for (View v : views) {
            if (v instanceof NestedView) {
                NestedView nw = (NestedView) v;
                all.add(new NamableWithClass(v, v.getViewName(), s + v.getViewName()));
                addViewsRecursively(((NestedView) v).getViews(), s + v.getViewName() + "/", all);
            } else {
                all.add(new NamableWithClass(v, v.getViewName(), s + v.getViewName()));
            }
        }
    }

    private static class NestedViewsSearchResult implements SearchItem, Comparable, ExtendedSearch {
        private final String searchName;
        private final String searchUrl;
        private final ProjectWrapper project;

        public boolean isStillValid() {
            if (project != null) {
                return project.isStillValid();
            }
            return true;
        }

        @Override
        public String getSearchName() {
            return searchName;
        }

        @Override
        public String getSearchUrl() {
            return searchUrl;
        }

        @Override
        public SearchIndex getSearchIndex() {
            return null;
        }

        public NestedViewsSearchResult(String searchName, String searchUrl, Optional<AbstractProject> project, Query query) {
            this.searchName = searchName;
            this.searchUrl = searchUrl;
            if (query != null) {
                this.project = new ProjectWrapper(project, query.isMultiline(), query.isProjectInfo(), query.getStats(), query.getLast(), query.getBuilds(), query);
            } else {
                this.project = new ProjectWrapper(Optional.empty(), false, false, -1, -1, -1, null);
            }
        }

        @Override
        public String toString() {
            return searchName;
        }

        public String toPlainOldHref() {
            return "<a href=\"" + searchUrl + "\">" + searchName + "</a>";
        }

        @Override
        @SuppressFBWarnings(value = {"EQ_COMPARETO_USE_OBJECT_EQUALS"}, justification = "intentional. We check the types when filling the allCached, and the classes have not much in common")
        public int compareTo(Object o) {
            return this.toString().length() - o.toString().length();
        }

        @Override
        public ProjectWrapper getProject() {
            return project;
        }
    }

    private final static Logger LOGGER = Logger.getLogger(Search.class.getName());
    private List<NestedViewsSearchResult> hits = new ArrayList<>();
    private Query query;


    @Override
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String query = req.getParameter("q");
        if (query != null) {
            this.query = new Query(true, query);
            if (this.query.isNonTrivial(false)) {
                for (NamableWithClass item : allCache) {
                    if (item.matches(this.query)) {
                        NestedViewsSearchResult n = new NestedViewsSearchResult(item.getUsefulName(), item.getUrl(), item.getProject(), this.query);
                        if (n.isStillValid()) {
                            hits.add(n);
                        }
                    }
                }
            }
        }
        Collections.sort(hits);
        //todo, add paging &start=&count= .. defaulting to 0 and somwhere on 1000. Probably add next/prev links to jelly. Include `showing x/form` in jelly
        RequestDispatcher v = req.getView(this, "search-results.jelly");
        v.forward(req, rsp);
    }

    @Override
    public SearchResult getSuggestions(final StaplerRequest req, @QueryParameter final String query) {
        SearchResult suggestedItems = super.getSuggestions(req, query);
        this.query = new Query(false, query);
        if (this.query.isNonTrivial(true)) {
            for (NamableWithClass item : allCache) {
                if (item.matches(this.query)) {
                    suggestedItems.add(new SuggestedItem(new NestedViewsSearchResult(item.getUsefulName(), item.getUrl(), item.getProject(), null)));
                }
            }
        }
        return suggestedItems;
    }

    public static class HelpItem {
        private final String key;
        private final String description;

        public HelpItem(String key, String description) {
            this.key = key;
            this.description = description;
        }

        public String getKey() {
            return key;
        }

        public String getDescription() {
            return description;
        }
    }


    public List<HelpItem> getSearchHelp() throws IOException {
        List<HelpItem> r = new ArrayList<>();
        r.add(new HelpItem("the modifier(s) must start with `-` and end with `:`", ""));
        r.add(new HelpItem("r", "regex. Regex will be used also if query contains .*"));
        r.add(new HelpItem("R", "regex, but appended and prepended by .*"));
        r.add(new HelpItem("c", "contains (default)"));
        r.add(new HelpItem("s", "starts with"));
        r.add(new HelpItem("e", "ends with"));
        r.add(new HelpItem("q", "equals"));
        r.add(new HelpItem("Q", "equals honoring case"));
        r.add(new HelpItem("a", "and - query will be split on spaces, and all must match"));
        r.add(new HelpItem("o", "or - query will be split on spaces, and at least one must match"));
        r.add(new HelpItem("p", "match just name"));
        r.add(new HelpItem("f", "match full path (default)"));
        r.add(new HelpItem("j", "search only in jobs (default is in all -jw (-jvn))"));
        r.add(new HelpItem("v", "search only in views (default is in all) -jw (-jvn)"));
        r.add(new HelpItem("n", "search only in nested views (default is in all -jw (-jvn))"));
        r.add(new HelpItem("w", "search only in views and nested views (default is in all -jw (-jvn))"));
        r.add(new HelpItem("!", "invert result"));
        r.add(new HelpItem("Xn", "for NEXTn searches Nested View search will be turned off. n is optional number 1-9"));
        r.add(new HelpItem("eg \"-Rjo: dacapo sp[ei]c\"", "will find all Jobs which Matches .*dacapo.* or .*sp[ei]c.* "));
        r.add(new HelpItem(" Project/build details in search: ", ""));
        r.add(new HelpItem("m", "multiline instead of singe line"));
        r.add(new HelpItem("P", "will include project details"));
        r.add(new HelpItem("Ln", "will add information about last builds. Plain L c an be followed by mask of numbers 1-last,2-stable,3-green,4-yellow,5-red,6-unsuccess,7-completed"));
        r.add(new HelpItem("Bn", "details about builds. N is limiting am amount of builds. Default is 10!"));
        r.add(new HelpItem("Sn", "statistics (like weather, but in numbers). N is limiting am amount of builds. Default is 10!"));
        r.add(new HelpItem("S x B x L", "S and B switches are iterating to the past. This may have significant performance impact! L should be fast always"));
        r.add(new HelpItem("d",
                "will search also in DisplayName. In addition it sets `-oB` as OR and Build details are required for it to work. The OR is enforcing you to filter jobs first and name as second"));
        r.add(new HelpItem("D",
                "Same -d, but only projects with at least one matching build will be shown"));
        return r;
    }

    public List<NestedViewsSearchResult> getHits() {
        return hits;
    }

}
