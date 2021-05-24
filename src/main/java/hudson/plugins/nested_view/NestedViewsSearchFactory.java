package hudson.plugins.nested_view;

import hudson.Extension;
import hudson.search.Search;
import hudson.search.SearchFactory;
import hudson.search.SearchableModelObject;
import jenkins.model.Jenkins;

import javax.inject.Inject;


@Extension
public class NestedViewsSearchFactory extends SearchFactory {

    @Override
    public Search createFor(final SearchableModelObject owner) {
        return new NestedViewsSearch();
    }
}