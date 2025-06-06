package com.example.myapplication;

public class Currency {
    private int currencyCodeA;
    private int currencyCodeB;
    private long date;
    private double rateBuy;
    private double rateSell;
    private double rateCross;

    public int getCurrencyCodeA() {
        return currencyCodeA;
    }

    public int getCurrencyCodeB() {
        return currencyCodeB;
    }

    public long getDate() {
        return date;
    }

    public double getRateBuy() {
        return rateBuy;
    }

    public double getRateSell() {
        return rateSell;
    }

    public double getRateCross() {
        return rateCross;
    }
}