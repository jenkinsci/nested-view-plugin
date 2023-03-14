package hudson.plugins.nested_view;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.AbstractProject;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.plugins.nested_view.search.HelpItem;
import hudson.plugins.nested_view.search.HistoryItem;
import hudson.plugins.nested_view.search.NamableWithClass;
import hudson.plugins.nested_view.search.NestedViewsSearchResult;
import hudson.plugins.nested_view.search.Query;
import hudson.search.Search;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class NestedViewsSearch extends Search {

    private static final long refreshTimeout = 10l * 60l * 1000l;
    private static final int refreshAmount = 20;
    public final static Logger LOGGER = Logger.getLogger(Search.class.getName());
    private static transient volatile List<NamableWithClass> allCache = new ArrayList(0);
    private static transient volatile int allTTL = 0;
    private static transient volatile Date lastRefresh = new Date(0);
    private List<NestedViewsSearchResult> hits;
    private Query query;

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

    @Override
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String query = req.getParameter("q");
        hits = new ArrayList<>();
        if (query != null) {
            this.query = new Query(true, query);
            if (this.query.isNonTrivial(false)) {
                List<NestedViewsSearchResult> initialHits = new ArrayList<>();
                final Set<String> matched = new HashSet<>();
                for (NamableWithClass item : allCache) {
                    if (item.matches(this.query, matched)) {
                        NestedViewsSearchResult n = new NestedViewsSearchResult(item.getUsefulName(), item.getUrl(), item.getProject(), this.query, matched);
                        initialHits.add(n);
                    }
                }
                //this have to be done after all names were tried agaisnt all tokens
                for (NestedViewsSearchResult hit : initialHits) {
                    hit.createDetails();
                    if (hit.isStillValid()) {
                        hits.add(hit);
                    }
                }
            }
            putToHistory(query, hits.size(), new Date());
        }
        Collections.sort(hits);
        //todo, add paging &start=&count= .. defaulting to 0 and somwhere on 1000. Probably add next/prev links to jelly. Include `showing x/form` in jelly
        RequestDispatcher v = req.getView(this, "search-results.jelly");
        v.forward(req, rsp);
    }

    @SuppressFBWarnings(value = {"ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD"}, justification = "history is shared")
    private void putToHistory(String query, int size, Date date) {
        HistoryItem his = new HistoryItem(query.trim().replaceAll("\\s+", " "), size, date);
        HistoryItem.add(his);
    }

    @SuppressFBWarnings(value = {"ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD"}, justification = "history is shared")
    public List getHistory() {
        return HistoryItem.get();
    }

    @Override
    public SearchResult getSuggestions(final StaplerRequest req, @QueryParameter final String query) {
        SearchResult suggestedItems = super.getSuggestions(req, query);
        this.query = new Query(false, query);
        final Set<String> matched = new HashSet<>(); //unusuded for suggestions
        if (this.query.isNonTrivial(true)) {
            for (NamableWithClass item : allCache) {
                if (item.matches(this.query, matched)) {
                    suggestedItems.add(new SuggestedItem(new NestedViewsSearchResult(item.getUsefulName(), item.getUrl(), item.getProject(), null, null)));
                }
            }
        }
        return suggestedItems;
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
                "Same as -d, but only projects with at least one matching build will be shown. -d/-D  do not affect suggestions and can be acompanied by number - algorithm: "));
        r.add(new HelpItem("1: ", "default, what mathced project name, is not used in displayName search. -! is weird here, not sure what to do better"));
        r.add(new HelpItem("2: ", "all yor expressions are used used in displayName search"));
        return r;
    }

    public List<NestedViewsSearchResult> getHits() {
        return hits;
    }

}
