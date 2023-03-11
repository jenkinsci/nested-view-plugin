package hudson.plugins.nested_view.search;

import hudson.plugins.nested_view.ProjectWrapper;

// for some reasons, if class implements some interfaces, only methods from interface are available form jelly!
// thus this artificial interface...
public interface ExtendedSearch {

    public ProjectWrapper getProject();
}
