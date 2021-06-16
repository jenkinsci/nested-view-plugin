package hudson.plugins.nested_view;



import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.FreeStyleProject;
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
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NestedViewsSearch extends Search {

    private static class Query {

        private final String original;
        private final String withoutArguments;

        private String where = "vnj"; //v,n,j
        private String how = "c"; //c,s,e,r,R,q,Q
        private String bool = ""; //a,o,""
        private String part = "f"; //p,f
        private boolean invert = false;

        public Query(String ooriginal) {
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
    }

    private static class NamableWithClass {
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
            if (item instanceof FreeStyleProject) {
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
            if (item instanceof FreeStyleProject) {
                return "../../../../../../../../../../../../../../job/" + name;
            } else {
                return "../../../../../../../../../../../../../../" + getFullPath().replace("/", "/view/");
            }
        }

        public boolean matches(Query query) {
            if (query.invert) {
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
            if (query.where.contains("j") && (item instanceof FreeStyleProject)) {
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
                String[] parts = query.withoutArguments.split("\\s+");
                for (String part : parts) {
                    if (!matchSingle(nameOrPath, part, query)) {
                        return false;
                    }
                }
                return true;
            } else if (query.bool.equals("o")) {
                String[] parts = query.withoutArguments.split("\\s+");
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
            if (query.how.equals("s")) {
                return nameOrPath.startsWith(queryOrPart);
            } else if (query.how.equals("e")) {
                return nameOrPath.endsWith(queryOrPart);
            } else if (query.how.equals("r")) {
                return nameOrPath.matches(queryOrPart);
            } else if (query.how.equals("R")) {
                return nameOrPath.matches(".*" + queryOrPart + ".*");
            } else if (query.how.equals("q")) {
                return nameOrPath.equalsIgnoreCase(queryOrPart);
            } else if (query.how.equals("Q")) {
                return nameOrPath.equals(queryOrPart);
            } else {
                return nameOrPath.contains(queryOrPart);
            }
        }
    }

    private static transient volatile List<NamableWithClass> allCache = new ArrayList(0);
    private static transient volatile int allTTL = 0;

    @SuppressFBWarnings(value = {"ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD"}, justification = "allTTL and allCache are used to cache base foundation of search. Cahce is shared between insntances")
    public NestedViewsSearch() {
        //this is very very naive caching and refreshing of results
        allTTL--;
        if (allTTL <= 0) {
            allTTL = 20;
            List<NamableWithClass> all = new ArrayList(1000);
            Jenkins j = Jenkins.get();
            for (TopLevelItem ti : j.getItems()) {
                if (ti instanceof FreeStyleProject) {
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

    private static class NestedViewsSearchResult implements SearchItem, Comparable {
        private final String searchName;
        private final String searchUrl;

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

        public NestedViewsSearchResult(String searchName, String searchUrl) {
            this.searchName = searchName;
            this.searchUrl = searchUrl;
        }

        @Override
        public String toString() {
            return searchName;
        }

        @Override
        @SuppressFBWarnings(value = {"EQ_COMPARETO_USE_OBJECT_EQUALS"}, justification = "intentional. We chack the types when filling the allCached, and the classes have not much in common")
        public int compareTo(Object o) {
            return this.toString().length() - o.toString().length();
        }
    }

    private final static Logger LOGGER = Logger.getLogger(Search.class.getName());
    private List<NestedViewsSearchResult> hits = new ArrayList<>();
    private Query query;


    @Override
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String query = req.getParameter("q");
        if (query != null) {
            this.query = new Query(query);
            //limit to at least 2 character(with .* ommited out)
            for (NamableWithClass item : allCache) {
                if (item.matches(this.query)) {
                    hits.add(new NestedViewsSearchResult(item.getUsefulName(), item.getUrl()));
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
        //the suggestions donot lose performance with to mch resutlst. So maybe the below todo limit is not ncessary at all
        for (NamableWithClass item : allCache) {
            this.query = new Query(query);
            //limit to at least 2 character(with .* ommited out)
            if (item.matches(this.query)) {
                suggestedItems.add(new SuggestedItem(new NestedViewsSearchResult(item.getUsefulName(), item.getUrl())));
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
        r.add(new HelpItem("R", "regex, but appended and preppended by .*"));
        r.add(new HelpItem("c", "contains (default)"));
        r.add(new HelpItem("s", "starts with"));
        r.add(new HelpItem("e", "ends with"));
        r.add(new HelpItem("q", "equals"));
        r.add(new HelpItem("Q", "equals honoring case"));
        r.add(new HelpItem("a", "and - query will be splitted on spaces, and all must match"));
        r.add(new HelpItem("o", "or - query will be splitted on spaces, and at least one must match"));
        r.add(new HelpItem("p", "match just name"));
        r.add(new HelpItem("f", "match full path (default)"));
        r.add(new HelpItem("j", "search only in jobs (default is in all -jw (-jvn))"));
        r.add(new HelpItem("v", "search only in views (default is in all) -jw (-jvn)"));
        r.add(new HelpItem("n", "search only in nested views (default is in all -jw (-jvn))"));
        r.add(new HelpItem("w", "search only in views and nested views (default is in all -jw (-jvn))"));
        r.add(new HelpItem("!", "invert result"));
        r.add(new HelpItem("eg \"-Rjo: dacapo sp[ei]c\"", "will find all Jobs which Matches .*dacapo.* or .*sp[ei]c.* "));
        return r;
    }

    public List<NestedViewsSearchResult> getHits() {
        return hits;
    }

}
