/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Alan Harder
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
 */
public class NestedView extends View implements ViewGroup, StaplerProxy {
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
     * <p>Copy of {@link Hudson#doViewExistsCheck(String)}</p>
     * 
     * Checks if a top-level view with the given name exists.
     */
    public FormValidation doViewExistsCheck(@QueryParameter String value) {
        checkPermission(View.CREATE);

        String view = fixEmpty(value);
        if(view==null) return FormValidation.ok();

        if(getView(view)==null)
            return FormValidation.ok();
        else
            return FormValidation.error(hudson.model.Messages.Hudson_ViewAlreadyExists(view));
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
            if(v.getViewName().equals(name))
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
        views.add(View.create(req,rsp,this));
        save();
    }

    public static Result getWorstResult(View view) {
        Result result = Result.SUCCESS, check;
        if (view instanceof NestedView) {
            for (View v : ((NestedView)view).getViews()) {
                if ((check = getWorstResult(v)).isWorseThan(result))
                    result = check;
            }
        } else {
            for (TopLevelItem item : view.getItems()) {
                if (item instanceof Job) {
                    final Run lastCompletedBuild = ((Job)item).getLastCompletedBuild();
                    if (lastCompletedBuild != null && (check = lastCompletedBuild.getResult()).isWorseThan(result))
                        result = check;
                }
            }
        }
        return result;
    }

    public static HealthReportContainer getViewHealth(View view) {
        HealthReportContainer hrc = new HealthReportContainer();
        healthCounter(hrc, view);
        hrc.report = hrc.count > 0
                   ? new HealthReport(hrc.sum / hrc.count, Messages._ViewHealth(hrc.count))
                   : new HealthReport(100, Messages._NoJobs());
        return hrc;
    }

    private static void healthCounter(HealthReportContainer hrc, View view) {
        if (view instanceof NestedView) {
            for (View v : ((NestedView)view).getViews())
                healthCounter(hrc, v);
        } else {
            for (TopLevelItem item : view.getItems())
                if (item instanceof Job) {
                    hrc.sum += ((Job)item).getBuildHealth().getScore();
                    hrc.count++;
                }
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
