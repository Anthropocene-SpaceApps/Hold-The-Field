package org.anthropocene.htf.sim;

import java.util.List;

/** A precomputed season file (see data/*.json). */
public final class Season {
    public record LatLon(double lat, double lon) {}
    public record Points(LatLon farm, LatLon upstream) {}

    public String id;
    public String region;
    public String season;
    public boolean sample;
    public String note;
    public String source;
    public Points points;
    public List<Day> days;

    public int length() { return days.size(); }
    public Day day(int i) { return days.get(i); }
}
