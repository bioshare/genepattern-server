package org.genepattern.drm;

import java.util.concurrent.TimeUnit;

/**
 * Generic representation of the amount of time, the wall clock limit, for 
 * a job submitted to the queue.
 * 
 * @author pcarr
 *
 */
public class Walltime {
    public static Walltime NOT_SET=new Walltime(0L, TimeUnit.SECONDS);

    private String asString;
    private long duration;
    private TimeUnit timeUnit;
    
    private Walltime(long duration, final TimeUnit timeUnit) {
        this.duration=duration;
        this.timeUnit=timeUnit;
    }
    
    public long getDuration() {
        return duration;
    }
    public TimeUnit getTimeUnit() {
        return timeUnit;
    }
    
    public String toString() {
        if (asString != null) {
            return asString;
        }
        if (duration==0) {
            return "00:00";
        }
        return ""+duration;
    }

    /**
     * Parse a string in 'days-hh:mm:ss' format, where hours are on a 24 hour scale.
     * @param val
     * @return
     */
    static Walltime fromString(final String in) throws Exception {
        if (in==null) {
            //null in, null out
            return null;
        }
        final String val=in.trim();
        if (val.length()==0) {
            //special-case: empty
            return NOT_SET;
        }
        String hoursPart=null;
        long numSeconds=0;
        final String[] step1=val.split("-");
        if (step1.length==0) {
            //special-case: not set
            return NOT_SET;
        }
        else if (step1.length==2) {  // has a day part
            final int numDays=Integer.parseInt(step1[0]);
            numSeconds += TimeUnit.DAYS.toSeconds(numDays);
            hoursPart = step1[1];
        }
        else if (step1.length==1) {
            hoursPart = step1[0];
        }
        else {
            throw new Exception("Invalid value: '"+val+"', expecting 'days-hh:mm:ss'");
        }
        
        String[] step2=hoursPart.split(":");
        String hh=null;
        String mm=null;
        String ss=null;
        if (step2.length==3) {
            hh=step2[0];
            mm=step2[1];
            ss=step2[2];
        }
        else if (step2.length==2) {
            mm=step2[0];
            ss=step2[1];
        }
        else if (step2.length==1) {
            ss=step2[0];
        }
        
        if (hh != null) {
            numSeconds += TimeUnit.HOURS.toSeconds( Integer.parseInt(hh) );
        }
        if (mm != null) {
            numSeconds += TimeUnit.MINUTES.toSeconds( Integer.parseInt(mm) );
        }
        numSeconds += Integer.parseInt(ss);
        Walltime w=new Walltime(numSeconds, TimeUnit.SECONDS);
        w.asString = val;
        return w;
    }
    
}
