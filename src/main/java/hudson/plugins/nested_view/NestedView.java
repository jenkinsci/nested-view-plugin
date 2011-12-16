/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi, Alan Harder,
 * Manufacture Francaise des Pneumatiques Michelin, Romain Seguy
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.nested_view;

import static hudson.Util.fixEmpty;
import hudson.model.Descriptor.FormException;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.HealthReport;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.model.Run;
import hudson.model.View;
import hudson.model.Result;
import hudson.model.ViewDescriptor;
import hudson.model.ViewGroup;
import hudson.util.FormValidation;
import hudson.views.ViewsTabBar;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

/**
 * View type that contains only another set of views.
 * Allows grouping job views into multiple levels instead of one big list of tabs.
 *
 * @author Alan Harder
 * @author Kohsuke Kawaguchi
 * @author Romain Seguy
 */
public class NestedView extends View implements ViewGroup, StaplerProxy {
    private final static Result WORST_RESULT = Result.ABORTED;

    /**
     * Nested views.
     */
    private final CopyOnWriteArrayList<View> views = new CopyOnWriteArrayList<View>();

    /**
     * Name of the subview to show when this tree view is selected.  May be null/empty.
     */
    private String defaultView;

    @DataBoundConstructor
    public NestedView(String name) {
        super(name);
    }

    public List<TopLevelItem> getItems() {
        return Collections.emptyList();
    }

    public boolean contains(TopLevelItem item) {
        return false;
    }

    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException {
        return Hudson.getInstance().doCreateItem(req, rsp);
    }
    
    /**
     * Checks if a nested view with the given name exists.
     */
    public FormValidation doViewExistsCheck(@QueryParameter String value) {
        checkPermission(View.CREATE);

        String view = fixEmpty(value);
        return (view == null || getView(view) == null) ? FormValidation.ok()
          : FormValidation.error(hudson.model.Messages.Hudson_ViewAlreadyExists(view));
    }

    @Override
    public synchronized void onJobRenamed(Item item, String oldName, String newName) {
        // forward to children
        for (View v : views)
            v.onJobRenamed(item,oldName,newName);
    }

    protected void submit(StaplerRequest req) throws IOException, ServletException, FormException {
        defaultView = Util.fixEmpty(req.getParameter("defaultView"));
    }

    public boolean canDelete(View view) {
        return true;
    }

    public void deleteView(View view) throws IOException {
        views.remove(view);
    }

    @Exported
    public Collection<View> getViews() {
        List<View> copy = new ArrayList<View>(views);
        Collections.sort(copy, View.SORTER);
        return copy;
    }

    public View getView(String name) {
        for (View v : views)
            if (v.getViewName().equals(name))
                return v;
        return null;
    }

    public View getDefaultView() {
        // Don't allow default subview for a NestedView that is the Jenkins default view..
        // (you wouldn't see the other top level view tabs, as it'd always jump into subview)
        return isDefault() ? null : getView(defaultView);
    }

    public void onViewRenamed(View view, String oldName, String newName) {
        // noop
    }

    public void save() throws IOException {
        owner.save();
    }

    public void doCreateView(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, FormException {
        checkPermission(View.CREATE);
        addView(View.create(req, rsp, this));
        save();
    }

    // Method for testing
    void addView(View view) {
        views.add(view);
    }

    // Method for testing
    void setOwner(ViewGroup owner) {
        this.owner = owner;
    }

    /**
     * Returns the worst result for this nested view.
     *
     * <p>To get the worst result, this method browses all the jobs this view
     * contains. Also, as soon as it finds the worst result possible (cf.
     * {@link #WORST_RESULT}), the browsing stops.</p>
     * <p>The algorithm first analyzes normal views (that is, views which are
     * not nested ones); Then, in a second time, it processes nested views,
     * hoping that {@link #WORST_RESULT} will be found as quick as possible, as
     * mentionned previously.</p>
     */
    public Result getWorstResult() {
        Result result = Result.SUCCESS, check;

        List<View> normalViews = new ArrayList<View>();
        List<NestedView> nestedViews = new ArrayList<NestedView>();

        for (View v : views) {
            if(v instanceof NestedView) {
                nestedViews.add((NestedView) v);
            }
            else {
                normalViews.add(v);
            }
        }

        // we process "normal" views first since it's likely faster to process
        // them (no unknown nested hierarchy of views) and we may find the worst
        // case (which stops the processing) faster
        for (View v : normalViews) {
            if ((check = getWorstResultForNormalView(v)).isWorseThan(result)) {
                result = check;
                if (result.isWorseOrEqualTo(WORST_RESULT)) {
                    // cut the search if we find the worst possible case
                    return result;
                }
            }
        }

        // nested views are processed in a second time
        for (NestedView v : nestedViews) {
            // notice that this algorithm is recursive: as such, if a job is present
            // in several views, then it is processed several times (except if it
            // has the worst result possible, in which case the algorithm ends)
            // TODO: derecursify the algorithm to improve performance on complex views
            if ((check = v.getWorstResult()).isWorseThan(result)) {
                result = check;
                if (result.isWorseOrEqualTo(WORST_RESULT)) {
                    // as before, cut the search if we find the worst possible case
                    return result;
                }
            }
        }

        return result;
    }

    /**
     * Returns the worst result for a normal view, by browsing all the jobs it
     * contains; As soon as {@link #WORST_RESULT} is found, the browsing stops.
     */
    private static Result getWorstResultForNormalView(View v) {
        Result result = Result.SUCCESS, check;
        for (TopLevelItem item : v.getItems()) {
            if (item instanceof Job && !(  // Skip disabled projects
                  item instanceof AbstractProject && ((AbstractProject)item).isDisabled())) {
                final Run lastCompletedBuild = ((Job)item).getLastCompletedBuild();
                if (lastCompletedBuild != null
                        && (check = lastCompletedBuild.getResult()).isWorseThan(result))
                    result = check;
                    if (result.isWorseOrEqualTo(WORST_RESULT)) {
                        // cut the search if we find the worst possible case
                        return result;
                    }
            }
        }
        return result;
    }

    /**
     * Returns the worst result for a view, wether is a normal view or a nested
     * one.
     * @see #getWorstResult()
     * @see #getWorstResultForNormalView(hudson.model.View)
     */
    public static Result getWorstResult(View v) {
        if (v instanceof NestedView) {
            return ((NestedView) v).getWorstResult();
        } else {
            return getWorstResultForNormalView(v);
        }
    }

    /**
     * Returns the health of this nested view.
     *
     * <p>Notice that, if a job is contained in several sub-views of the current
     * view, then it is taken into account only once to get accurate stats.</p>
     * <p>This algorithm has been derecursified, hence the stack stuff.</p>
     */
    public HealthReportContainer getHealth() {
        // we use a set to avoid taking into account several times the same job
        // when computing the health
        Set<TopLevelItem> items = new LinkedHashSet<TopLevelItem>(100);

        // retrieve all jobs to analyze (using DFS)
        Deque<View> viewsStack = new ArrayDeque<View>(20);
        viewsStack.push(this);
        do {
            View currentView = viewsStack.pop();
            if (currentView instanceof NestedView) {
                for (View v : ((NestedView) currentView).views) {
                    viewsStack.push(v);
                }
            }
            else {
                items.addAll(currentView.getItems());
            }
        } while (!viewsStack.isEmpty());

        HealthReportContainer hrc = new HealthReportContainer();
        for (TopLevelItem item : items) {
            if (item instanceof Job) {
                hrc.sum += ((Job)item).getBuildHealth().getScore();
                hrc.count++;
            }
        }

        hrc.report = hrc.count > 0
                   ? new HealthReport(hrc.sum / hrc.count, Messages._ViewHealth(hrc.count))
                   : new HealthReport(100, Messages._NoJobs());

        return hrc;
    }

    /**
     * Returns the health of a normal view.
     */
    private static HealthReportContainer getHealthForNormalView(View view) {
        HealthReportContainer hrc = new HealthReportContainer();
        for (TopLevelItem item : view.getItems()) {
            if (item instanceof Job) {
                hrc.sum += ((Job)item).getBuildHealth().getScore();
                hrc.count++;
            }
        }
        hrc.report = hrc.count > 0
                   ? new HealthReport(hrc.sum / hrc.count, Messages._ViewHealth(hrc.count))
                   : new HealthReport(100, Messages._NoJobs());
        return hrc;
    }

    /**
     * Returns the health of a view, wether it is a normal or a nested one.
     * @see #getHealth()
     * @see #getHealthForNormalView(hudson.model.View)
     */
    public static HealthReportContainer getViewHealth(View v) {
        if (v instanceof NestedView) {
            return ((NestedView) v).getHealth();
        }
        else {
            return getHealthForNormalView(v);
        }
    }

    public ViewsTabBar getViewsTabBar() {
        return Hudson.getInstance().getViewsTabBar();
    }

    /**
     * Container for HealthReport with two methods matching hudson.model.Job
     * so we can pass this to f:healthReport jelly.
     */
    public static class HealthReportContainer {
        private HealthReport report;
        private int sum = 0, count = 0;
        private HealthReportContainer() { }
        public HealthReport getBuildHealth() {
            return report;
        }
        public List<HealthReport> getBuildHealthReports() {
            return Collections.singletonList(report);
        }
    }

    public Object getTarget() {
        // Proxy to handle redirect when a default subview is configured
        return "".equals(Stapler.getCurrentRequest().getRestOfPath())
                ? new DefaultViewProxy() : this;
    }

    public class DefaultViewProxy {
        public void doIndex(StaplerRequest req, StaplerResponse rsp)
                throws IOException, ServletException {
            if (getDefaultView() != null)
                rsp.sendRedirect2("view/" + defaultView);
            else
                req.getView(NestedView.this, "index.jelly").forward(req, rsp);
        }
    }

    @Extension
    public static final class DescriptorImpl extends ViewDescriptor {
        public String getDisplayName() {
            return Messages.DisplayName();
        }
    }
}
