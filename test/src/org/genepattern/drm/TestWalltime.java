package org.genepattern.drm;

import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;


public class TestWalltime {
    @Test
    public void testFullSpec() throws Exception {
        Walltime wt=Walltime.fromString("7-12:30:30");
        Assert.assertEquals("duration", 649830, wt.getDuration());
        Assert.assertEquals("units", TimeUnit.SECONDS, wt.getTimeUnit());
        Assert.assertEquals("toString()", "7-12:30:30", wt.toString());
    }

    @Test
    public void testPbsSpec() throws Exception {
        Walltime wt=Walltime.fromString("12:15:00");
        Assert.assertEquals("duration", 44100, wt.getDuration());
        Assert.assertEquals("units", TimeUnit.SECONDS, wt.getTimeUnit());        
        Assert.assertEquals("toString()", "12:15:00", wt.toString());
    }

    @Test
    public void testPbsSpecTrim() throws Exception {
        Walltime wt=Walltime.fromString(" 12:15:00 ");
        Assert.assertEquals("duration", 44100, wt.getDuration());
        Assert.assertEquals("units", TimeUnit.SECONDS, wt.getTimeUnit());        
        Assert.assertEquals("toString()", "12:15:00", wt.toString());
    }

    @Test
    public void testSevenDays() throws Exception {
        //7 days
        Walltime wt=Walltime.fromString("7-00:00:00");
        Assert.assertEquals("duration", 604800, wt.getDuration());
        Assert.assertEquals("units", TimeUnit.SECONDS, wt.getTimeUnit());
        Assert.assertEquals("toString()", "7-00:00:00", wt.toString());
    }
    
    @Test
    public void testJustSeconds() throws Exception {
        Walltime wt=Walltime.fromString("3600");
        Assert.assertEquals("duration", 3600, wt.getDuration());
        Assert.assertEquals("units", TimeUnit.SECONDS, wt.getTimeUnit());
        Assert.assertEquals("toString()", "3600", wt.toString());
    }
    
    @Test
    public void testMinutesAndSeconds() throws Exception {
        Walltime wt=Walltime.fromString("59:59");
        Assert.assertEquals("duration", 3599, wt.getDuration());
        Assert.assertEquals("units", TimeUnit.SECONDS, wt.getTimeUnit());
        Assert.assertEquals("toString()", "59:59", wt.toString());
    }
    
    @Test
    public void testNullIn() throws Exception {
        Walltime wt=Walltime.fromString(null);
        Assert.assertNull("Expecting null value from parse(null)", wt);
    }
    
    @Test
    public void testEmptyIn() throws Exception {
        Walltime wt=Walltime.fromString("");
        Assert.assertEquals("parse empty string", Walltime.NOT_SET, wt);
        Assert.assertEquals("toString()", "00:00", wt.toString());
    }
    
    //parse errors
    /**
     * should throw an exception when the '-' separator is the prefix.
     * @throws Exception
     */
    @Test(expected=Exception.class)
    public void testMissingDay() throws Exception {
        Walltime.fromString("-500000");
    }
    
    @Test(expected=Exception.class)
    public void testNegativeMinutes() throws Exception {
        Walltime.fromString("00:-60:00");
    }
    
    @Test(expected=Exception.class)
    public void testNegativeDays() throws Exception {
        Walltime t=Walltime.fromString("-7-12:30:30");
        Assert.assertEquals("duration", 649830, t.getDuration());
        Assert.assertEquals("units", TimeUnit.SECONDS, t.getTimeUnit());
    }

}