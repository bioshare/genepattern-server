/*
 The Broad Institute
 SOFTWARE COPYRIGHT NOTICE AGREEMENT
 This software and its documentation are copyright (2003-2006) by the
 Broad Institute/Massachusetts Institute of Technology. All rights are
 reserved.

 This software is supplied without any warranty or guaranteed support
 whatsoever. Neither the Broad Institute nor MIT can be responsible for its
 use, misuse, or functionality.
 */

package org.genepattern.server.webapp.jsf;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.event.ActionEvent;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.myfaces.custom.navmenu.NavigationMenuItem;
import org.genepattern.codegenerator.CodeGeneratorUtil;
import org.genepattern.server.genepattern.GenePatternAnalysisTask;
import org.genepattern.server.user.UserPropKey;
import org.genepattern.server.webservice.server.dao.AdminDAO;
import org.genepattern.server.webservice.server.local.LocalAnalysisClient;
import org.genepattern.util.SemanticUtil;
import org.genepattern.webservice.AnalysisJob;
import org.genepattern.webservice.JobInfo;
import org.genepattern.webservice.ParameterInfo;
import org.genepattern.webservice.TaskInfo;
import org.genepattern.webservice.WebServiceException;

public class RecentJobsBean {
    private MyJobInfo[] jobs;

    private static Logger log = Logger.getLogger(RecentJobsBean.class);

    public RecentJobsBean() {
        updateJobs();
    }

    private void updateJobs() {
        String userId = UIBeanHelper.getUserId();
        assert userId != null;
        int recentJobsToShow = Integer.parseInt(UserPrefsBean.getProp(
                UserPropKey.RECENT_JOBS_TO_SHOW, "4").getValue());
        LocalAnalysisClient analysisClient = new LocalAnalysisClient(userId);
        try {
            // JobInfo[] temp = analysisClient.getJobs(userId, -1,
            // recentJobsToShow,
            // false); // FIXME uncomment when bug recording jobs is fixed
            JobInfo[] temp = analysisClient.getJobs(null, -1, recentJobsToShow,
                    false);
            jobs = new MyJobInfo[temp.length];
            TaskInfo[] tasks = new AdminDAO().getAllTasksForUser(UIBeanHelper
                    .getUserId());
            Map<String, Collection<TaskInfo>> kindToModules = SemanticUtil
                    .getKindToModulesMap(tasks);
            for (int i = 0; i < jobs.length; i++) {
                jobs[i] = new MyJobInfo(temp[i], kindToModules);
            }
        } catch (WebServiceException wse) {
            log.error(wse);
        }
    }

    public int getSize() {
        return jobs == null ? 0 : jobs.length;
    }

    public MyJobInfo[] getJobs() {
        return jobs;
    }

    public void createPipeline(ActionEvent e) {
        try {
            String jobNumber = UIBeanHelper.decode(UIBeanHelper.getRequest()
                    .getParameter("jobNumber"));
            String pipelineName = "job" + jobNumber; // TODO prompt user for
            // name
            String lsid = new LocalAnalysisClient(UIBeanHelper.getUserId())
                    .createProvenancePipeline(jobNumber, pipelineName);

            if (lsid == null) {
                UIBeanHelper.setInfoMessage("Unable to create pipeline.");
                return;
            }
            UIBeanHelper.getResponse().sendRedirect(
                    UIBeanHelper.getRequest().getContextPath()
                            + "/pipelineDesigner.jsp?name="
                            + UIBeanHelper.encode(lsid));
        } catch (IOException e1) {
            log.error(e1);
        }

    }

    public String reload(ActionEvent event) {
        LocalAnalysisClient ac = new LocalAnalysisClient(UIBeanHelper
                .getUserId());
        try {
            int jobNumber = Integer.parseInt(UIBeanHelper.decode(UIBeanHelper
                    .getRequest().getParameter("jobNumber")));
            JobInfo reloadJob = ac.getJob(jobNumber);
            RunTaskBean runTaskBean = (RunTaskBean) UIBeanHelper
                    .getManagedBean("#{runTaskBean}");
            assert runTaskBean != null;
            UIBeanHelper.getRequest().setAttribute("reloadJob",
                    String.valueOf(reloadJob.getJobNumber()));
            runTaskBean.setTask(reloadJob.getTaskLSID());
        } catch (WebServiceException e) {
            log.error(e);
        } catch (NumberFormatException e) {
            log.error(e);
        }
        return "run task";
    }

    public void deleteFile(ActionEvent event) {
        try {
            String value = UIBeanHelper.decode(UIBeanHelper.getRequest()
                    .getParameter("jobFile"));
            int index = value.indexOf("/");
            int jobNumber = Integer.parseInt(value.substring(0, index));
            String filename = value.substring(index + 1);
            new LocalAnalysisClient(UIBeanHelper.getUserId())
                    .deleteJobResultFile(jobNumber, jobNumber + "/" + filename);
        } catch (NumberFormatException e) {
            log.error(e);
        } catch (WebServiceException e) {
            e.printStackTrace();
            log.error(e);
        }
    }

    public void saveFile(ActionEvent event) {
        InputStream is = null;

        try {
            String value = UIBeanHelper.decode(UIBeanHelper.getRequest()
                    .getParameter("jobFileName"));
            int index = value.indexOf("/");
            String jobNumber = value.substring(0, index);
            String filename = value.substring(index + 1);
            File in = new File(GenePatternAnalysisTask.getJobDir(jobNumber),
                    filename);
            if (!in.exists()) {
                UIBeanHelper.setInfoMessage("File " + filename
                        + " does not exist.");
                return;
            }
            HttpServletResponse response = UIBeanHelper.getResponse();
            // response.setHeader("Content-Disposition", "attachment; filename="
            // + in.getName() + ";");
            response.setHeader("Content-disposition", "inline; filename=\""
                    + in.getName() + "\"");
            response.setHeader("Content-Type", "application/octet-stream");
            response.setHeader("Cache-Control", "no-store"); // HTTP 1.1
            // cache
            // control
            response.setHeader("Pragma", "no-cache"); // HTTP 1.0 cache
            // control
            response.setDateHeader("Expires", 0);

            OutputStream os = response.getOutputStream();
            is = new BufferedInputStream(new FileInputStream(in));
            byte[] b = new byte[10000];
            int bytesRead;
            while ((bytesRead = is.read(b)) != -1) {
                os.write(b, 0, bytesRead);
            }
            os.flush();
            os.close();
            UIBeanHelper.getFacesContext().responseComplete();
        } catch (IOException e) {
            log.error(e);

        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    log.error(e);
                }
            }
        }

    }

    public void delete(ActionEvent event) {
        try {
            int jobNumber = Integer.parseInt(UIBeanHelper.decode(UIBeanHelper
                    .getRequest().getParameter("jobNumber")));
            LocalAnalysisClient ac = new LocalAnalysisClient(UIBeanHelper
                    .getUserId());
            ac.deleteJob(jobNumber);
            updateJobs(); // TODO don't retrieve jobs twice from the database
        } catch (WebServiceException e) {
            log.error(e);
        } catch (NumberFormatException e) {
            log.error(e);
        }

    }

    public void viewCode(ActionEvent event) {

        try {
            int jobNumber = Integer.parseInt(UIBeanHelper.decode(UIBeanHelper
                    .getRequest().getParameter("jobNumber")));
            String language = UIBeanHelper.decode(UIBeanHelper.getRequest()
                    .getParameter("language"));
            String code = CodeGeneratorUtil.getCode(language, new AnalysisJob(
                    UIBeanHelper.getUserId(), new LocalAnalysisClient(
                            UIBeanHelper.getUserId()).getJob(jobNumber)));
            HttpServletResponse response = UIBeanHelper.getResponse();
            String filename = jobNumber
                    + CodeGeneratorUtil.getFileExtension(language);
            response.setHeader("Content-disposition", "inline; filename=\""
                    + filename + "\"");
            response.setHeader("Content-Type", "text/plain");
            response.setHeader("Cache-Control", "no-store"); // HTTP 1.1
            // cache
            // control
            response.setHeader("Pragma", "no-cache"); // HTTP 1.0 cache
            // control
            response.setDateHeader("Expires", 0);

            OutputStream os = response.getOutputStream();
            PrintWriter pw = new PrintWriter(os);
            pw.println(code);
            pw.flush();
            os.close();
            UIBeanHelper.getFacesContext().responseComplete();
        } catch (Exception e) {
            log.error(e);
        }
    }

    public void terminateJob(ActionEvent event) {
        try {
            int jobNumber = Integer.parseInt(UIBeanHelper.decode(UIBeanHelper
                    .getRequest().getParameter("jobNumber")));
            LocalAnalysisClient ac = new LocalAnalysisClient(UIBeanHelper
                    .getUserId());
            ac.terminateJob(jobNumber);
            updateJobs(); // TODO don't retrieve jobs twice from the database
        } catch (WebServiceException e) {
            log.error(e);
        } catch (NumberFormatException e) {
            log.error(e);
        }
    }

    // This action is not referenced from anywhere
    public String loadTask(ActionEvent event) {
        String lsid = UIBeanHelper.decode(UIBeanHelper.getRequest()
                .getParameter("module"));
        RunTaskBean runTaskBean = (RunTaskBean) UIBeanHelper
                .getManagedBean("#{runTaskBean}");
        assert runTaskBean != null;
        runTaskBean.setTask(lsid);

        return "run task";
    }

    public static class MyJobInfo {
        private JobInfo jobInfo;

        private List<MyParameterInfo> outputFiles;

        public MyJobInfo(JobInfo jobInfo,
                Map<String, Collection<TaskInfo>> kindToModules) {
            this.jobInfo = jobInfo;
            outputFiles = new ArrayList<MyParameterInfo>();
            ParameterInfo[] parameterInfoArray = jobInfo
                    .getParameterInfoArray();
            if (parameterInfoArray != null) {
                File outputDir = new File(GenePatternAnalysisTask.getJobDir(""
                        + jobInfo.getJobNumber()));
                for (int i = 0; i < parameterInfoArray.length; i++) {
                    if (parameterInfoArray[i].isOutputFile()) {

                        File file = new File(outputDir, parameterInfoArray[i]
                                .getName());
                        Collection<TaskInfo> modules = kindToModules
                                .get(SemanticUtil.getKind(file));
                        outputFiles.add(new MyParameterInfo(
                                parameterInfoArray[i], modules));
                    }
                }
            }
        }

        public boolean isComplete() {
            String status = jobInfo.getStatus();
            return status.equalsIgnoreCase("Finished")
                    || status.equalsIgnoreCase("Error");
        }

        public Date getDateCompleted() {
            return jobInfo.getDateCompleted();
        }

        public Date getDateSubmitted() {
            return jobInfo.getDateSubmitted();
        }

        public int getJobNumber() {
            return jobInfo.getJobNumber();
        }

        public List<MyParameterInfo> getOutputFileParameterInfos() {
            return outputFiles;
        }

        public ParameterInfo[] getParameterInfoArray() {
            return jobInfo.getParameterInfoArray();
        }

        public String getStatus() {
            return jobInfo.getStatus();
        }

        public int getTaskID() {
            return jobInfo.getTaskID();
        }

        public String getTaskLSID() {
            return jobInfo.getTaskLSID();
        }

        public String getTaskName() {
            return jobInfo.getTaskName();
        }

        public String getUserId() {
            return jobInfo.getUserId();
        }
    }

    private static class NavigationMenuItemComparator implements
            Comparator<NavigationMenuItem> {

        public int compare(NavigationMenuItem o1, NavigationMenuItem o2) {
            return o1.getLabel().compareToIgnoreCase(o2.getLabel());
        }

    }

    public static class MyParameterInfo {
        ParameterInfo p;

        List<NavigationMenuItem> moduleMenuItems = new ArrayList<NavigationMenuItem>();

        private static final Comparator COMPARATOR = new NavigationMenuItemComparator();

        public List<NavigationMenuItem> getModuleMenuItems() {
            return moduleMenuItems;
        }

        public MyParameterInfo(ParameterInfo p, Collection<TaskInfo> modules) {
            this.p = p;

            if (modules != null) {
                for (TaskInfo t : modules) {
                    NavigationMenuItem mi = new NavigationMenuItem(t
                            .getShortName(), "module");

                    mi.setValue(UIBeanHelper.encode(t.getLsid()));

                    mi.setActionListener("#{recentJobsBean.loadTask}");
                    moduleMenuItems.add(mi);
                }
                Collections.sort(moduleMenuItems, COMPARATOR);
            }
        }

        public HashMap getAttributes() {
            return p.getAttributes();
        }

        public String[] getChoices(String delimiter) {
            return p.getChoices(delimiter);
        }

        public String getDescription() {
            return p.getDescription();
        }

        public String getLabel() {
            return p.getLabel();
        }

        public String getName() {
            return UIBeanHelper.encode(p.getName());
        }

        public String getUIValue(ParameterInfo formalParam) {
            return p.getUIValue(formalParam);
        }

        public String getValue() {
            return p.getValue();
        }

        public boolean hasChoices(String delimiter) {
            return p.hasChoices(delimiter);
        }

        public boolean isInputFile() {
            return p.isInputFile();
        }

        public boolean isOutputFile() {
            return p.isOutputFile();
        }

        public boolean isPassword() {
            return p.isPassword();
        }

        public void setAsInputFile() {
            p.setAsInputFile();
        }

        public void setAsOutputFile() {
            p.setAsOutputFile();
        }

        public void setAttributes(HashMap attributes) {
            p.setAttributes(attributes);
        }

        public void setDescription(String description) {
            p.setDescription(description);
        }

        public void setLabel(String label) {
            p.setLabel(label);
        }

        public void setName(String name) {
            p.setName(name);
        }

        public void setValue(String value) {
            p.setValue(value);
        }

        public String toString() {
            return p.toString();
        }
    }

}
