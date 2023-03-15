package hudson.plugins.nested_view.search;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.AbstractProject;
import hudson.search.SearchIndex;
import hudson.search.SearchItem;

import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

public class NestedViewsSearchResult implements SearchItem, ExtendedSearch {
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
    public ProjectWrapper getProject() {
        return project;
    }

    public void createDetails() {
        project.createDetails();
    }

    public static class LenghtComparator implements Comparator<NestedViewsSearchResult>, Serializable {

        @Override
        public int compare(NestedViewsSearchResult a, NestedViewsSearchResult b) {
            return a.toString().length() - b.toString().length();
        }
    }

    public static class NameComparator implements Comparator<NestedViewsSearchResult>, Serializable {

        @Override
        public int compare(NestedViewsSearchResult a, NestedViewsSearchResult b) {
            return a.toString().compareTo(b.toString());
        }
    }

    public static class DateComparator implements Comparator<NestedViewsSearchResult>, Serializable {

        @Override
        public int compare(NestedViewsSearchResult a, NestedViewsSearchResult b) {
            if (a.getDate() == b.getDate()) {
                return 0;
            } else if (a.getDate() > b.getDate()) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    private long getDate() {
        return project.getDateTime();
    }
}
