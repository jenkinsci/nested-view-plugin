package hudson.plugins.nested_view;



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

public class NestedViewsSearch extends Search {

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
                return "../job/" + name;
            } else {
                return "../" + getFullPath().replace("/", "/view/");
            }
        }
    }

    private final List<NamableWithClass> all = new ArrayList(1000);

    public NestedViewsSearch() {
        Jenkins j = Jenkins.get();
        for (TopLevelItem ti : j.getItems()) {
            if (ti instanceof FreeStyleProject) {
                all.add(new NamableWithClass(ti, ti.getName(), ti.getName()));
            }
        }
        addViewsRecursively(j.getViews(), "/");
    }

    private void addViewsRecursively(Collection<View> views, String s) {
        for (View v : views) {
            if (v instanceof NestedView) {
                NestedView nw = (NestedView) v;
                all.add(new NamableWithClass(v, v.getViewName(), s + v.getViewName()));
                addViewsRecursively(((NestedView) v).getViews(), s + v.getViewName() + "/");
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
        public int compareTo(Object o) {
            return this.toString().length() - o.toString().length();
        }
    }

    private final static Logger LOGGER = Logger.getLogger(Search.class.getName());
    private List<NestedViewsSearchResult> hits = new ArrayList<>();


    @Override
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String query = req.getParameter("q");
        if (query != null) {
            for (NamableWithClass item : all) {
                if (item.getFullPath().contains(query)) {
                    hits.add(new NestedViewsSearchResult(item.getUsefulName(), item.getUrl()));
                }
            }
        }
        Collections.sort(hits);
        RequestDispatcher v = req.getView(this, "search-results.jelly");
        v.forward(req, rsp);
    }

        @Override
        public SearchResult getSuggestions(final StaplerRequest req, @QueryParameter final String query) {
            SearchResult suggestedItems = super.getSuggestions(req, query);
            for (NamableWithClass item : all) {
                if (item.getFullPath().contains(query)) {
                    suggestedItems.add(new SuggestedItem(new NestedViewsSearchResult(item.getUsefulName(), item.getUrl())));
                }
            }
            return suggestedItems;
        }

    public String getSearchHelp() throws IOException {
        return "nvp help";
    }

    public List<NestedViewsSearchResult> getHits() {
        return hits;
    }

}
