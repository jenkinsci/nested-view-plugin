package hudson.plugins.nested_view;

import hudson.Extension;
import hudson.model.RootAction;

@Extension
public class NestedViewLegacySearchGate implements RootAction {

    public String getIconFileName() {
        return "clipboard.png";
    }

    public String getDisplayName() {
        return "Nested view search gate";
    }

    public String getUrlName() {
        return "nwSearchGate";
    }

}
