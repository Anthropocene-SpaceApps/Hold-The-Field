package org.anthropocene.htf.sim;

/** Something that happened on day i. type: watch | warning | flood | loss | harvest | action. */
public record Event(int i, String date, String type, String text) {}
