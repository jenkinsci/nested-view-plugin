/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Alan Harder
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

import org.htmlunit.ElementNotFoundException;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlOption;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.xml.XmlPage;

import hudson.model.AllView;
import hudson.model.Cause.UserCause;
import hudson.model.FreeStyleProject;
import hudson.model.ListView;

import static hudson.model.Result.*;

import hudson.security.csrf.CrumbIssuer;

import static hudson.util.FormValidation.Kind.*;

import jakarta.servlet.ServletRequest;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import static org.junit.Assert.*;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.xml.sax.SAXException;

/**
 * Test interaction of nested-view plugin with Jenkins core.
 *
 * @author Alan Harder
 */
public class NestedViewTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    public void test() throws Exception {
        rule.createFreeStyleProject("Abcd");
        rule.createFreeStyleProject("Efgh");
        WebClient wc = rule.createWebClient();

        // Create a new nested view
        HtmlForm form = wc.goTo("newView").getFormByName("createItem");
        form.getInputByName("name").setValue("test-nest");
        form.getInputByValue("hudson.plugins.nested_view.NestedView").setChecked(true);
        rule.submit(form);
        // Add some subviews
        form = wc.goTo("view/test-nest/newView").getFormByName("createItem");
        form.getInputByName("name").setValue("subview");
        form.getInputByValue("hudson.model.ListView").setChecked(true);
        form = rule.submit(form).getFormByName("viewConfig");
        form.getInputByName("useincluderegex").setChecked(true);
        form.getInputByName("includeRegex").setValue("E.*");
        rule.submit(form);
        form = wc.goTo("view/test-nest/newView").getFormByName("createItem");
        form.getInputByName("name").setValue("subnest");
        form.getInputByValue("hudson.plugins.nested_view.NestedView").setChecked(true);
        rule.submit(form);
        form = wc.goTo("view/test-nest/newView").getFormByName("createItem");
        form.getInputByName("name").setValue("suball");
        form.getInputByValue("hudson.model.AllView").setChecked(true);
        rule.submit(form);
        // Verify links to subviews
        HtmlPage page = wc.goTo("view/test-nest/");
        assertNotNull(page.getAnchorByHref("/jenkins/view/test-nest/view/subview/"));
        assertNotNull(page.getAnchorByHref("/jenkins/view/test-nest/view/subnest/"));
        assertNotNull(page.getAnchorByHref("/jenkins/view/test-nest/view/suball/"));
        // Now set a default subview
        form = wc.goTo("view/test-nest/configure").getFormByName("viewConfig");
        List<HtmlOption> options = form.getSelectByName("defaultView").getOptions();
        assertEquals("", options.get(0).getValueAttribute());
        assertEquals("suball", options.get(1).getValueAttribute());
        assertEquals("subview", options.get(2).getValueAttribute());
        // "None" and 2 views in alphabetical order; subnest should not be in list
        assertEquals(3, options.size());
        options.get(1).setSelected(true);
        rule.submit(form);
        // Verify redirect to default subview
        page = wc.goTo("view/test-nest/");
        assertNotNull(page.getAnchorByHref("job/Efgh/"));
        // Verify link to add a subview for empty nested view
        page = wc.goTo("view/test-nest/view/subnest/");
        assertNotNull(page.getAnchorByHref("/jenkins/view/test-nest/view/subnest/newView"));
    }

    @Test
    public void testGetWorstResult() throws Exception {
        NestedView view = new NestedView("test");
        view.setOwner(rule.jenkins);
        assertSame(null, NestedView.getWorstResult(view));    // Empty
        view.addView(new AllView("foo", view));
        assertSame(null, NestedView.getWorstResult(view));    // Empty
        FreeStyleProject p = rule.createFreeStyleProject();
        assertSame(null, NestedView.getWorstResult(view));    // Job not yet run
        rule.assertBuildStatusSuccess(p.scheduleBuild2(0, new UserCause()).get());
        assertSame(SUCCESS, NestedView.getWorstResult(view));    // Job ran ok
        FreeStyleProject bad = rule.createFreeStyleProject();
        bad.getBuildersList().add(new FailureBuilder());
        assertSame(SUCCESS, NestedView.getWorstResult(view));    // New job not yet run
        rule.assertBuildStatus(FAILURE, bad.scheduleBuild2(0, new UserCause()).get());
        assertSame(FAILURE, NestedView.getWorstResult(view));    // Job failed
        bad.disable();
        assertSame(SUCCESS, NestedView.getWorstResult(view));    // Ignore disabled job
    }

    @Test
    public void testStatusOfEmptyNest() throws Exception {
        NestedView parent = new NestedView("parent");
        parent.setOwner(rule.jenkins);
        NestedView child = new NestedView("child");
        parent.addView(child);
        assertSame(null, NestedView.getWorstResult(child));     // Empty
        assertSame(null, NestedView.getWorstResult(parent));    // contains Empty child only
    }

    @Test
    public void testDoViewExistsCheck() {
        NestedView view = new NestedView("test");
        view.setOwner(rule.jenkins);
        view.addView(new ListView("foo", view));
        assertSame(OK, view.doViewExistsCheck(null).kind);
        assertSame(OK, view.doViewExistsCheck("").kind);
        assertSame(OK, view.doViewExistsCheck("bar").kind);
        assertSame(ERROR, view.doViewExistsCheck("foo").kind);
    }

    @Bug(25315)
    public void testUploadXml() throws Exception {
        NestedView parent = new NestedView("parent");
        rule.jenkins.addView(parent);
        assertEquals(rule.jenkins, parent.getOwner());
        NestedView child = new NestedView("child");
        parent.addView(child);
        child.setOwner(parent);
        WebClient wc = rule.createWebClient();
        wc.goTo("view/parent/view/child/");
        String xml = wc.goToXml("view/parent/config.xml").getTextContent();
        assertFalse(xml, xml.contains("<owner"));
        // First try creating a clone of this view (Jenkins.doCreateView → View.create → View.createViewFromXML):
        // TODO wc.createCrumbedUrl does not work when you are specifying your own query parameters
        CrumbIssuer issuer = rule.jenkins.getCrumbIssuer();
        WebRequest req = new WebRequest(new URL(rule.getURL(), "/createView?name=clone&" + issuer.getDescriptor().getCrumbRequestField() + "=" + issuer.getCrumb((ServletRequest) null)), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(xml);
        wc.getPage(req);
        NestedView clone = (NestedView) rule.jenkins.getView("clone");
        assertNotNull(clone);
        assertEquals(rule.jenkins, clone.getOwner());
        child = (NestedView) clone.getView("child");
        assertNotNull(child);
        assertEquals(clone, child.getOwner());
        wc.goTo("view/clone/view/child/");
        // Now try replacing an existing view (View.doConfigDotXml → View.updateByXml):
        req = new WebRequest(wc.createCrumbedUrl("view/parent/config.xml"), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(xml);
        wc.getPage(req);
        parent = (NestedView) rule.jenkins.getView("parent");
        assertNotNull(parent);
        assertEquals(rule.jenkins, parent.getOwner());
        child = (NestedView) parent.getView("child");
        assertNotNull(child);
        assertEquals(parent, child.getOwner());
        wc.goTo("view/parent/view/child/");
    }

    //nested view should be reloadable from config.xml which it provides
    // With update from  <jenkins.version>1.580.1 to 2.164
    // this test started to failas crumb is no longer possible via get
    @Test(expected = FailingHttpStatusCodeException.class)
    public void testDotConfigXmlOwnerSettings() throws Exception {
        NestedView root = new NestedView("nestedRoot");
        root.setOwner(rule.jenkins);
        ListView viewLevel1 = new ListView("listViewlvl1", root);
        NestedView subviewLevel1 = new NestedView("nestedViewlvl1");
        subviewLevel1.setOwner(root);
        NestedView subviewLevel2 = new NestedView("nestedViewlvl2");
        subviewLevel2.setOwner(subviewLevel1);
        ListView viewLevel2 = new ListView("listViewlvl2", subviewLevel1);
        ListView viewLevel3 = new ListView("listViewlvl3", subviewLevel2);
        root.addView(viewLevel1);
        root.addView(subviewLevel1);
        subviewLevel1.addView(subviewLevel2);
        subviewLevel1.addView(viewLevel2);
        subviewLevel2.addView(viewLevel3);
        rule.jenkins.addView(root);
        root.save();
        WebClient wc = rule.createWebClient();
        URL url = new URL(rule.jenkins.getRootUrl() + root.getUrl() + "config.xml");
        XmlPage page = wc.getPage(url);
        String configDotXml = page.getWebResponse().getContentAsString();
        configDotXml = configDotXml.replace("listViewlvl1", "new");
        url = new URL(rule.jenkins.getRootUrl() + root.getUrl() + "config.xml/?.crumb=test");
        WebRequest s = new WebRequest(url, HttpMethod.POST);
        s.setRequestBody(configDotXml);
        wc.addRequestHeader("Content-Type", "application/xml");
        HtmlPage p = wc.getPage(s);
//        assertEquals("Root Nested view should have set owner.", rule.jenkins, rule.jenkins.getView("nestedRoot").getOwner());
//        assertNotNull("Configuration should be updated.", ((NestedView)rule.jenkins.getView("nestedRoot")).getView("new"));
//        root = (NestedView) rule.jenkins.getView("nestedRoot");
//        assertEquals("Nested subview should have correct owner.", root, root.getView("nestedViewlvl1").getOwner());
//        assertEquals("ListView subview should have correct woner.",root, root.getView("new").getOwner());
//        NestedView subview = (NestedView) root.getView("nestedViewlvl1");
//        assertEquals("Nested subview of subview should have correct woner.",subview, subview.getView("nestedViewlvl2").getOwner());
//        assertEquals("Listview subview of subview should have correct woner.",subview, subview.getView("nestedViewlvl2").getOwner());
    }

    @Ignore("TODO pending baseline with https://github.com/jenkinsci/jenkins/pull/1798")
    @Issue("JENKINS-25276")
    @Test
    public void testRenameJob() throws IOException {
        FreeStyleProject project = rule.createFreeStyleProject("project");
        NestedView view = new NestedView("nested");
        view.setOwner(rule.jenkins);
        rule.jenkins.addView(view);
        ListView subview = new ListView("listView", view);
        view.addView(subview);
        subview.add(project);
        assertTrue("Subview 'listView' should contains item 'project'", subview.contains(project));
        project.renameTo("project-renamed");
        assertTrue("Subview contains renamed item.", subview.contains(project));
    }

    @Issue("JENKINS-59466")
    @Test
    public void testSetViewNoOwner() throws IOException {
        FreeStyleProject project = rule.createFreeStyleProject("project");
        NestedView view = new NestedView("nested");
        //This should add a view owned by the Jenkins user since there is "no owner"
        rule.jenkins.addView(view);
        assertEquals("Jenkins", view.getOwner().getDisplayName());
        ListView subview = new ListView("listView", view);
        view.addView(subview);
        subview.add(project);
        assertTrue("Subview 'listView' should contains item 'project'", subview.contains(project));
    }


    static WebClient createViewAndJobsForNEstedViewSearch(JenkinsRule rule) throws Exception {
        WebClient wc = rule.createWebClient();

        // Create a job
        rule.createFreeStyleProject("test-job");
        // Create a new nested view
        HtmlForm form = wc.goTo("newView").getFormByName("createItem");
        form.getInputByName("name").setValue("test-nest");
        form.getInputByValue("hudson.plugins.nested_view.NestedView").setChecked(true);
        rule.submit(form);
        // Add some subviews
        form = wc.goTo("view/test-nest/newView").getFormByName("createItem");
        form.getInputByName("name").setValue("subview");
        form.getInputByValue("hudson.model.ListView").setChecked(true);
        form = rule.submit(form).getFormByName("viewConfig");
        form.getInputByName("useincluderegex").setChecked(true);
        form.getInputByName("includeRegex").setValue(".*job");
        rule.submit(form);
        return wc;
    }


    static HtmlAnchor searchAndCheck4(WebClient wc, JenkinsRule rule) throws IOException, SAXException {
        HtmlPage page = wc.search("-f: test-nest/subview");
        return page.getAnchorByHref(rule.getURL().toString() + "view/test-nest/view/subview");
    }

    static HtmlAnchor searchAndCheck3(WebClient wc, JenkinsRule rule) throws IOException, SAXException {
        HtmlPage page = wc.search("-p: subview");
        return page.getAnchorByHref(rule.getURL().toString() + "view/test-nest/view/subview");
    }

    static HtmlAnchor searchAndCheck2(WebClient wc, JenkinsRule rule) throws IOException, SAXException {
        HtmlPage page = wc.search("-p: test-nest");
        return page.getAnchorByHref(rule.getURL().toString() + "view/test-nest");
    }

    static HtmlAnchor searchAndCheck1(WebClient wc, JenkinsRule rule) throws IOException, SAXException {
        HtmlPage page = wc.search("test-job");
        return page.getAnchorByHref(rule.getURL().toString() + "job/test-job");
    }
}
