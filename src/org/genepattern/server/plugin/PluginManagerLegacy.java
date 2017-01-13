/*******************************************************************************
 * Copyright (c) 2003, 2015 Broad Institute, Inc. and Massachusetts Institute of Technology.  All rights reserved.
 *******************************************************************************/
package org.genepattern.server.plugin;

import static org.genepattern.util.GPConstants.COMMAND_LINE;
import static org.genepattern.util.GPConstants.DEFAULT_PATCH_URL;
import static org.genepattern.util.GPConstants.MANIFEST_FILENAME;
import static org.genepattern.util.GPConstants.PATCH_ERROR_EXIT_VALUE;
import static org.genepattern.util.GPConstants.PATCH_SUCCESS_EXIT_VALUE;
import static org.genepattern.util.GPConstants.REQUIRED_PATCH_LSIDS;
import static org.genepattern.util.GPConstants.REQUIRED_PATCH_URLS;
import static org.genepattern.util.GPConstants.UTF8;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.genepattern.server.config.ConfigurationException;
import org.genepattern.server.config.GpConfig;
import org.genepattern.server.config.GpContext;
import org.genepattern.server.config.ServerConfigurationFactory;
import org.genepattern.server.database.HibernateSessionManager;
import org.genepattern.server.executor.JobDispatchException;
import org.genepattern.server.genepattern.CommandLineParser;
import org.genepattern.server.genepattern.GenePatternAnalysisTask;
import org.genepattern.server.rest.ParameterInfoRecord;
import org.genepattern.server.webservice.server.Status;
import org.genepattern.util.LSID;
import org.genepattern.webservice.TaskInfo;
import org.genepattern.webservice.TaskInfoAttributes;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Duplicate patch installation functions from GenePatternAnalysisTask class, circa GP 3.3.3.
 * 
 * @author pcarr
 */
public class PluginManagerLegacy {
    private static Logger log = Logger.getLogger(PluginManagerLegacy.class);
    
    private final HibernateSessionManager mgr;
    private GpConfig gpConfig;
    private final GpContext gpContext;
    private final PluginRegistry pluginRegistry;
    
    public PluginManagerLegacy(HibernateSessionManager mgr, GpConfig gpConfig, GpContext gpContext) {
        this(mgr, gpConfig, gpContext, initDefaultPluginRegistry(mgr, gpConfig, gpContext));
    }

    public PluginManagerLegacy(HibernateSessionManager mgr, GpConfig gpConfig, GpContext gpContext, PluginRegistry pluginRegistry) {
        this.mgr=mgr;
        this.gpConfig=gpConfig;
        this.gpContext=gpContext;
        this.pluginRegistry=pluginRegistry;
    }
    
    public static PluginRegistry initDefaultPluginRegistry(final HibernateSessionManager mgr, final GpConfig gpConfig, final GpContext gpContext) {
        return new PluginRegistryGpDb(mgr);
    }
    
    /**
     * Get the list of required patches for the given task.
     * @param taskInfo
     * @return
     */
    protected List<PatchInfo> getRequiredPatches(final TaskInfo taskInfo) throws JobDispatchException {
        final TaskInfoAttributes tia = taskInfo.giveTaskInfoAttributes();
        String requiredPatchLSID = tia.get(REQUIRED_PATCH_LSIDS);
        String requiredPatchURL = tia.get(REQUIRED_PATCH_URLS);
        return getRequiredPatches(requiredPatchLSID, requiredPatchURL);
    }
    
    protected List<PatchInfo> getRequiredPatches(final String requiredPatchLSIDs, final String requiredPatchURLs) 
    throws JobDispatchException {
        // no patches required?
        if (requiredPatchLSIDs == null || requiredPatchLSIDs.length() == 0) {
            return Collections.emptyList();
        }
        
        String[] requiredPatchLSIDArray = requiredPatchLSIDs.split(",");
        String[] requiredPatchURLArray = (requiredPatchURLs != null && requiredPatchURLs.length() > 0 ? requiredPatchURLs.split(",")
                : new String[requiredPatchLSIDArray.length]);
        if (requiredPatchURLArray != null && requiredPatchURLArray.length != requiredPatchLSIDArray.length) {
            throw new JobDispatchException("manifest has " + requiredPatchLSIDArray.length + " patch LSIDs but " + requiredPatchURLArray.length + " URLs");
        }
        List<PatchInfo> patchInfos=new ArrayList<PatchInfo>();
        for(int i=0; i<requiredPatchLSIDArray.length; ++i) {
            try {
                PatchInfo patchInfo=new PatchInfo(requiredPatchLSIDArray[i], requiredPatchURLArray[i]);
                patchInfos.add(patchInfo);
            }
            catch (MalformedURLException e) {
                throw new JobDispatchException("Error initializing patchInfo from args, "+
                        "lsid="+requiredPatchLSIDArray[i]+
                        "url="+requiredPatchURLArray[i], e);
            }
        }
        return patchInfos;
    }
    
    protected List<PatchInfo> getPatchesToInstall(final TaskInfo taskInfo) throws Exception, MalformedURLException, JobDispatchException {
        final List<PatchInfo> requiredPatches=getRequiredPatches(taskInfo);
        // no patches required?
        if (requiredPatches==null || requiredPatches.size()==0) {
            return Collections.emptyList();
        }
        
        // remove installed patches from list of required patches
        for (Iterator<PatchInfo> iterator = requiredPatches.iterator(); iterator.hasNext();) {
            final PatchInfo requiredPatch = iterator.next();
            if (pluginRegistry.isInstalled(gpConfig, gpContext, requiredPatch)) {
                iterator.remove();
            }
        }
        return requiredPatches;
    }

    /**
     * Get the list of installed patches.
     * @return
     * @throws Exception
     */
    protected List<PatchInfo> getInstalledPatches() throws Exception {
        return pluginRegistry.getInstalledPatches(gpConfig, gpContext);
    }
    
    protected String toStringOrEmpty(Object obj) {
        if (obj==null) {
            return "";
        }
        return obj.toString();
    }

    protected String toStringOrNull(Object obj) {
        if (obj==null) {
            return null;
        }
        return obj.toString();
    }

    /**
     * Check that each patch listed in the TaskInfoAttributes for this task is installed.
     * if not, download and install it.
     * For any problems, throw an exception
     * 
     * @param taskInfo
     * @param taskIntegrator
     * 
     * @throws Exception
     * @throws MalformedURLException
     * @throws JobDispatchException
     */
    public boolean validatePatches(TaskInfo taskInfo, Status taskIntegrator) throws Exception, MalformedURLException, JobDispatchException {
        List<PatchInfo> patchesToInstall=getPatchesToInstall(taskInfo);
        // no patches to install?
        if (patchesToInstall==null) {
            log.error("Unexpected null value returned from getPatchesToInstall, for task="
                    +taskInfo.getName()+", "+taskInfo.getLsid());
            return true;
        } 
        for(final PatchInfo patchToInstall : patchesToInstall) {
            // download and install this patch
            checkInstallPatch(toStringOrNull(patchToInstall.getPatchLsid()), toStringOrEmpty(patchToInstall.getPatchUrl()), taskIntegrator);
        }
        // end of loop for each patch LSID for the task
        return true;
    }

    /**
     * Install the patch if and only if it is not already installed.
     * @param requiredPatchLSID
     * @param requiredPatchURL
     * @param taskIntegrator
     * @throws Exception
     */
    protected void checkInstallPatch(String requiredPatchLSID, String requiredPatchURL, Status taskIntegrator) throws Exception {
        final boolean isInTransaction=mgr.isInTransaction();
        if (mgr.isInTransaction()) {
            log.debug("isInTransaction="+isInTransaction+", closeCurrentSession");
            mgr.closeCurrentSession();
        }
        boolean isInstalled=pluginRegistry.isInstalled(gpConfig, gpContext, new PatchInfo(requiredPatchLSID, requiredPatchURL));
        if (isInstalled) {
            taskIntegrator.statusMessage("Patch is already installed, patchLsid="+requiredPatchLSID);
            return;
        }
        boolean wasNullURL = (requiredPatchURL == null || requiredPatchURL.length() == 0);
        long expectedContentLength=-1;
        if (wasNullURL) {
            requiredPatchURL = System.getProperty(DEFAULT_PATCH_URL);
            HashMap<String,String> hmProps = fetchPatchInfoFromUrl(requiredPatchLSID, requiredPatchURL, taskIntegrator);
            String result = (String) hmProps.get("result");
            if (!result.equals("Success")) {
                throw new JobDispatchException("Error requesting patch: " + result + " in request for " + requiredPatchURL);
            }
            requiredPatchURL = (String) hmProps.get("site_module.url");
            try {
                expectedContentLength = Long.parseLong((String) hmProps.get("site_module.zipfilesize"));
            }
            catch (NullPointerException npe) {
                // ignore
            } 
            catch (NumberFormatException nfe) {
                // ignore
            }
        }
        installPatch(requiredPatchLSID, requiredPatchURL, expectedContentLength, taskIntegrator);
    } 

    /**
     * Get the location on the server file system for installing the patch.
     * @param patchLSID
     * @return
     * @throws ConfigurationException, if there is no configured root plugin directory (e.g. '<GENEPATTERN_HOME>/patches')
     */
    protected File getPatchDirectory(final LSID patchLSID) 
    throws ConfigurationException
    {
        final String patchDirName = patchLSID.getAuthority() + "." + patchLSID.getNamespace() + "." + patchLSID.getIdentifier() + "." + patchLSID.getVersion();
        File rootPluginDir=gpConfig.getRootPluginDir(gpContext);
        if (rootPluginDir==null) {
            throw new ConfigurationException("Configuration error: Unable to get patch directory");
        }
        return new File(rootPluginDir, patchDirName);
    }

    /**
     * refactored from original (pre 3.9.4) implementation of 'installPatch'; When the module manifest does not include a patchURL,
     * use the server configured location to initialize the patch meta-data.
     * 
     * @param requiredPatchLSID
     * @param requiredPatchURL
     * @param taskIntegrator
     * @return the patch meta data
     * @throws JobDispatchException
     */
    protected HashMap<String,String> fetchPatchInfoFromUrl(final String requiredPatchLSID, final String requiredPatchURL, final Status taskIntegrator) 
    throws JobDispatchException
    {
        try {
            final HashMap<String,String> hmProps = new HashMap<String,String>();
            taskIntegrator.statusMessage("Fetching patch information from " + requiredPatchURL);
            final URL url = new URL(requiredPatchURL);
            final URLConnection connection = url.openConnection();
            connection.setUseCaches(false);
            if (connection instanceof HttpURLConnection) {
                connection.setDoOutput(true);
                final PrintWriter pw = new PrintWriter(connection.getOutputStream());
                final String[] patchQualifiers = System.getProperty("patchQualifiers", "").split(",");
                pw.print("patch");
                pw.print("=");
                pw.print(URLEncoder.encode(requiredPatchLSID, UTF8));
                for (int p = 0; p < patchQualifiers.length; p++) {
                    pw.print("&");
                    pw.print(URLEncoder.encode(patchQualifiers[p], UTF8));
                    pw.print("=");
                    pw.print(URLEncoder.encode(System.getProperty(patchQualifiers[p], ""), UTF8));
                }
                pw.close();
            }
            final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(connection.getInputStream());
            final Element root = doc.getDocumentElement();
            processNode(root, hmProps);
            return hmProps;
        }
        catch (Throwable t) {
            //catch MalformedURLException, IOException, UnsupportedEncodingException, SAXException, ParserConfigurationException 
            String errorMessage="Error installing patch, lsid="+requiredPatchLSID+", url="+requiredPatchURL+": "+t.getLocalizedMessage();
            log.error(errorMessage, t);
            throw new JobDispatchException(errorMessage, t);
        }
    }
    
    /**
     * Install a specific patch, downloading a zip file with a manifest containing a command line, 
     * running that command line after substitutions, and recording the result 
     * in the genepattern.properties patch registry
     */
    protected void installPatch(final String requiredPatchLSID, final String requiredPatchURL, final long expectedContentLength, final Status taskIntegrator) throws JobDispatchException {
        log.debug("installPatch, lsid="+requiredPatchLSID+", url="+requiredPatchURL+", contentLength="+expectedContentLength);
        LSID patchLSID = null;
        try {
            patchLSID = new LSID(requiredPatchLSID);
        }
        catch (MalformedURLException e) {
            throw new JobDispatchException("Error installing patch, requiredPatchLSID="+requiredPatchLSID, e);
        }
        File patchDirectory = null; 
        try {
            patchDirectory = getPatchDirectory(patchLSID); 
        }
        catch (Throwable t) {
            throw new JobDispatchException(t.getLocalizedMessage(), t);
        }
        if (taskIntegrator != null) {
            taskIntegrator.statusMessage("Downloading required patch from " + requiredPatchURL + "...");
        }
        String zipFilename = null;
        try {
            zipFilename = downloadPatch(requiredPatchURL, taskIntegrator, expectedContentLength);
        }
        catch (IOException e) {
            String errorMessage="Error downloading patch, lsid="+requiredPatchLSID+", url="+requiredPatchURL+": "+e.getLocalizedMessage();
            log.error(errorMessage, e);
            throw new JobDispatchException(errorMessage, e);
        }
        
        if (taskIntegrator != null) {
            taskIntegrator.statusMessage("Installing patch from " + patchDirectory.getPath() + ".");
        }
        try {
            explodePatch(zipFilename, patchDirectory, taskIntegrator);
        }
        catch (IOException e) {
            String errorMessage="Error unzipping patch from "+zipFilename+" into "+patchDirectory;
            log.error(errorMessage, e);
            throw new JobDispatchException(errorMessage, e);
        }
        new File(zipFilename).delete();

        // entire zip file has been exploded, now load the manifest, get the command line, and execute it
        Properties props = null;
        try {
            props = loadManifest(patchDirectory);
        }
        catch (IOException e) {
            String errorMessage="Error loading manifest from "+patchDirectory;
            log.error(errorMessage, e);
            throw new JobDispatchException(errorMessage, e);
        }
        
        validatePatchLsid(requiredPatchLSID, props);
        
        String nomDePatch = props.getProperty("name");
        if (taskIntegrator != null) {
            taskIntegrator.statusMessage("Running " + nomDePatch + " Installer.");
        }
        String exitValue = null; 
        
        String cmdLine = props.getProperty(COMMAND_LINE);
        if (cmdLine == null || cmdLine.length() == 0) {
            throw new JobDispatchException("No command line defined in " + MANIFEST_FILENAME);
        }
        List<String> cmdLineArgs=initCmdLineArray(cmdLine);
        String[] cmdLineArray = new String[0];
        cmdLineArray = cmdLineArgs.toArray(cmdLineArray);
        try {
            exitValue = "" + executePatch(cmdLineArray, patchDirectory, taskIntegrator);
        }
        catch (IOException e) {
            String errorMessage="IOException while running patch: "+e.getLocalizedMessage();
            log.error(errorMessage, e);
            throw new JobDispatchException(errorMessage, e);
        }
        catch (InterruptedException e2) {
            Thread.currentThread().interrupt();
            throw new JobDispatchException("Patch install interrupted", e2);
        }
        catch (Throwable t) {
            throw new JobDispatchException("Unexpected error while installing patch, lsid="+requiredPatchLSID+": "+t.getLocalizedMessage(), t);
        }
        if (taskIntegrator != null) {
            taskIntegrator.statusMessage("Patch installed, exit code " + exitValue);
        }
        String goodExitValue = props.getProperty(PATCH_SUCCESS_EXIT_VALUE, "0");
        String failureExitValue = props.getProperty(PATCH_ERROR_EXIT_VALUE, "");
        if (exitValue.equals(goodExitValue) || !exitValue.equals(failureExitValue)) {
            try {
                File patchManifest=new File(patchDirectory, "manifest");
                PatchInfo patchInfo=MigratePlugins.initPatchInfoFromManifest(patchManifest);
                patchInfo.setUrl(requiredPatchURL);
                patchInfo.setPatchDir(patchDirectory.getAbsolutePath());
                pluginRegistry.recordPatch(gpConfig, gpContext, patchInfo);
                // always reload config
                ServerConfigurationFactory.reloadConfiguration();
                this.gpConfig=ServerConfigurationFactory.instance();
            }
            catch (Exception e) {
                String errorMessage="Exception while recording patch: "+e.getLocalizedMessage();
                log.error(errorMessage, e);
                throw new JobDispatchException(errorMessage, e);
            }
            if (taskIntegrator != null) {
                taskIntegrator.statusMessage("Patch LSID recorded");
            }

            // keep the manifest file around for future reference
            if (!new File(patchDirectory, MANIFEST_FILENAME).exists()) {
                try {
                    explodePatch(zipFilename, patchDirectory, null, MANIFEST_FILENAME);
                }
                catch (IOException e) {
                    String errorMessage="IOException while unzipping patch from "+zipFilename+" into "+patchDirectory+": "+e.getLocalizedMessage();
                    log.error(errorMessage, e);
                    throw new JobDispatchException(errorMessage, e);
                }
                if (props.getProperty(REQUIRED_PATCH_URLS, null) == null) {
                    try {
                        File f = new File(patchDirectory, MANIFEST_FILENAME);
                        Properties mprops = new Properties();
                        mprops.load(new FileInputStream(f));
                        mprops.setProperty(REQUIRED_PATCH_URLS, requiredPatchURL);
                        mprops.store(new FileOutputStream(f), "added required patch");
                    } 
                    catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            }
        } 
        else {
            if (taskIntegrator != null) {
                taskIntegrator.statusMessage("Deleting patch directory after installation failure");
            }
            // delete patch directory
            File[] old = patchDirectory.listFiles();
            for (int i = 0; old != null && i < old.length; i++) {
                old[i].delete();
            }
            patchDirectory.delete();
            throw new JobDispatchException("Could not install required patch: " + props.get("name") + "  " + props.get("LSID"));
        }
    }
    
    /**
     * Check for patch LSID mismatch
     * @param requiredPatchLSID, the patch LSID declared in the module manifest file
     * @param patchProperties, the actual properties loaded from the manifest file for the patch
     * @return
     * @throws JobDispatchException iff the requiredPatchLSID does not match the LSID in the patch properties
     */
    protected static boolean validatePatchLsid(final String requiredPatchLSID, final Properties patchProperties) 
    throws JobDispatchException
    {
         final String actualPatchLsid=patchProperties.getProperty("LSID");
        if (!Objects.equals(actualPatchLsid, requiredPatchLSID)) {
            throw new JobDispatchException("patch LSID mismatch, requiredPatchLSID="+requiredPatchLSID+" does not match the LSID in the patch manifest, patchLSID="+actualPatchLsid);
        } 
        return true;
    }
    
    protected static List<String> initCmdLineArray(final String cmdLine) {
        final GpConfig gpConfig=ServerConfigurationFactory.instance();
        final GpContext gpContext=GpContext.getServerContext(); //TODO: <==== create a context for the patch
        return initCmdLineArray(gpConfig, gpContext, cmdLine);
    }

    protected static List<String> initCmdLineArray(final GpConfig gpConfig, final GpContext gpContext, final String cmdLine) {
        final Map<String,ParameterInfoRecord> paramInfoMap=Collections.emptyMap();
        final List<String> cmdLineArgs = CommandLineParser.createCmdLine(gpConfig, gpContext, cmdLine, paramInfoMap);        
        return cmdLineArgs;
    }

    /**
     * download the patch zip file from a URL
     * @param url
     * @param taskIntegrator
     * @param expectedContentLength, the content length of the zip file or '-1'
     * @return
     * @throws IOException
     */
    private static String downloadPatch(String url, Status taskIntegrator, long expectedContentLength) throws IOException {
        try {
            return GenePatternAnalysisTask.downloadTask(url, taskIntegrator, expectedContentLength);
        } 
        catch (IOException ioe) {
            if (ioe.getCause() != null) {
                ioe = (IOException) ioe.getCause();
            }
            throw new IOException(ioe.toString() + " while downloading " + url);
        }
    }

    // unzip the patch files into their own directory
    private static void explodePatch(String zipFilename, File patchDirectory, Status taskIntegrator) throws IOException {
        explodePatch(zipFilename, patchDirectory, taskIntegrator, null);
    }

    // unzip the patch files into their own directory
    private static void explodePatch(String zipFilename, File patchDirectory, Status taskIntegrator, String zipEntryName)
    throws IOException 
    {
        ZipFile zipFile = new ZipFile(zipFilename);
        InputStream is = null;
        patchDirectory.mkdirs();
        if (zipEntryName == null) {
            // clean out existing directory
            File[] old = patchDirectory.listFiles();
            for (int i = 0; old != null && i < old.length; i++) {
                old[i].delete();
            }
        }
        for (Enumeration eEntries = zipFile.entries(); eEntries.hasMoreElements();) {
            ZipEntry zipEntry = (ZipEntry) eEntries.nextElement();
            if (zipEntryName != null && !zipEntryName.equals(zipEntry.getName())) {
                continue;
            }
            File outFile = new File(patchDirectory, zipEntry.getName());
            if (zipEntry.isDirectory()) {
                if (taskIntegrator != null) {
                    taskIntegrator.statusMessage("Creating subdirectory " + outFile.getAbsolutePath());
                }
                outFile.mkdirs();
                continue;
            }
            is = zipFile.getInputStream(zipEntry);
            OutputStream os = new FileOutputStream(outFile);
            long fileLength = zipEntry.getSize();
            long numRead = 0;
            byte[] buf = new byte[100000];
            int i;
            while ((i = is.read(buf, 0, buf.length)) > 0) {
                os.write(buf, 0, i);
                numRead += i;
            }
            os.close();
            os = null;
            if (numRead != fileLength) {
                throw new IOException("only read " + numRead + " of " + fileLength + " bytes in " + zipFile.getName() + "'s " + zipEntry.getName());
            }
            is.close();
        } // end of loop for each file in zip file
        zipFile.close();
    }

    // load the patch manifest file into a Properties object
    protected static Properties loadManifest(File patchDirectory) throws IOException {
        File manifestFile = new File(patchDirectory, MANIFEST_FILENAME);
        if (!manifestFile.exists()) {
            throw new IOException(MANIFEST_FILENAME + " missing from patch " + patchDirectory.getName());
        }
        Properties props = new Properties();
        FileInputStream manifest = new FileInputStream(manifestFile);
        props.load(manifest);
        manifest.close();
        return props;
    }

    /**
     * Run the patch command line in the patch directory, returning the exit code from the executable.
     */
    private static int executePatch(String[] commandLineArray, File patchDirectory, Status taskIntegrator) throws IOException, InterruptedException {
        if (log.isDebugEnabled()) {
            log.debug("patch dir="+patchDirectory);
            log.debug("patch commandLine ...");
            int i=0;
            for(final String arg : commandLineArray) {
                log.debug("\targ["+i+"]: "+arg);
            }
        }
        
        // spawn the command
        Process process = Runtime.getRuntime().exec(commandLineArray, null, patchDirectory);

        // BUG: there is race condition during a tiny time window between the exec and the close
        // (the lines above and below this comment) during which it is possible for an application
        // to imagine that there might be useful input coming from stdin.
        // This seemed to be the case for Perl 5.0.1 on Wilkins, and might be a problem in
        // other applications as well.
        process.getOutputStream().close(); 
        // there is no stdin to feed to the program. So if it asks, let it see EOF!

        // create threads to read from the command's stdout and stderr streams
        Thread outputReader = (taskIntegrator != null) ? antStreamCopier(process.getInputStream(), taskIntegrator)
                : streamCopier(process.getInputStream(), System.out);
        Thread errorReader = (taskIntegrator != null) ? antStreamCopier(process.getErrorStream(), taskIntegrator) : streamCopier(
                process.getInputStream(), System.err);

        // drain the output and error streams
        outputReader.start();
        errorReader.start();

        // wait for all output
        outputReader.join();
        errorReader.join();

        // the process will be dead by now
        process.waitFor();
        int exitValue = process.exitValue();
        return exitValue;
    }
    
    protected static void processNode(Node node, HashMap<String,String> hmProps) {
    if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element c_elt = (Element) node;
        String nodeValue = c_elt.getFirstChild().getNodeValue();
        log.debug("GPAT.processNode: adding " + c_elt.getTagName() + "=" + nodeValue);
        hmProps.put(c_elt.getTagName(), nodeValue);
        NamedNodeMap attributes = c_elt.getAttributes();
        if (attributes != null) {
        for (int i = 0; i < attributes.getLength(); i++) {
            String attrName = ((Attr) attributes.item(i)).getName();
            String attrValue = ((Attr) attributes.item(i)).getValue();
            log.debug("GPAT.processNode: adding " + c_elt.getTagName() + "." + attrName + "=" + attrValue);
            hmProps.put(c_elt.getTagName() + "." + attrName, attrValue);
        }
        }
    } else {
        log.debug("non-Element node: " + node.getNodeName() + "=" + node.getNodeValue());
    }
    NodeList childNodes = node.getChildNodes();
    for (int i = 0; i < childNodes.getLength(); i++) {
        processNode(childNodes.item(i), hmProps);
    }
    }
    // copy an InputStream to a PrintStream until EOF
    public static Thread streamCopier(final InputStream is, final PrintStream ps) throws IOException {
    // create thread to read from the a process' output or error stream
    return new Thread(new Runnable() {
        public void run() {
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        String line;
        try {
            while ((line = in.readLine()) != null) {
            ps.println(line);
            ps.flush();
            }
        } catch (IOException ioe) {
            System.err.println(ioe + " while reading from process stream");
        }
        }
    });
    }

    // copy an InputStream to a PrintStream until EOF
    public static Thread streamCopier(final InputStream is, final Status taskIntegrator) throws IOException {
    // create thread to read from the a process' output or error stream
    return new Thread(new Runnable() {
        public void run() {
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        String line;
        try {
            while ((line = in.readLine()) != null) {
            if (taskIntegrator != null && line != null) {
                taskIntegrator.statusMessage(line);
            }
            }
        } catch (IOException ioe) {
            System.err.println(ioe + " while reading from process stream");
        }
        }
    });
    }

    // copy an InputStream to a PrintStream until EOF
    public static Thread antStreamCopier(final InputStream is, final Status taskIntegrator) throws IOException {
    // create thread to read from the a process' output or error stream
    return new Thread(new Runnable() {
        public void run() {
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        String line;
        try {
            while ((line = in.readLine()) != null) {
            int idx = 0;
            if ((idx = line.indexOf("[echo]")) >= 0) {
                line = line.substring(idx + 6);
            }
            if (taskIntegrator != null && line != null) {
                taskIntegrator.statusMessage(line);
            }
            }
        } catch (IOException ioe) {
            System.err.println(ioe + " while reading from process stream");
        }
        }
    });
    }

}
