package hudson.plugins.nested_view.search;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.model.Jenkins;

import java.util.ArrayList;
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

    public LinkableCandidate toLinkable(String projectName) {
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
            return new LinkableCandidate(pre, link, post, getJenkinsUrl() + "/job/" + projectName + "/" + id);
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
}
