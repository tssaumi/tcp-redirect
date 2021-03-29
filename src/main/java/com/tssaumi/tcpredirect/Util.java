package com.tssaumi.tcpredirect;

import java.text.DecimalFormat;

public class Util {
    /**
     * Elapsed time in micro-second.<BR>
     * Return format: #,##0us<BR>
     * Example: "12,345us"
     */
    public static String elapsedTimeUs(long startTimeNs, long endTimeNs) {
        DecimalFormat df = new DecimalFormat("#,##0");
        StringBuilder sb = new StringBuilder();
        long elapsed = (endTimeNs - startTimeNs) / 1_000L;
        sb.append(df.format(elapsed));
        sb.append("us");
        return sb.toString();
    }
}
