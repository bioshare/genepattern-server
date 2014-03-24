package org.genepattern.server.genepattern;

import org.genepattern.drm.JobRunner;
import org.genepattern.drm.Memory;
import org.genepattern.server.config.GpContext;
import static org.genepattern.util.GPConstants.COMMAND_LINE;
import org.genepattern.webservice.TaskInfo;
import org.genepattern.webservice.TaskInfoAttributes;

/**
 * Helper class for java modules, to set the java max memory based on the GP server configuration setting 'drm.memory'.
 * 
 * This is called after all other command line substitutions have happened and simply replaces all
 * occurrences of -Xmx<originalValue> with -Xmx<customValue> if a custom value has been specified.
 * 
 * Note: this is only applied to java modules. Determined by the command line starting with the '<java>'
 * substitution parameter. 
 * 
 * @see JobRunner#PROP_MEMORY
 * @author pcarr
 *
 */
public class CustomXmxFlags {
    public static String replaceXmx(final Memory mem, final String arg) {
        if (arg==null) {
            return arg;
        }
        final int i0=arg.indexOf("-Xmx");
        if (i0<0) {
            //no match
            return arg;
        }
        final int i1=arg.indexOf(" ", i0);
        if (i1<0) {
            return arg.substring(0, i0) + "-Xmx"+mem.toXmx();
        }        
        return arg.substring(0, i0) + "-Xmx"+mem.toXmx() + arg.substring(i1);
    }
    
    public static String[] addOrReplaceXmxFlag(final GpContext jobContext, final Memory mem, final String[] cmdLineArgs) {
        if (mem==null) {
            return cmdLineArgs;
        }
        
        if (!isJavaCmd(jobContext)) {
            return cmdLineArgs;
        }
        
        //case 1: replace existing -Xmx flag
        boolean hasXmx=false;
        int idx=0;
        for(final String arg : cmdLineArgs) {
            if (arg.contains("-Xmx")) {
                cmdLineArgs[idx]=replaceXmx(mem,arg);
                hasXmx=true;
                break;
            }
            ++idx;
        }
        if (hasXmx) {
            return cmdLineArgs;
        }
        else  {
            //case 2: add -Xmx flag to command line
            String[] rval=new String[ 1+cmdLineArgs.length ];
            rval[0]=cmdLineArgs[0];
            rval[1]="-Xmx"+mem.toXmx();
            System.arraycopy(cmdLineArgs, 1, rval, 2, cmdLineArgs.length);
            return rval;
        }
    }
    
    private static boolean isJavaCmd(final GpContext jobContext) {
        return isJavaCmd(jobContext.getTaskInfo());
    }
    
    private static boolean isJavaCmd(final TaskInfo taskInfo) {
        if (taskInfo==null) {
            return false;
        }
        final TaskInfoAttributes taskInfoAttributes = taskInfo.giveTaskInfoAttributes();
        final String cmdLine=taskInfoAttributes.get(COMMAND_LINE);
        if (cmdLine==null) {
            return false;
        }
        return cmdLine.startsWith("<java>");
    }
    

}