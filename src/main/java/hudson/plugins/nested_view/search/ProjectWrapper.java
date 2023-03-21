package hudson.plugins.nested_view.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
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
    private final Query nvrSearch;
    private List<LinkableCandidate> details;
    private final Collection<String> matched;
    private int matchedBuildsCount;
    private Date dateTime = new Date(Integer.MIN_VALUE);

    public ProjectWrapper(Optional<AbstractProject> project, boolean multiline, boolean projectInfo, int stats, int last, int builds, Query nvrSearch, Collection<String> matched) {
        this.project = project;
        this.multiline = multiline;
        this.projectInfo = projectInfo;
        this.stats = stats;
        this.last = last;
        this.builds = builds;
        this.nvrSearch = nvrSearch;
        this.matched = matched;
    }

    public List<LinkableCandidate> getDetails() {
        return details;
    }

    public void createDetails() {
        details = createDetailsImpl();
    }

    public List<LinkableCandidate> createDetailsImpl() {
        if (project.isPresent()) {
            List<LinkableCandidate> result = new ArrayList<>();
            if (projectInfo) {
                //-P
                int bc = project.get().getNextBuildNumber() - 1;
                String projectInfo = "" +
                        " builds count: " + bc + ", " +
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
                    setDateTime(lastBuild);
                    result.add(lastBuild.toLinkable(project.get().getName()));
                }
                if (s.contains("2")) {//-L2
                    BuildDetails lastStable = specifiedBuild(" last stable build    : ", project.get().getLastStableBuild());
                    setDateTime(lastStable);
                    result.add(lastStable.toLinkable(project.get().getName()));
                }
                if (s.contains("3")) {//-L3
                    BuildDetails lastSuc = specifiedBuild(" last success build   : ", project.get().getLastSuccessfulBuild());
                    setDateTime(lastSuc);
                    result.add(lastSuc.toLinkable(project.get().getName()));
                }
                if (s.contains("4")) {//-L4
                    BuildDetails lastUnst = specifiedBuild(" last unstable build  : ", project.get().getLastUnstableBuild());
                    result.add(lastUnst.toLinkable(project.get().getName()));
                }
                if (s.contains("5")) {//-L5
                    BuildDetails lastFail = specifiedBuild(" last failed build    : ", project.get().getLastFailedBuild());
                    setDateTime(lastFail);
                    result.add(lastFail.toLinkable(project.get().getName()));
                }
                if (s.contains("6")) {//-L6
                    BuildDetails lastUnsuc = specifiedBuild(" last unsuccess build : ", project.get().getLastUnsuccessfulBuild());
                    setDateTime(lastUnsuc);
                    result.add(lastUnsuc.toLinkable(project.get().getName()));
                }
                if (s.contains("7")) {//-L7
                    BuildDetails lastComp = specifiedBuild(" last completed build : ", project.get().getLastCompletedBuild());
                    setDateTime(lastComp);
                    result.add(lastComp.toLinkable(project.get().getName()));
                }
            }

            if (builds >= 0 || stats >= 0) {
                Iterator it = project.get().getBuilds().iterator();
                //-B , -Bn n builds to past
                List<BuildDetails> buildsList = new ArrayList<>();
                //-S, -Sn - stats ; we want to include also thsoe with 0 occurences, so the order is table like
                Map<Result, Integer> summ = new HashMap<>();
                summ.put(Result.ABORTED, 0);
                summ.put(Result.FAILURE, 0);
                summ.put(Result.NOT_BUILT, 0);
                summ.put(Result.SUCCESS, 0);
                summ.put(Result.UNSTABLE, 0);
                summ.put(null, 0); //running
                //if new arrive, it will occure anyway, but out of order
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
                            if (nvrSearch != null && nvrSearch.isSearchByNvr() >= 0) {
                                for (String candidate : nvrSearch.getWithoutArgumentsSplit()) {
                                    if (nvrSearch.isSearchByNvr() == 1 && matched != null && matched.contains(candidate)) {
                                        continue;
                                    }
                                    String displayName = b.getDisplayName();
                                    boolean matches = NamableWithClass.matchSingle(displayName, candidate, nvrSearch.getHow());
                                    if (!nvrSearch.isInvert()) {
                                        if (matches) {
                                            BuildDetails bb = buildToString(b);
                                            setDateTime(bb);
                                            buildsList.add(bb);
                                        }
                                    } else {
                                        if (!matches) {
                                            BuildDetails bb = buildToString(b);
                                            setDateTime(bb);
                                            buildsList.add(bb);
                                        }
                                    }
                                }
                            } else {
                                BuildDetails bb = buildToString(b);
                                setDateTime(bb);
                                buildsList.add(bb);
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
                    result.add(new LinkableCandidate(summ.entrySet().stream().sorted((t0, t1) -> {
                        String l1 = resultToString(t1.getKey());
                        String l0 = resultToString(t0.getKey());
                        return l1.compareTo(l0);
                    }).map(a -> resultToString(a.getKey()) + ": " + a.getValue() + "x").collect(Collectors.joining(", "))));
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

    private String resultToString(Result r) {
        return (r == null) ? "RUNNING" : r.toString();
    }

    private void setDateTime(BuildDetails build) {
        dateTime = new Date(Math.max(dateTime.getTime(), build.getDateTime()));
    }

    public long getDateTime() {
        return dateTime.getTime();
    }

    public boolean isMultiline() {
        return multiline;
    }

    private BuildDetails specifiedBuild(String s, Run lastBuild) {
        return lastBuild != null ? new BuildDetails(s, lastBuild) : new BuildDetails(s, null, null, null, null, new Date(0));
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