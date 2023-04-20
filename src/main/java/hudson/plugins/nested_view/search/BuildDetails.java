package hudson.plugins.nested_view.search;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class BuildDetails {

    private final String id;
    private final String displayName;
    private final String result;
    private final String timeStampString;
    private final String prefix;
    private final Date dateTime;
    private final List<Run.Artifact> artifacts;

    public BuildDetails(String prefix, Run run, int archives) {
        this(prefix, run.getId(), run.getDisplayName(), run.getResult(), run.getTimestampString(), run.getTime(), archives>0?run.getArtifactsUpTo(archives):new ArrayList<Run.Artifact>(0));
    }

    @SuppressFBWarnings(value = {"EI_EXPOSE_REP2"}, justification = "date is not cared")
    public BuildDetails(String prefix, String id, String displayName, Result result, String timeStampString, Date dateTime, List<Run.Artifact> list) {
        this.prefix = prefix;
        this.id = id;
        this.displayName = displayName;
        this.result = result == null ? "RUNNING" : result.toString();
        this.timeStampString = timeStampString;
        this.dateTime = dateTime;
        this.artifacts = (list==null?new ArrayList<>():list);
    }

    public List<String> getArtifacts() {
        return artifacts.stream().map(a->a.relativePath).collect(Collectors.toList());
    }

    public String toString() {
        if (id != null) {
            return prefix + id + "/" +
                    displayName + "/" +
                    result + "/" +
                    timeStampString + " ago";
        } else {
            return prefix + "n/a";
        }
    }

    public LinkableCandidate toLinkable(String projectName, SearchArtifactsOptions searchArtifactsOptions, ProjectWrapper listener) {
        if (id != null) {
            String pre = "";
            String link;
            String post = "";
            if (prefix.isEmpty()) {
                link = id + "/" + displayName;
            } else {
                link = prefix;
                post = id + "/" + displayName;
            }
            post = post + "/" + result + "/" + timeStampString + " ago";
            if (artifacts.size()>0) {
                post += " ("+artifacts.size()+" artifacts)";
            }
            List<LinkableCandidate> sublinks = new ArrayList<>(artifacts.size());
            if(searchArtifactsOptions != null) {
                for (Run.Artifact artifact : artifacts) {
                    for (String candidate : searchArtifactsOptions.query) {
                        if (searchArtifactsOptions.algorithm == 1 && searchArtifactsOptions.matched != null && searchArtifactsOptions.matched.contains(candidate)) {
                            continue;
                        }
                        boolean matches = NamableWithClass.matchSingle(artifact.relativePath, candidate, searchArtifactsOptions.how);
                        if (!searchArtifactsOptions.invert) {
                            if (matches) {
                                sublinks.add(new LinkableCandidate("", artifact.relativePath, "", getJenkinsUrl() + "/job/" + projectName + "/" + id + "/artifact/" + artifact.relativePath, new ArrayList<>(0)));
                                if (listener!=null){
                                    listener.addArtifactCouont();
                                }
                                break;
                            }
                        } else {
                            if (!matches) {
                                sublinks.add(new LinkableCandidate("", artifact.relativePath, "", getJenkinsUrl() + "/job/" + projectName + "/" + id + "/artifact/" + artifact.relativePath, new ArrayList<>(0)));
                                listener.addArtifactCouont();
                                break;
                            }
                        }
                    }
                }
            }
            return new LinkableCandidate(pre, link, post, getJenkinsUrl() + "/job/" + projectName + "/" + id, sublinks);
        } else {
            return new LinkableCandidate(prefix + "n/a");
        }
    }

    public static String getJenkinsUrl() {
        return Jenkins.get().getRootUrl().replaceAll("[\\/]+$", "");
    }

    public long getDateTime() {
        return dateTime.getTime();
    }

    static class SearchArtifactsOptions {
        private final String[] query;
        private final int algorithm;
        private final Collection<String> matched;
        private final String how;
        private final boolean invert;

        public SearchArtifactsOptions(String[] query, int algorithm,  Collection<String> matched, String how, boolean invert) {
            this.query = query;
            this.algorithm = algorithm;
            this.matched = matched;
            this.how = how;
            this.invert = invert;
        }
    }
}
