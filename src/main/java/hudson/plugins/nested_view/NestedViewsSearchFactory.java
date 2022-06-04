package hudson.plugins.nested_view;

import hudson.Extension;
import hudson.search.Search;
import hudson.search.SearchFactory;
import hudson.search.SearchableModelObject;

import java.io.File;

@Extension
public class NestedViewsSearchFactory extends SearchFactory {

    @Override
    public Search createFor(final SearchableModelObject owner) {
        String userHomeDir = System.getProperty("user.home");
        if (userHomeDir == null) {
            return new NestedViewsSearch();
        } else {
            // File nestedViewsFileForce = new File(userHomeDir, ".nestedViewsSearchForce");
            // if (nestedViewsFileForce.exists()) {
            //     return new NestedViewsSearch();
            // }
            // File nestedViewsFile = new File(userHomeDir, ".nestedViewsSearch");
            // if (nestedViewsFile.exists()) {
                return new Search();
            // } else {
            //     return new NestedViewsSearch();
            // }
        }
    }
}