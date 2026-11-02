package com.daara.internatadmin.common;

public class MatriculeGenerator {
    public static String format(String prefix, int year, long sequence) {
        return String.format("%s-%d-%04d", prefix, year, sequence);
    }
}