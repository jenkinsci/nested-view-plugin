package hudson.plugins.nested_view.search;

public class LinkableCandidate {
    private final String plainText;
    private final boolean canLink;
    private final String prefix;
    private final String suffix;
    private final String url;

    public LinkableCandidate(String prefix, String link, String suffix, String url) {
        this.plainText = link;
        this.canLink = true;
        this.prefix = prefix;
        this.suffix = suffix;
        this.url = url;
    }

    public LinkableCandidate(String plainText) {
        this.plainText = plainText;
        this.canLink = false;
        this.prefix = null;
        this.suffix = null;
        this.url = null;
    }

    public String getPlainText() {
        return plainText;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public String getUrl() {
        return url;
    }

    public boolean isCanLink() {
        return canLink;
    }

}
