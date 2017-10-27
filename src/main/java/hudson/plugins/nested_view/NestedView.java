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

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.io.StreamException;
import com.thoughtworks.xstream.io.xml.XppDriver;
import hudson.util.IOException2;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import hudson.util.XStream2;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.Source;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.model.Descriptor.FormException;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.views.ListViewColumn;
import hudson.views.ViewsTabBar;
import jenkins.model.ModelObjectWithContextMenu;
import org.apache.commons.jelly.JellyException;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static hudson.Util.fixEmpty;

/**
 * View type that contains only another set of views.
 * Allows grouping job views into multiple levels instead of one big list of tabs.
 *
 * @author Alan Harder
 * @author Kohsuke Kawaguchi
 * @author Romain Seguy
 */
public class NestedView extends View implements ViewGroup, StaplerProxy, ModelObjectWithContextMenu {
    private final static Result WORST_RESULT = Result.FAILURE;

    /**
     * Nested views.
     */
    private final CopyOnWriteArrayList<View> views = new CopyOnWriteArrayList<View>();

    /**
     * Name of the subview to show when this tree view is selected.  May be null/empty.
     */
    private String defaultView;
    private NestedViewColumns columns;

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

    public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) throws IOException, JellyException {
        return new ContextMenu().from(this, request, response);
    }

    public ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        ContextMenu menu = new ContextMenu();
        for (View view : getViews()) {
            menu.add(new MenuItem().withContextRelativeUrl(view.getUrl()).withDisplayName(view.getDisplayName()));
        }
        return menu;
    }

    @Override
    public String getUrl() {
        return getViewUrl();
    }

    @Override
    public View getPrimaryView() {
        return null;
    }

    @Override
    public ItemGroup<? extends TopLevelItem> getItemGroup() {
        return getOwnerItemGroup();
    }

    @Override
    public List<Action> getViewActions() {
        return getOwner().getViewActions();
    }

    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException {
        ItemGroup itemGroup = getItemGroup();
        if (itemGroup instanceof ModifiableItemGroup) {
            return ((ModifiableItemGroup) itemGroup).doCreateItem(req, rsp);
        }
        return null;
    }

    /**
     * Checks if a nested view with the given name exists and 
     * make sure that the name is good as a view name.
     */
    public FormValidation doCheckViewName(@QueryParameter String value) {
        checkPermission(View.CREATE);
        
        String name = fixEmpty(value);
        if (name == null) 
            return FormValidation.ok();
        
        // already exists?
        if (getView(name) != null) 
            return FormValidation.error(hudson.model.Messages.Hudson_ViewAlreadyExists(name));
        
        // good view name?
        try {
            jenkins.model.Jenkins.checkGoodName(name);
        } catch (Failure e) {
            return FormValidation.error(e.getMessage());
        }

        return FormValidation.ok();
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
            v.onJobRenamed(item, oldName, newName);
    }

    protected synchronized void submit(StaplerRequest req) throws IOException, ServletException, FormException {
        defaultView = Util.fixEmpty(req.getParameter("defaultView"));
        if (columns == null) {
            columns = new NestedViewColumns();
        }
        if (columns.getColumns() == null) {
            columns.setColumns(new DescribableList<ListViewColumn, Descriptor<ListViewColumn>>(this));
        }
        columns.updateFromForm(req, req.getSubmittedForm(), "columnsToShow");
    }

    public boolean canDelete(View view) {
        return true;
    }

    public void deleteView(View view) throws IOException {
        views.remove(view);
        save();
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

    public NestedViewColumns getColumnsToShow() {
        return columns;
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
     * <p>To get the worst result, this method browses all the jobs this view
     * contains. Also, as soon as it finds the worst result possible (cf.
     * {@link #WORST_RESULT}), the browsing stops.</p>
     * <p>The algorithm first analyzes normal views (that is, views which are
     * not nested ones); Then, in a second time, it processes nested views,
     * hoping that {@link #WORST_RESULT} will be found as quick as possible, as
     * mentionned previously.</p>
     */
    public Result getWorstResult() {
        Result result = Result.NOT_BUILT, check;
        boolean found = false;

        List<View> normalViews = new ArrayList<View>();
        List<NestedView> nestedViews = new ArrayList<NestedView>();

        for (View v : views) {
            if (v instanceof NestedView) {
                nestedViews.add((NestedView) v);
            } else {
                normalViews.add(v);
            }
        }

        // we process "normal" views first since it's likely faster to process
        // them (no unknown nested hierarchy of views) and we may find the worst
        // case (which stops the processing) faster
        for (View v : normalViews) {
            check = getWorstResultForNormalView(v);
            if (check != null) {
                found = true;
                if (isWorst(check)) {
                    // cut the search if we find the worst possible case
                    return check;
                }
                result = getWorse(check, result);
            }
        }

        // nested views are processed in a second time
        for (NestedView v : nestedViews) {
            // notice that this algorithm is recursive: as such, if a job is present
            // in several views, then it is processed several times (except if it
            // has the worst result possible, in which case the algorithm ends)
            // TODO: derecursify the algorithm to improve performance on complex views
            check = v.getWorstResult();
            if (check != null) {
                found = true;
                if (isWorst(check)) {
                    // as before, cut the search if we find the worst possible case
                    return check;
                }
                result = getWorse(check, result);
            }
        }

        return found ? result : null;
    }

    /**
     * Returns true if r is worst.
     */
    private static boolean isWorst(Result r) {
        return (r.isCompleteBuild() == WORST_RESULT.isCompleteBuild() &&
            r.isWorseOrEqualTo(WORST_RESULT));
    }

    /**
     * Returns the worse result from two.
     */
    private static Result getWorse(Result r1, Result r2) {
        // completed build wins
        if (!r1.isCompleteBuild() && r2.isCompleteBuild()) {
            return r2;
        }
        if (r1.isCompleteBuild() && !r2.isCompleteBuild()) {
            return r1;
        }

        // return worse one
        return r1.isWorseThan(r2) ? r1 : r2;
    }

    /**
     * Returns the worst result for a normal view, by browsing all the jobs it
     * contains; As soon as {@link #WORST_RESULT} is found, the browsing stops.
     * Returns null if no build occurred yet
     */
    private static Result getWorstResultForNormalView(View v) {
        boolean found = false;
        Result result = Result.NOT_BUILT, check;
        for (TopLevelItem item : v.getItems()) {
            if (item instanceof Job && !(  // Skip disabled projects
                    item instanceof AbstractProject && ((AbstractProject) item).isDisabled())) {
                final Run lastCompletedBuild = ((Job) item).getLastCompletedBuild();
                if (lastCompletedBuild != null) {
                    found = true;
                    check = lastCompletedBuild.getResult();
                    if (check == null) {
                        check = Result.SUCCESS;
                    }
                    if (isWorst(check)) {
                        // cut the search if we find the worst possible case
                        return check;
                    }

                    result = getWorse(check, result);
                }
            }
        }
        return found ? result : null;
    }

    /**
     * Returns the worst result for a view, wether is a normal view or a nested
     * one.
     *
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
            } else {
                items.addAll(currentView.getItems());
            }
        } while (!viewsStack.isEmpty());

        HealthReportContainer hrc = new HealthReportContainer();
        for (TopLevelItem item : items) {
            if (item instanceof Job) {
                hrc.sum += ((Job) item).getBuildHealth().getScore();
                hrc.count++;
            }
        }

        hrc.report = hrc.count > 0
                ? new HealthReport(hrc.sum / hrc.count, Messages._ViewHealth(hrc.count))
                : new HealthReport(100, Messages._NoJobs());

        return hrc;
    }
    
    @Override
    @WebMethod(name = "config.xml")
    public HttpResponse doConfigDotXml(StaplerRequest req) throws IOException {
       if (req.getMethod().equals("GET")) {
            checkPermission(READ);
            return new HttpResponse() {
                public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                    
                    rsp.setContentType("application/xml");
                    XStream2 xStream2 = new XStream2();
                    //the owner of this view has to stay unchanged.
                    xStream2.registerLocalConverter(View.class, "owner", new OwnerConvertor());
                    
                    xStream2.toXMLUTF8(NestedView.this,  rsp.getOutputStream());
                }
            };
        }
        if (req.getMethod().equals("POST")) {
            updateByXml((Source)new StreamSource(req.getReader()));
            
            return HttpResponses.ok();
        }
        return HttpResponses.error(400, "Unexpected request method " + req.getMethod());
    
    }
    
         /**
     * Updates View by its XML definition.
     */
    @Override
    public void updateByXml(Source source) throws IOException {
        checkPermission(CONFIGURE);
        StringWriter out = new StringWriter();
        try {
            // this allows us to use UTF-8 for storing data,
            // plus it checks any well-formedness issue in the submitted
            // data
            Transformer t = TransformerFactory.newInstance()
                    .newTransformer();
            t.transform(source,
                    new StreamResult(out));
            out.close();
        } catch (TransformerException e) {
            throw new IOException2("Failed to persist configuration.xml", e);
        }
        InputStream in = new BufferedInputStream(new ByteArrayInputStream(out.toString().getBytes("UTF-8")));
        try {
            XStream2 xstream = new XStream2();
            //Owner of this view does not have to be changed
            xstream.registerLocalConverter(View.class, "owner", new OwnerConvertor());
            xstream.unmarshal(new XppDriver().createReader(in), this);
        } catch (StreamException e) {
            throw new IOException2("Unable to read",e);
        } catch(ConversionException e) {
            throw new IOException2("Unable to read",e);
        } catch(Error e) {// mostly reflection errors
            throw new IOException2("Unable to read",e);
        } finally {
            in.close();
        }
        //owner = gr;
        save();
    }

    /**
     * Returns the health of a normal view.
     */
    private static HealthReportContainer getHealthForNormalView(View view) {
        HealthReportContainer hrc = new HealthReportContainer();
        for (TopLevelItem item : view.getItems()) {
            if (item instanceof Job) {
                Job job = (Job) item;
                if (job.getBuildHealthReports().isEmpty()) continue;
                hrc.sum += job.getBuildHealth().getScore();
                hrc.count++;
            }
        }
        hrc.report = hrc.count > 0
                ? new HealthReport(hrc.sum / hrc.count, Messages._ViewHealth(hrc.count))
                : null;
        return hrc;
    }

    /**
     * Returns the health of a view, wether it is a normal or a nested one.
     *
     * @see #getHealth()
     * @see #getHealthForNormalView(hudson.model.View)
     */
    public static HealthReportContainer getViewHealth(View v) {
        if (v instanceof NestedView) {
            return ((NestedView) v).getHealth();
        } else {
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

        private HealthReportContainer() {
        }

        public HealthReport getBuildHealth() {
            return report;
        }

        public List<HealthReport> getBuildHealthReports() {
            return report != null ? Collections.singletonList(report) : Collections.<HealthReport>emptyList();
        }
    }

    public Object getTarget() {
        // Proxy to handle redirect when a default subview is configured
        return (getDefaultView() != null &&
                "".equals(Stapler.getCurrentRequest().getRestOfPath()))
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
    
    /**
     * Handle owner attribute
     */
    public class OwnerConvertor implements Converter {
       

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            XStream stream = new XStream();
            if(source.equals(owner)){
                writer.addAttribute("ignore", "true");
                return;
            }
            stream.marshal(source, writer);
           
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            if(reader.getAttribute("ignore")!=null && reader.getAttribute("ignore").equals("true")){
                return owner;
            }
            XStream stream = new XStream();
            Object o = stream.unmarshal(reader);
            return o;
        }

        public boolean canConvert(Class type) {
            return ViewGroup.class.isAssignableFrom(type);
        }
  
    }

    @Extension
    public static final class DescriptorImpl extends ViewDescriptor {
        public String getDisplayName() {
            return Messages.DisplayName();
        }

    }
}
