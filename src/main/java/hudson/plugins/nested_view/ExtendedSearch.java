package hudson.plugins.nested_view;

// for some reasons, if class implements soe interfaces, only ethods from interface are available form jelly!
// thus this artificial interface...
public interface ExtendedSearch {

    public ProjectWrapper getProject();
}
