package hudson.plugins.nested_view.search;

import java.util.ArrayList;
import java.util.List;

public class LinkableCandidate {
    private final String plainText;
    private final boolean canLink;
    private final String prefix;
    private final String suffix;
    private final String url;
    private final List<LinkableCandidate> sublinks;

    public LinkableCandidate(String prefix, String link, String suffix, String url, List<LinkableCandidate> sublinks) {
        this.plainText = link;
        this.canLink = true;
        this.prefix = prefix;
        this.suffix = suffix;
        this.url = url;
        this.sublinks = sublinks;
    }

    public LinkableCandidate(String plainText) {
        this.plainText = plainText;
        this.canLink = false;
        this.prefix = null;
        this.suffix = null;
        this.url = null;
        this.sublinks = new ArrayList<LinkableCandidate>(0);
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

    public List<LinkableCandidate> getSublinks() {
        return sublinks;
    }
}
