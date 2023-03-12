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
import hudson.plugins.nested_view.search.BuildDetails;
import hudson.plugins.nested_view.search.LinkableCandidate;
import hudson.plugins.nested_view.search.NamableWithClass;
import hudson.plugins.nested_view.search.Query;

public class ProjectWrapper {

    private final Optional<AbstractProject> project;
    private final boolean multiline;
    private final boolean projectInfo;
    private final int stats;
    private final int last;
    private final int builds;
    private final Query nvrSearch;
    private final List<LinkableCandidate> details;
    private int matchedBuildsCount;

    public ProjectWrapper(Optional<AbstractProject> project, boolean multiline, boolean projectInfo, int stats, int last, int builds, Query nvrSearch) {
        this.project = project;
        this.multiline = multiline;
        this.projectInfo = projectInfo;
        this.stats = stats;
        this.last = last;
        this.builds = builds;
        this.nvrSearch = nvrSearch;
        this.details = createDetails();
    }

    public List<LinkableCandidate> getDetails() {
        return createDetails();
    }

    public List<LinkableCandidate> createDetails() {
        if (project.isPresent()) {
            List<LinkableCandidate> result = new ArrayList<>();
            if (projectInfo) {
                //-P
                String projectInfo = "" +
                        " builds count: " + project.get().getBuilds().size() + ", " +
                        " is running  : " + project.get().isBuilding() + ", " +
                        " in queue    : " + project.get().isInQueue() + ", " +
                        " disabled    : " + project.get().isDisabled();
                result.add(new LinkableCandidate(projectInfo));
            }
            if (last >= 0) {
                String s = String.valueOf(last);
                //-L/L0 -all
                if (s.contains("1")) {//-L1
                    BuildDetails lastBuild = specifiedBuild(" last build           : ", project.get().getLastBuild());
                    result.add(lastBuild.toLinkable(project.get().getName()));
                }
                if (s.contains("2")) {//-L2
                    BuildDetails lastStable = specifiedBuild(" last stable build    : ", project.get().getLastStableBuild());
                    result.add(lastStable.toLinkable(project.get().getName()));
                }
                if (s.contains("3")) {//-L3
                    BuildDetails lastSuc = specifiedBuild(" last success build   : ", project.get().getLastSuccessfulBuild());
                    result.add(lastSuc.toLinkable(project.get().getName()));
                }
                if (s.contains("4")) {//-L4
                    BuildDetails lastUnst = specifiedBuild(" last unstable build  : ", project.get().getLastUnstableBuild());
                    result.add(lastUnst.toLinkable(project.get().getName()));
                }
                if (s.contains("5")) {//-L5
                    BuildDetails lastFail = specifiedBuild(" last failed build    : ", project.get().getLastFailedBuild());
                    result.add(lastFail.toLinkable(project.get().getName()));
                }
                if (s.contains("6")) {//-L6
                    BuildDetails lastUnsuc = specifiedBuild(" last unsuccess build : ", project.get().getLastUnsuccessfulBuild());
                    result.add(lastUnsuc.toLinkable(project.get().getName()));
                }
                if (s.contains("7")) {//-L7
                    BuildDetails lastComp = specifiedBuild(" last completed build : ", project.get().getLastCompletedBuild());
                    result.add(lastComp.toLinkable(project.get().getName()));
                }
            }

            if (builds >= 0 || stats >= 0) {
                Iterator it = project.get().getBuilds().iterator();
                //-B , -Bn n builds to past
                List<BuildDetails> buildsList = new ArrayList<>();
                //-S, -Sn - stats
                Map<Result, Integer> summ = new HashMap<>();
                int i1 = builds;
                int i2 = stats;
                while (it.hasNext()) {
                    if (i1 <= 0 && i2 <= 0) {
                        break;
                    }
                    Object q = it.next();
                    if (q instanceof AbstractBuild) {
                        AbstractBuild b = (AbstractBuild) q;
                        if (i1 > 0) {
                            if (nvrSearch != null && nvrSearch.isSearchByNvr()) {
                                for (String candidate : nvrSearch.getWithoutArgumentsSplit()) {
                                    String displayName = b.getDisplayName();
                                    boolean matches = NamableWithClass.matchSingle(displayName, candidate, nvrSearch.getHow());
                                    if (!nvrSearch.isInvert()) {
                                        if (matches) {
                                            buildsList.add(buildToString(b));
                                        }
                                    } else {
                                        if (!matches) {
                                            buildsList.add(buildToString(b));
                                        }
                                    }
                                }
                            } else {
                                buildsList.add(buildToString(b));
                            }
                        }
                        if (i2 > 0) {
                            Integer counter = summ.getOrDefault(b.getResult(), 0);
                            counter = counter + 1;
                            summ.put(b.getResult(), counter);
                        }
                    }
                    i1--;
                    i2--;
                }
                if (stats >= 0) {
                    result.add(new LinkableCandidate(summ.entrySet().stream().map(a ->
                            a.getKey() == null ? "RUNNING: " + a.getValue() + "x"
                                    : a.getKey() + ": " + a.getValue() + "x")
                            .collect(Collectors.joining(", "))));
                }
                if (builds >= 0) {
                    result.addAll(buildsList.stream().map(a -> a.toLinkable(project.get().getName())).collect(Collectors.toList()));
                }
                matchedBuildsCount = buildsList.size();
            }
            //-m multiline
            if (multiline && result.size() > 0) {
                result.add(0, new LinkableCandidate(""));
            }
            return result;
        } else {
            return Arrays.asList(new LinkableCandidate("N/A"));
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

    public boolean isStillValid() {
        if (project.isPresent()) {
            if (nvrSearch == null) {
                return true;
            } else {
                if (nvrSearch.isFinalFilter() && matchedBuildsCount <= 0) {
                    return false;
                } else {
                    return true;
                }
            }
        } else {
            return true;
        }
    }

}