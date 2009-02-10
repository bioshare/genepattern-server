package org.genepattern.server.genepattern;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.Properties;

import org.genepattern.server.webservice.server.DirectoryManager;
import org.genepattern.util.GPConstants;
import org.genepattern.visualizer.RunVisualizerConstants;
import org.genepattern.webservice.JobInfo;
import org.genepattern.webservice.ParameterInfo;
import org.genepattern.webservice.TaskInfoAttributes;

public class RunVisualizer {
    private String userID = null;
    private JobInfo jobInfo = null;
    //private TaskInfo taskInfo = null; //the visualizer task
    private TaskInfoAttributes taskInfoAttributes = null;
    private String contextPath = "/gp";
    private String javaFlags = null;
    private Properties requestParameters = new Properties();

//
//     session="false" language="Java" %><%
//      response.setHeader("Cache-Control", "no-store"); // HTTP 1.1 cache control
//      response.setHeader("Pragma", "no-cache");        // HTTP 1.0 cache control
//      response.setDateHeader("Expires", 0);
    public RunVisualizer() {
    }
    
    public void setJobInfo(JobInfo ji) {
        this.jobInfo = ji;
    }
    
//    public void setTaskInfo(TaskInfo ti) {
//        this.taskInfo = ti;
//    }
    
    public void setTaskInfoAttributes(TaskInfoAttributes tia) {
        this.taskInfoAttributes = tia;
    }

    /**
     * <code>request.getContextPath()</code>
     * @param str
     */
    public void setContextPath(String str) {
        this.contextPath = str;
    }
    
    public void setJavaFlags(String str) {
        this.javaFlags = str;
    }
    
    public void setRequestParameters(Properties props) {
        this.requestParameters.putAll(props);
    }

    public void writeVisualizer(Writer out) throws IOException, UnsupportedEncodingException, MalformedURLException {
        String name = jobInfo.getTaskName();
        ParameterInfo[] parameterInfoArray = jobInfo.getParameterInfoArray();

        if(javaFlags==null) {
            javaFlags = System.getProperty(RunVisualizerConstants.JAVA_FLAGS_VALUE);
        }

        StringWriter app = new StringWriter();
        app.append("<applet code=\"" 
                + org.genepattern.visualizer.RunVisualizerApplet.class.getName() 
                + "\" archive=\"runVisualizer.jar,commons-httpclient.jar,commons-codec-1.3.jar\" codebase=\"/gp/downloads\" width=\"1\" height=\"1\" alt=\"Your browser can not run applets\">");

        app.append("<param name=\"" + RunVisualizerConstants.NAME + "\" value=\"" + URLEncoder.encode(name, "UTF-8") + "\" >");
        app.append("<param name=\"" + RunVisualizerConstants.OS + "\" value=\"" + URLEncoder.encode(taskInfoAttributes.get(GPConstants.OS), "UTF-8") + "\">");
        app.append("<param name=\"" + RunVisualizerConstants.CPU_TYPE + "\" value=\"" + URLEncoder.encode(taskInfoAttributes.get(GPConstants.CPU_TYPE), "UTF-8") + "\">");
        app.append("<param name=\"" + RunVisualizerConstants.JAVA_FLAGS_VALUE + "\" value=\"" + URLEncoder.encode(javaFlags, "UTF-8") + "\">");
        app.append("<param name=\"" + RunVisualizerConstants.CONTEXT_PATH + "\" value=\"" + URLEncoder.encode(contextPath, "UTF-8") + "\">");
        StringBuffer paramNameList = new StringBuffer();
        for (int i = 0; i < parameterInfoArray.length; i++) {
            if (i > 0) {
                paramNameList.append(",");
            }
            paramNameList.append(parameterInfoArray[i].getName());
        }
        //app.append("<param name=\"" + RunVisualizerConstants.PARAM_NAMES + "\" value=\"" + URLEncoder.encode(paramNameList.toString(), "UTF-8") + "\" >");
        app.append("<param name=\"" + RunVisualizerConstants.PARAM_NAMES + "\" value=\"" + paramNameList.toString() + "\" >");

        for (int i = 0; i < parameterInfoArray.length; i++) {
            String paramName = parameterInfoArray[i].getName();
            String paramValue = (String) parameterInfoArray[i].getValue();
//          String value = params.getProperty(paramName);
            if(paramValue != null) {
                paramValue = paramValue.replace("\\", "\\\\");
            } 
            else {
                paramValue = "";
            }
            //app.append("<param name=\"" + paramName + "\" value=\"" + URLEncoder.encode(paramValue, "UTF-8") + "\">");
            app.append("<param name=\"" + paramName + "\" value=\"" + paramValue + "\">");
        }

        StringBuffer vis = new StringBuffer();

        int numToDownload = 0;
        for (int i = 0; i < parameterInfoArray.length; i++) {
            String paramName = parameterInfoArray[i].getName();
            String paramValue = parameterInfoArray[i].getValue();
            //if (parameterInfoArray[i].isInputFile() && params.getProperty(paramName) != null) {
            if (parameterInfoArray[i].isInputFile() && paramValue != null) {
                try {
                    new java.net.URL(paramValue);
                    // note that this parameter is a URL that must be downloaded by adding it to the CSV list for the applet
                    if (numToDownload > 0) {
                        vis.append(",");
                    }
                    vis.append(parameterInfoArray[i].getName());
                    numToDownload++;
                }
                catch(Exception x){
                }
            }
        }
        app.append("<param name=\"" + RunVisualizerConstants.DOWNLOAD_FILES + "\" value=\"" + URLEncoder.encode(vis.toString(), "UTF-8") + "\">");
        app.append("<param name=\"" + RunVisualizerConstants.COMMAND_LINE + "\" value=\"" + URLEncoder.encode(taskInfoAttributes.get(GPConstants.COMMAND_LINE), "UTF-8") + "\">");
        app.append("<param name=\"" + RunVisualizerConstants.DEBUG + "\" value=\"1\">");

        StringBuffer fileNamesBuf = new StringBuffer();
        //String lsid = (String) taskInfoAttributes.get(GPConstants.LSID);
        String lsid = jobInfo.getTaskLSID();
        String libdir = DirectoryManager.getTaskLibDir(null, lsid, userID);
        File[] supportFiles = new File(libdir).listFiles();
        for (int i = 0; i < supportFiles.length; i++) {
            if (i > 0) fileNamesBuf.append(",");
            fileNamesBuf.append(supportFiles[i].getName());
        }
        app.append("<param name=\"" + RunVisualizerConstants.SUPPORT_FILE_NAMES + "\" value=\"" + URLEncoder.encode(fileNamesBuf.toString(), "UTF-8") + "\" >");

        StringBuffer fileDatesBuf = new StringBuffer();
        for (int i = 0; i < supportFiles.length; i++) {
            if (i > 0) {
                fileDatesBuf.append(",");
            }
            fileDatesBuf.append(supportFiles[i].lastModified());
        }
        app.append("<param name=\"" + RunVisualizerConstants.SUPPORT_FILE_DATES + "\" value=\"" + URLEncoder.encode(fileDatesBuf.toString(), "UTF-8") + "\" >");
        app.append("<param name=\"" + RunVisualizerConstants.LSID + "\" value=\"" + URLEncoder.encode(lsid, "UTF-8") + "\" >");

        String js = "<SCRIPT LANGUAGE=\"JavaScript\">\ndocument.writeln('";
        js += app.toString();
        js += "');\n";
        //TODO: do I need the cookie?
        //js += ("document.writeln('<PARAM name=\\\"browserCookie\\\" value=\\\" + document.cookie + \"\\\">');\n");
        js += "document.writeln(\"<PARAM name=\\\"browserCookie\\\" value=\\\"\" + document.cookie + \"\\\">\");";
        js += "document.writeln('</applet>');\n";
        js += "</SCRIPT>";
        
        out.write(js);
    }
}
