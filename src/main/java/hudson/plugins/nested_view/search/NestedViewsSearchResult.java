package hudson.plugins.nested_view.search;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.AbstractProject;
import hudson.search.SearchIndex;
import hudson.search.SearchItem;

import java.util.Collection;
import java.util.Optional;

public class NestedViewsSearchResult implements SearchItem, Comparable, ExtendedSearch {
    private final String searchName;
    private final String searchUrl;
    private final ProjectWrapper project;

    public boolean isStillValid() {
        if (project != null) {
            return project.isStillValid();
        }
        return true;
    }

    @Override
    public String getSearchName() {
        return searchName;
    }

    @Override
    public String getSearchUrl() {
        return searchUrl;
    }

    @Override
    public SearchIndex getSearchIndex() {
        return null;
    }

    public NestedViewsSearchResult(String searchName, String searchUrl, Optional<AbstractProject> project, Query query, Collection<String> matched) {
        this.searchName = searchName;
        this.searchUrl = searchUrl;
        if (query != null) {
            this.project = new ProjectWrapper(project, query.isMultiline(), query.isProjectInfo(), query.getStats(), query.getLast(), query.getBuilds(), query, matched);
        } else {
            this.project = new ProjectWrapper(Optional.empty(), false, false, -1, -1, -1, null, null);
        }
    }

    @Override
    public String toString() {
        return searchName;
    }

    public String toPlainOldHref() {
        return "<a href=\"" + searchUrl + "\">" + searchName + "</a>";
    }

    @Override
    @SuppressFBWarnings(value = {"EQ_COMPARETO_USE_OBJECT_EQUALS"}, justification = "intentional. We check the types when filling the allCached, and the classes have not much in common")
    public int compareTo(Object o) {
        return this.toString().length() - o.toString().length();
    }

    @Override
    public ProjectWrapper getProject() {
        return project;
    }

    public void createDetails() {
        project.createDetails();
    }
}
