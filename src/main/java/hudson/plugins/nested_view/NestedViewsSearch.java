package hudson.plugins.nested_view;


import hudson.search.*;

import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

    public class NestedViewsSearch extends Search {
        private final static Logger LOGGER = Logger.getLogger(Search.class.getName());



        public NestedViewsSearch() {

        }

        @Override
        public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            String query = req.getParameter("q");
            if (query != null) {
               // hits = normalSearch(req, query);
              //  hits.addAll(manager.getHits(query, true));
            }
            //req.getView(this, "search-results.jelly").forward(req, rsp);
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


    }
