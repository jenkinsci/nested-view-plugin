/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Alan Harder
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
import java.util.List;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Test interaction of nested-view plugin with Hudson core.
 * @author Alan.Harder@sun.com
 */
public class NestedViewTest extends HudsonTestCase {

    public void test() throws Exception {
        createFreeStyleProject("Abcd");
        createFreeStyleProject("Efgh");
        WebClient wc = new WebClient();

        // Create a new nested view
        HtmlForm form = wc.goTo("newView").getFormByName("createView");
        form.getInputByName("name").setValueAttribute("test-nest");
        form.getInputByValue("hudson.plugins.nested_view.NestedView").setChecked(true);
        submit(form);
        // Add some subviews
        form = wc.goTo("view/test-nest/newView").getFormByName("createView");
        form.getInputByName("name").setValueAttribute("subview");
        form.getInputByValue("hudson.model.ListView").setChecked(true);
        form = submit(form).getFormByName("viewConfig");
        form.getInputByName("useincluderegex").setChecked(true);
        form.getInputByName("includeRegex").setValueAttribute("E.*");
        submit(form);
        form = wc.goTo("view/test-nest/newView").getFormByName("createView");
        form.getInputByName("name").setValueAttribute("subnest");
        form.getInputByValue("hudson.plugins.nested_view.NestedView").setChecked(true);
        submit(form);
        form = wc.goTo("view/test-nest/newView").getFormByName("createView");
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
        assertEquals("subview", options.get(1).getValueAttribute());
        assertEquals("suball", options.get(2).getValueAttribute());
        assertEquals(3, options.size());  // "None" and 2 views; subnest should not be in list
        options.get(1).setSelected(true);
        submit(form);
        // Verify redirect to default subview
        page = wc.goTo("view/test-nest/");
        assertNotNull(page.getAnchorByHref("job/Efgh/"));
        // Verify link to add a subview for empty nested view
        page = wc.goTo("view/test-nest/view/subnest/");
        assertNotNull(page.getAnchorByHref("newView"));
    }
}
