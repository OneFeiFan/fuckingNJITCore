package com.feifan.fuckingnjit.Model;


import java.util.ArrayList;

public class Time {
    private int weekday = 0;
    private ArrayList<Integer> courseTime = new ArrayList<>();
    private int week = 0;

    public Time() {

    }

    public Time(int weekday, ArrayList<Integer> courseTime, int week) {
        this.weekday = weekday;
        this.courseTime = courseTime;
        this.week = week;
    }

    public int getWeekday() {
        return weekday;
    }

    public void setWeekday(int weekday) {
        this.weekday = weekday;
    }

    public ArrayList<Integer> getCourseTime() {
        return courseTime;
    }

    public void setCourseTime(ArrayList<Integer> courseTime) {
        this.courseTime = courseTime;
    }

    public int getWeek() {
        return week;
    }

    public void setWeek(int week) {
        this.week = week;
    }
}