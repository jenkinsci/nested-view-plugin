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

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.AllView;
import hudson.model.Cause.UserCause;
import hudson.model.FreeStyleProject;
import hudson.model.ListView;
import static hudson.model.Result.*;
import hudson.util.FormValidation;
import static hudson.util.FormValidation.Kind.*;
import java.util.List;

import org.junit.Ignore;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Test interaction of nested-view plugin with Jenkins core.
 * @author Alan Harder
 */
public class NestedViewTest extends HudsonTestCase {

    public void test() throws Exception {
        createFreeStyleProject("Abcd");
        createFreeStyleProject("Efgh");
        WebClient wc = new WebClient();

        // Create a new nested view
        HtmlForm form = wc.goTo("newView").getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("test-nest");
        form.getInputByValue("hudson.plugins.nested_view.NestedView").setChecked(true);
        submit(form);
        // Add some subviews
        form = wc.goTo("view/test-nest/newView").getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("subview");
        form.getInputByValue("hudson.model.ListView").setChecked(true);
        form = submit(form).getFormByName("viewConfig");
        form.getInputByName("useincluderegex").setChecked(true);
        form.getInputByName("includeRegex").setValueAttribute("E.*");
        submit(form);
        form = wc.goTo("view/test-nest/newView").getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("subnest");
        form.getInputByValue("hudson.plugins.nested_view.NestedView").setChecked(true);
        submit(form);
        form = wc.goTo("view/test-nest/newView").getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("suball");
        form.getInputByValue("hudson.model.AllView").setChecked(true);
        submit(form);
        // Verify links to subviews
        HtmlPage page = wc.goTo("view/test-nest/");
        assertNotNull(page.getAnchorByHref("/view/test-nest/view/subview/"));
        assertNotNull(page.getAnchorByHref("/view/test-nest/view/subnest/"));
        assertNotNull(page.getAnchorByHref("/view/test-nest/view/suball/"));
        // Now set a default subview
        form = wc.goTo("view/test-nest/configure").getFormByName("viewConfig");
        List<HtmlOption> options = form.getSelectByName("defaultView").getOptions();
        assertEquals("", options.get(0).getValueAttribute());
        assertEquals("suball", options.get(1).getValueAttribute());
        assertEquals("subview", options.get(2).getValueAttribute());
        // "None" and 2 views in alphabetical order; subnest should not be in list
        assertEquals(3, options.size());
        options.get(1).setSelected(true);
        submit(form);
        // Verify redirect to default subview
        page = wc.goTo("view/test-nest/");
        assertNotNull(page.getAnchorByHref("job/Efgh/"));
        // Verify link to add a subview for empty nested view
        page = wc.goTo("view/test-nest/view/subnest/");
        assertNotNull(page.getAnchorByHref("/view/test-nest/view/subnest/newView"));
    }

    public void testGetWorstResult() throws Exception {
        NestedView view = new NestedView("test");
        view.setOwner(hudson);
        assertSame(null, NestedView.getWorstResult(view));    // Empty
        view.addView(new AllView("foo", view));
        assertSame(null, NestedView.getWorstResult(view));    // Empty
        FreeStyleProject p = createFreeStyleProject();
        assertSame(null, NestedView.getWorstResult(view));    // Job not yet run
        assertBuildStatusSuccess(p.scheduleBuild2(0, new UserCause()).get());
        assertSame(SUCCESS, NestedView.getWorstResult(view));    // Job ran ok
        FreeStyleProject bad = createFreeStyleProject();
        bad.getBuildersList().add(new FailureBuilder());
        assertSame(SUCCESS, NestedView.getWorstResult(view));    // New job not yet run
        assertBuildStatus(FAILURE, bad.scheduleBuild2(0, new UserCause()).get());
        assertSame(FAILURE, NestedView.getWorstResult(view));    // Job failed
        bad.disable();
        assertSame(SUCCESS, NestedView.getWorstResult(view));    // Ignore disabled job
    }

    public void testStatusOfEmptyNest() throws Exception {
        NestedView parent = new NestedView("parent");
        parent.setOwner(hudson);
        NestedView child = new NestedView("child");
        parent.addView(child);
        assertSame(null, NestedView.getWorstResult(child));     // Empty
        assertSame(null, NestedView.getWorstResult(parent));    // contains Empty child only
    }

    public void testDoViewExistsCheck() {
        NestedView view = new NestedView("test");
        view.setOwner(hudson);
        view.addView(new ListView("foo", view));
        assertSame(OK, view.doViewExistsCheck(null).kind);
        assertSame(OK, view.doViewExistsCheck("").kind);
        assertSame(OK, view.doViewExistsCheck("bar").kind);
        assertSame(ERROR, view.doViewExistsCheck("foo").kind);
    }
}
