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

import hudson.model.Descriptor.FormException;
import hudson.Extension;
import hudson.Util;
import hudson.model.HealthReport;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewDescriptor;
import hudson.model.ViewGroup;

import javax.servlet.ServletException;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * View type that contains only another set of views.
 * Allows grouping job views into multiple levels instead of one big list of tabs.
 *
 * @author Alan.Harder@sun.com
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

    @Override
    public synchronized void onJobRenamed(Item item, String oldName, String newName) {
        // forward to children
        for (View v : views)
            v.onJobRenamed(item,oldName,newName);
    }

    protected void submit(StaplerRequest req) throws IOException, ServletException, FormException {
        defaultView = Util.fixEmpty(req.getParameter("defaultView"));
    }

    public void deleteView(View view) throws IOException {
        views.remove(view);
    }

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
        return getView(defaultView);
    }

    public void onViewRenamed(View view, String oldName, String newName) {
        // noop
    }

    public void save() throws IOException {
        owner.save();
    }

    public void doCreateView(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, FormException {
        try {
            checkPermission(View.CREATE);
            views.add(View.create(req,rsp,this));
            save();
        } catch (ParseException e) {
            sendError(e,req,rsp);
        }
    }

    public static HealthReportContainer getViewHealth(View view) {
        int sum = 0, count = 0;
        for (TopLevelItem item : view.getItems()) {
            if (item instanceof Job) {
                sum += ((Job)item).getBuildHealth().getScore();
                count++;
            }
        }
        return new HealthReportContainer(
                count > 0 ? new HealthReport(sum / count, Messages._ViewHealth(count))
                          : new HealthReport(100, Messages._NoJobs()));
    }

    /**
     * Container for HealthReport with two methods matching hudson.model.Job
     * so we can pass this to f:healthReport jelly.
     */
    public static class HealthReportContainer {
        private HealthReport report;
        public HealthReportContainer(HealthReport report) {
            this.report = report;
        }
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
