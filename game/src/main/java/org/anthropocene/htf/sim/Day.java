package org.anthropocene.htf.sim;

/** One day of NASA POWER data. */
public record Day(String date, double rainUp, double rainFarm, double tmax, double soil) {}
