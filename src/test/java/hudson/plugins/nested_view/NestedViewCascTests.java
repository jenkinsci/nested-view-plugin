package hudson.plugins.nested_view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.ListView;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class NestedViewCascTests {

  private JenkinsConfiguredWithCodeRule rule;

  @BeforeEach
  void setUp(JenkinsConfiguredWithCodeRule rule) {
    this.rule = rule;
  }

  @ConfiguredWithCode("/hudson/plugins/nested_view/casc.yaml")
  @Test
  void configurationAsCodeCanSetViews() throws Exception {
    NestedView view = (NestedView) rule.jenkins.getView("nestedView");
    assertNotNull(view);
    assertEquals("nestedView", view.getViewName());
    assertEquals(1, view.getViews().size());

    ListView subview = (ListView) view.getView("listView");
    assertNotNull(subview);
    assertEquals("listView", subview.getViewName());
    assertEquals(view, subview.getOwner());

    String exported = rule.exportToString(true);
    List<String> expectedLines = List.of(
        ">> other unrelated configuration >>",
        "  views:",
        "  - nested:",
        "      name: \"nestedView\"",
        "      statusColumns:",
        "      - \"weather\"",
        "      views:",
        "      - list:",
        "          columns:",
        "          - \"status\"",
        "          - \"jobName\"",
        "          - \"lastSuccess\"",
        "          - \"lastFailure\"",
        "          - \"lastDuration\"",
        "          - \"buildButton\"",
        "          name: \"listView\"",
        ">> other unrelated configuration >>");
    assertLinesMatch(expectedLines, exported.lines().toList());
  }

  @ConfiguredWithCode("/hudson/plugins/nested_view/casc-nested.yaml")
  @Test
  void configurationAsCodeCanSetNestedViews() throws Exception {
    NestedView parent = (NestedView) rule.jenkins.getView("parent");
    assertNotNull(parent);
    assertEquals("parent", parent.getViewName());
    assertEquals(1, parent.getViews().size());

    NestedView child = (NestedView) parent.getView("child");
    assertNotNull(child);
    assertEquals("child", child.getViewName());
    assertEquals(parent, child.getOwner());

    ListView grandchild = (ListView) child.getView("grandchild");
    assertNotNull(grandchild);
    assertEquals("grandchild", grandchild.getViewName());
    assertEquals(child, grandchild.getOwner());

    String exported = rule.exportToString(true);
    List<String> expectedLines = List.of(
        ">> other unrelated configuration >>",
        "  views:",
        "  - nested:",
        "      name: \"parent\"",
        "      views:",
        "      - nested:",
        "          name: \"child\"",
        "          views:",
        "          - list:",
        "              columns:",
        "              - \"status\"",
        "              - \"jobName\"",
        "              - \"lastSuccess\"",
        "              - \"lastFailure\"",
        "              - \"lastDuration\"",
        "              - \"buildButton\"",
        "              name: \"grandchild\"",
        ">> other unrelated configuration >>");
    assertLinesMatch(expectedLines, exported.lines().toList());
  }
}
