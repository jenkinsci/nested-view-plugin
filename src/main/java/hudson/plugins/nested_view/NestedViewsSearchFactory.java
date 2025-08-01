package hudson.plugins.nested_view;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.search.Search;
import hudson.search.SearchFactory;
import hudson.search.SearchableModelObject;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

import jakarta.servlet.ServletException;
import java.io.IOException;


@Extension
public class NestedViewsSearchFactory extends SearchFactory {

    private static int tmpSkip = 0;

    public static boolean isTmpSkip() {
        return tmpSkip>0;
    }

    public static void setTmpSkip(int n) {
        NestedViewsSearchFactory.tmpSkip = n;
    }

    public static void resetTmpSkip() {
        NestedViewsSearchFactory.tmpSkip--;
    }

    @Override
    public Search createFor(final SearchableModelObject owner) {
        if (NestedViewGlobalConfig.getInstance().isNestedViewSearch()) {
            if (isTmpSkip()) {
                return new Search(){
                    @Override
                    public void doIndex(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
                        //we have to reset it only in search, not during suggestions
                        super.doIndex(req, rsp);
                        resetTmpSkip();
                    }
                };
            } else {
                return new NestedViewsSearch();
            }
        } else {
            return new Search();
        }
    }
}
