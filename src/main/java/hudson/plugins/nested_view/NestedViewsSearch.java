package hudson.plugins.nested_view;



import hudson.search.Search;
import hudson.search.SearchIndex;
import hudson.search.SearchItem;
import hudson.search.SearchResult;
import hudson.search.SuggestedItem;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class NestedViewsSearch extends Search {

    private static class NestedViewsSearchResult implements SearchItem {
        private final String searchName;
        private final String searchUrl;

        public String getSearchName() {
            return searchName;
        }

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
    }

    private final static Logger LOGGER = Logger.getLogger(Search.class.getName());
    private List<NestedViewsSearchResult> hits = new ArrayList<>();


    public NestedViewsSearch() {

    }

    @Override
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String query = req.getParameter("q");
        if (query != null) {
            hits.add(new NestedViewsSearchResult("ccc", "../ddd"));
            hits.add(new NestedViewsSearchResult("eee", "../fff"));
        }
        RequestDispatcher v = req.getView(this, "search-results.jelly");
        v.forward(req, rsp);
    }

        @Override
        public SearchResult getSuggestions(final StaplerRequest req, @QueryParameter final String query) {
            SearchResult suggestedItems = super.getSuggestions(req, query);
            suggestedItems.add(new SuggestedItem(new SearchItem() {
                @Override
                public String getSearchName() {
                    return "aaaa";
                }

                @Override
                public String getSearchUrl() {
                    return "bbbbb";
                }

                @Override
                public SearchIndex getSearchIndex() {
                    return null;
                }
            }));
            return suggestedItems;
        }

    public String getSearchHelp() throws IOException {
        return "nvp help";
    }

    public List<NestedViewsSearchResult> getHits() {
        return hits;
    }

}
