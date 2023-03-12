package hudson.plugins.nested_view.search;

import hudson.model.AbstractProject;
import hudson.model.View;
import hudson.plugins.nested_view.NestedView;
import jenkins.model.Jenkins;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class NamableWithClass {
    private final String name;
    private final String fullPath;
    private Object item;

    public NamableWithClass(Object item, String name, String fullPath) {
        this.item = item;
        this.name = name;
        this.fullPath = fullPath;
    }

    public String getName() {
        return name;
    }

    public String getFullPath() {
        return fullPath;
    }

    public String getUsefulName() {
        if (item instanceof AbstractProject) {
            return name;
        } else {
            if (item instanceof NestedView) {
                return fullPath + "/";
            } else {
                return fullPath;
            }
        }
    }

    public String getUrl() {
        String rootUrl = Jenkins.get().getRootUrl();
        if (rootUrl.endsWith("/")) {
            rootUrl = rootUrl.substring(0, rootUrl.length() - 1);
        }
        if (item instanceof AbstractProject) {
            return rootUrl + "/job/" + name;
        } else {
            return rootUrl + getFullPath().replace("/", "/view/");
        }
    }

    public boolean matches(Query query, final Set<String> matched) {
        if (query.isInvert()) {
            return !matchesImpl(query, matched);
        } else {
            return matchesImpl(query, matched);
        }
    }

    private boolean matchesImpl(Query query, final Set<String> matched) {
        String nameOrPath = getFullPath();
        if (query.getPart().equals("p")) {
            nameOrPath = getName();
        }
        boolean clazzPass = false;
        if (query.getWhere().contains("j") && (item instanceof AbstractProject)) {
            clazzPass = true;
        }
        if (query.getWhere().contains("w") && (item instanceof View || item instanceof NestedView)) {
            clazzPass = true;
        }
        if (query.getWhere().contains("n") && (item instanceof NestedView)) {
            clazzPass = true;
        }
        if (query.getWhere().contains("v") && (item instanceof View) && !(item instanceof NestedView)) {
            clazzPass = true;
        }
        if (!clazzPass) {
            return false;
        }
        if (query.getBool().equals("a")) {
            String[] parts = query.getWithoutArgumentsSplit();
            boolean r = true;
            for (String part : parts) {
                if (!matchSingle(nameOrPath, part, query)) {
                    r = false;
                } else {
                    matched.add(part);
                }
            }
            return r;
        } else if (query.getBool().equals("o")) {
            String[] parts = query.getWithoutArgumentsSplit();
            boolean r = false;
            for (String part : parts) {
                if (matchSingle(nameOrPath, part, query)) {
                    r = true;
                    matched.add(part);
                }
            }
            return r;
        } else {
            if (matchSingle(nameOrPath, query.getWithoutArguments(), query)) {
                matched.add(query.getWithoutArguments());
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean matchSingle(String nameOrPath, String queryOrPart, Query query) {
        return matchSingle(nameOrPath, queryOrPart, query.getHow());
    }

    public static boolean matchSingle(String nameOrPath, String queryOrPart, String how) {
        if (how.equals("s")) {
            return nameOrPath.startsWith(queryOrPart);
        } else if (how.equals("e")) {
            return nameOrPath.endsWith(queryOrPart);
        } else if (how.equals("r")) {
            return nameOrPath.matches(queryOrPart);
        } else if (how.equals("R")) {
            return nameOrPath.matches(".*" + queryOrPart + ".*");
        } else if (how.equals("q")) {
            return nameOrPath.equalsIgnoreCase(queryOrPart);
        } else if (how.equals("Q")) {
            return nameOrPath.equals(queryOrPart);
        } else {
            return nameOrPath.contains(queryOrPart);
        }
    }

    public Optional<AbstractProject> getProject() {
        if (item instanceof AbstractProject) {
            return Optional.of((AbstractProject) item);
        } else {
            return Optional.empty();
        }
    }
}
