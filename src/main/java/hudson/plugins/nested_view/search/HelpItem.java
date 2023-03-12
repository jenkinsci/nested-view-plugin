package hudson.plugins.nested_view.search;

public class HelpItem {
    private final String key;
    private final String description;

    public HelpItem(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }
}
