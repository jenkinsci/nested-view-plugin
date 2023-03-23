package hudson.plugins.nested_view.search;

import hudson.plugins.nested_view.NestedViewsSearch;
import hudson.plugins.nested_view.NestedViewsSearchFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Query {

    private static final int MIN_LENGTH = 2;

    private final String original;
    private final String withoutArguments;

    private boolean multiline;
    private int searchByNvr = -1;
    private boolean finalFilter;
    private boolean projectInfo;
    private int stats = -1;
    private boolean statsTable = false;
    private int builds = -1;
    private int last = -1;
    private int sort = 1;

    private String yymmddhhmm = null;

    public String getOriginal() {
        return original;
    }

    public String getWhere() {
        return where;
    }

    public String getBool() {
        return bool;
    }

    public String getPart() {
        return part;
    }

    public int getSort() {
        return sort;
    }

    private String where = "vnj"; //v,n,j
    private String how = "c"; //c,s,e,r,R,q,Q
    private String bool = ""; //a,o,""
    private String part = "f"; //p,f
    private boolean invert = false;

    public Query(boolean search, String ooriginal) {
        if (search) {
            NestedViewsSearchFactory.resetTmpSkip();
        }
        this.original = ooriginal.trim();
        String query = null;
        try {
            Pattern getQuery = Pattern.compile("-.*:");
            Matcher m = getQuery.matcher(original);
            m.find();
            query = m.group();
        } catch (Exception ex) {
            //no query found is ok
        }
        if (query != null) {
            withoutArguments = original.replace(query, "").trim();
            if (withoutArguments.contains(".*")) {
                how = "r";
            }
            if (query.contains("X") && search) {
                String l = query.replaceAll(".*X", "");
                int n = 1;
                if (l.length() > 0) {
                    try {
                        l = l.substring(0, 1);
                        n = Integer.parseInt(l);
                    } catch (Exception ex) {
                        //ok
                    }
                }
                NestedViewsSearchFactory.setTmpSkip(n);
            }
            if ((query.contains("D") || query.contains("d"))) {
                if (search) {
                    if (query.contains("D")) {
                        finalFilter = true;
                        searchByNvr = getNumber(query, "D", 1);
                        ;
                    } else {
                        searchByNvr = getNumber(query, "d", 1);
                        ;
                    }
                }
                bool = "o";
                if (builds <= 0) { //maybe it was already set
                    builds = 10;
                }
            }

            if (query.contains("m") && search) {
                multiline = true;
            }
            if (query.contains("P") && search) {
                projectInfo = true;
            }
            if (query.contains("S") && search) {
                if (query.contains("SS")) {
                    statsTable = true;
                }
                stats = getNumber(query, "S", 10);
            }
            if (query.contains("B") && search) {
                builds = getNumber(query, "B", 10);
            }
            if (query.contains("L") && search) {
                last = getNumber(query, "L", 0);
                if (last == 0) { //it can be set also by user
                    last = 1234567;
                }
            } else {
                last = -1;
            }
            if (query.contains("T") && search) {
                long time = getLongNumber(query, "T", 0);
                String stime = "" + time;
                if (stime.length() == 10) {
                    yymmddhhmm = stime;
                } else {
                    NestedViewsSearch.LOGGER.log(Level.WARNING, "T have invlaid argument - " + stime + "; is not 10 chars of yymmddhhmm long");
                }
            }
            if (query.contains("t")) {
                sort = getNumber(query, "t", 1);
            }
            if (query.contains("j") || query.contains("v") || query.contains("n") || query.contains("w")) {
                where = "";
            }
            if (query.contains("j")) {
                where += "j";
            }
            if (query.contains("v")) {
                where += "v";
            }
            if (query.contains("n")) {
                where += "n";
            }
            if (query.contains("w")) {
                where += "vn";
            }
            if (query.contains("c")) {
                how = "c";
            }
            if (query.contains("e")) {
                how = "e";
            }
            if (query.contains("s")) {
                how = "s";
            }
            if (query.contains("r")) {
                how = "r";
            }
            if (query.contains("R")) {
                how = "R";
            }
            if (query.contains("q")) {
                how = "q";
            }
            if (query.contains("Q")) {
                how = "Q";
            }
            if (query.contains("a")) {
                bool = "a";
            }
            if (query.contains("o")) {
                bool = "o";
            }
            if (query.contains("f")) {
                part = "f";
            }
            if (query.contains("p")) {
                part = "p";
            }
            if (query.contains("!")) {
                invert = true;
            }
        } else {
            withoutArguments = original;
            if (withoutArguments.contains(".*")) {
                how = "r";
            }
        }
    }

    public String getWithoutArguments() {
        return withoutArguments;
    }

    public String[] getWithoutArgumentsSplit() {
        return withoutArguments.split("\\s+");
    }


    private int getNumber(String query, String switcher, int n) {
        return (int) getLongNumber(query, switcher, n);
    }

    private long getLongNumber(String query, String switcher, long n) {
        String l = query.replaceAll(".*" + switcher, "");
        l = l.replaceAll("[^0-9].*", "");
        try {
            n = Long.parseLong(l);
        } catch (Exception ex) {
            NestedViewsSearch.LOGGER.log(Level.WARNING, "no reasonabl enumber from " + l, ex);
        }
        return n;
    }

    public boolean isMultiline() {
        return multiline;
    }

    public boolean isProjectInfo() {
        return projectInfo;
    }

    public int getStats() {
        return stats;
    }

    public boolean isStatsTable() {
        return statsTable;
    }

    public int getBuilds() {
        return builds;
    }

    public int getLast() {
        return last;
    }

    public String getHow() {
        return how;
    }

    public int isSearchByNvr() {
        return searchByNvr;
    }

    public boolean isFinalFilter() {
        return finalFilter;
    }

    public boolean isInvert() {
        return invert;
    }

    public Date getTimeLimit() {
        try {
            if (yymmddhhmm == null) {
                return null;
            }
            return new SimpleDateFormat("yyMMddHHmm").parse(yymmddhhmm);
        } catch (Exception ex) {
            NestedViewsSearch.LOGGER.log(Level.WARNING, ex.toString(), ex);
            return null;
        }
    }

    public boolean isNonTrivial(boolean suggesting) {
        final String loriginal;
        if (original == null) {
            loriginal = "";
        } else {
            loriginal = original.trim();
        }
        final String lwithout;
        if (withoutArguments == null) {
            lwithout = "";
        } else {
            lwithout = withoutArguments.trim();
        }
        return !loriginal.equals(".*")
                && loriginal.length() >= MIN_LENGTH
                && !lwithout.equals(".*")
                && lwithout.length() >= MIN_LENGTH;
    }
}
