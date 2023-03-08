package hudson.plugins.nested_view;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.logging.Logger;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;

@Extension
public class NestedViewGlobalConfig extends GlobalConfiguration {
    private static Logger logger = Logger.getLogger(NestedViewGlobalConfig.class.getName());

    boolean nestedViewSearch;

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

    @DataBoundSetter
    public void setNestedViewSearch(boolean nestedViewSearch) {
        this.nestedViewSearch = nestedViewSearch;
    }

    @DataBoundConstructor
    public NestedViewGlobalConfig(boolean nestedViewSearch) {
        this.nestedViewSearch = nestedViewSearch;
    }

    public NestedViewGlobalConfig() {
        load();
    }


    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        save();
        return super.configure(req, json);
    }
}