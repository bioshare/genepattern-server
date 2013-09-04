package org.genepattern.server.job.input.choice;

import java.io.File;

import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileFilter;
import org.genepattern.webservice.ParameterInfo;

/**
 * A filter for apache commons FTPFile listings.
 * @author pcarr
 *
 */
public class FtpDirFilter extends DirFilter implements FTPFileFilter {
     public FtpDirFilter(final ParameterInfo param) {
         super(param);
     }

    @Override
    public boolean accept(final FTPFile ftpFile) {
        if (type==Type.file) {
            if (!ftpFile.isFile()) {
                return false;
            }
        }
        else if (type==Type.dir) {
            if (!ftpFile.isDirectory()) {
                return false;
            }
        }
        
        //check for glob patterns
        if (globs==null) {
            return true;
        }
        return globs.accept(new File(ftpFile.getName()));
    }
}