package hudson.plugins.nested_view.search;

import hudson.model.Result;
import hudson.model.Run;
import jenkins.model.Jenkins;

import java.util.Date;

public class BuildDetails {

    private final String id;
    private final String displayName;
    private final String result;
    private final String timeStampString;
    private final String prefix;
    private final Date dateTime;

    public BuildDetails(String prefix, Run run) {
        this(prefix, run.getId(), run.getDisplayName(), run.getResult(), run.getTimestampString(), run.getTime());
    }

    public BuildDetails(String prefix, String id, String displayName, Result result, String timeStampString, Date dateTime) {
        this.prefix = prefix;
        this.id = id;
        this.displayName = displayName;
        this.result = result == null ? "RUNNING" : result.toString();
        this.timeStampString = timeStampString;
        this.dateTime = dateTime;
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
            ;
            return new LinkableCandidate(pre, link, post, getJenkinsUrl() + "/job/" + projectName + "/" + id);
        } else {
            return new LinkableCandidate(prefix + "n/a");
        }
    }

    public static String getJenkinsUrl() {
        return Jenkins.get().getRootUrl().replaceAll("[\\/]+$", "");
    }

    public Date getDateTime() {
        return dateTime;
    }
}
