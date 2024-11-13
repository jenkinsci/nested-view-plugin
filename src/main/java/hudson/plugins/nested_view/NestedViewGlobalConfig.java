package hudson.plugins.nested_view;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

import java.util.logging.Logger;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;

@Extension
public class NestedViewGlobalConfig extends GlobalConfiguration {
    private static Logger logger = Logger.getLogger(NestedViewGlobalConfig.class.getName());

    boolean nestedViewSearch = true;
    int nestedViewHistoryCount = 500;
    String historyContent = "";

    public static NestedViewGlobalConfig getInstance() {
        return GlobalConfiguration.all().get(NestedViewGlobalConfig.class);
    }


    public boolean isNestedViewSearch() {
        return nestedViewSearch;
    }

    //some older jelly processors could have issues with just is
    public boolean getNestedViewSearch() {
        return nestedViewSearch;
    }

    public int getNestedViewHistoryCount() {
        return nestedViewHistoryCount;
    }

    public String getHistoryContent() {
        return historyContent;
    }

    @DataBoundSetter
    public void setNestedViewSearch(boolean nestedViewSearch) {
        this.nestedViewSearch = nestedViewSearch;
    }

    @DataBoundSetter
    public void setNestedViewHistoryCount(int nestedViewHistoryCount) {
        this.nestedViewHistoryCount = nestedViewHistoryCount;
    }

    @DataBoundSetter
    public void setHistoryContent(String historyContent) {
       this.historyContent = historyContent;
    }

    @DataBoundConstructor
    public NestedViewGlobalConfig(boolean nestedViewSearch, int nestedViewHistoryCount, String historyContent) {
        this.nestedViewSearch = nestedViewSearch;
        this.nestedViewHistoryCount = nestedViewHistoryCount;
        this.historyContent = historyContent;
    }

    public NestedViewGlobalConfig() {
        load();
    }


    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        save();
        return super.configure(req, json);
    }
}