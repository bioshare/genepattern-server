package org.genepattern.server.dm.userupload.dao;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.genepattern.server.database.BaseDAO;
import org.genepattern.server.database.HibernateUtil;
import org.genepattern.server.dm.GpFilePath;
import org.hibernate.Query;
import org.hibernate.criterion.Example;

public class UserUploadDao extends BaseDAO {
    
    /**
     * @param userId
     * @param gpFileObj
     * @return a managed UserUpload instance or null of no matching path was found for the user.
     */
    public UserUpload selectUserUpload(String userId, GpFilePath gpFileObj) {
        return selectUserUpload(userId, gpFileObj.getRelativeFile());
    }

    /**
     * Get the user_upload record from the DB for the given userId and the file path relative to their upload directory.
     * 
     * @param userId
     * @param relativeFile
     * @return null if no matching item in the DB
     */
    public UserUpload selectUserUpload(String userId, File relativeFile) {
        String hql = "from "+UserUpload.class.getName()+" uu where uu.userId = :userId and path = :path";
        Query query = HibernateUtil.getSession().createQuery( hql );
        query.setString("userId", userId);
        query.setString("path", relativeFile.getPath());
        List rval = query.list();
        if (rval != null && rval.size() == 1) {
            return (UserUpload) rval.get(0);
        }
        return null;
    }

    /**
     * Get all user upload files for the given user.
     * @param userId
     * @return
     */
    public List<UserUpload> selectAllUserUpload(String userId) {
        if (userId == null) return Collections.emptyList();
        
        UserUpload ex = new UserUpload();
        ex.setUserId( userId );
        List results = HibernateUtil.getSession().createCriteria( UserUpload.class ).add( Example.create( ex ) ).list();
        return results;
    }
    
    public int deleteUserUpload(String userId, GpFilePath gpFileObj) {
        String hql = "delete UploadFile uf where uf.userId = :userId and uf.path = :path";
        Query query = HibernateUtil.getSession().createQuery( hql );
        query.setString("userId", userId);
        query.setString("path", gpFileObj.getRelativePath());
        int numDeleted = query.executeUpdate();
        return numDeleted;
    }

}
