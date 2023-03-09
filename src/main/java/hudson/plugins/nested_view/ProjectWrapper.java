package hudson.plugins.nested_view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;

public class ProjectWrapper {

    private final Optional<AbstractProject> project;
    private final boolean multiline;
    private final boolean projectInfo;
    private final int stats;
    private final int last;
    private final int builds;
    private final NestedViewsSearch.Query nvrSearch;

    public ProjectWrapper(Optional<AbstractProject> project, boolean multiline, boolean projectInfo, int stats, int last, int builds, NestedViewsSearch.Query nvrSearch) {
        this.project = project;
        this.multiline = multiline;
        this.projectInfo = projectInfo;        
        this.stats = stats;
        this.last = last;
        this.builds = builds;
        this.nvrSearch = nvrSearch;
    }

    //FIXME repalce String b somethign what can harbor link to exact build and maybe collored results
    public List<String> getDetails() {
        if (project.isPresent()) {
            //-P
            String projectInfo = "" +
                    " builds count: " + project.get().getBuilds().size() + ", " +
                    " is running  : " + project.get().isBuilding() + ", " +
                    " in queue    : " + project.get().isInQueue() + ", " +
                    " disabled    : " + project.get().isDisabled();
            //-L/L0 -all
            //-L1
            BuildDetails lastBuild = specifiedBuild(" last build           : ", project.get().getLastBuild());
            //-L2
            BuildDetails lastStable = specifiedBuild(" last stable build    : ", project.get().getLastStableBuild());
            //-L3
            BuildDetails lastSuc = specifiedBuild(" last success build   : ", project.get().getLastSuccessfulBuild());
            //-L4
            BuildDetails lastUnst = specifiedBuild(" last unstable build  : ", project.get().getLastUnstableBuild());
            //-L5
            BuildDetails lastFail = specifiedBuild(" last failed build    : ", project.get().getLastFailedBuild());
            //-L6
            BuildDetails lastUnsuc = specifiedBuild(" last unsuccess build : ", project.get().getLastUnsuccessfulBuild());
            //-L7
            BuildDetails lastComp = specifiedBuild(" last completed build : ", project.get().getLastCompletedBuild());

            Iterator it = project.get().getBuilds().iterator();
            //-B , -Bn n builds to past
            List<BuildDetails> buildsList = new ArrayList<>();
            //-S, -Sn - stats
            Map<Result, Integer> summ = new HashMap<>();
            int i = Math.max(builds, stats);
            while (it.hasNext()) {
                if (i<=0) {
                    break;
                }
                Object q = it.next();
                if (q instanceof AbstractBuild) {
                    AbstractBuild b = (AbstractBuild) q;
                    buildsList.add(buildToString(b));
                    Integer r = summ.getOrDefault(b.getResult(), 0);
                    r = r + 1;
                    summ.put(b.getResult(), r);
                }
                i--;
            }
            //-m multiline
            List<String> r = new ArrayList<>();
            if (multiline) {
                r.add("");
            }

            r.add(projectInfo);
            r.add(lastBuild.toString());
            r.add(lastStable.toString());
            r.add(lastSuc.toString());
            r.add(lastUnst.toString());
            r.add(lastFail.toString());
            r.add(lastUnsuc.toString());
            r.add(lastComp.toString());
            r.add(summ.entrySet().stream().map(a -> a.getKey() + ": " + a.getValue() + "x").collect(Collectors.joining(", ")));
            r.addAll(buildsList.stream().map(a -> a.toString()).collect(Collectors.toList()));
            return r;
        } else {
            return Arrays.asList("N/A");
        }
    }

    public boolean isMultiline() {
        return multiline;
    }

    private BuildDetails specifiedBuild(String s, Run lastBuild) {
        return lastBuild != null ? new BuildDetails(s, lastBuild) : new BuildDetails(s, null, null, null, null);
    }

    private BuildDetails buildToString(Run ab) {
        return specifiedBuild("", ab);
    }

    private static class BuildDetails {

        private final String id;
        private final String displayName;
        private final Result result;
        private final String timeStampString;
        private final String prefix;

        BuildDetails(String prefix, Run run) {
            this(prefix, run.getId(), run.getDisplayName(), run.getResult(), run.getTimestampString());
        }

        BuildDetails(String prefix, String id, String displayName, Result result, String timeStampString) {
            this.prefix = prefix;
            this.id = id;
            this.displayName = displayName;
            this.result = result;
            this.timeStampString = timeStampString;
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
    }
}
