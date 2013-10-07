/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011-2013
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.archive.fs.impl;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;

import org.dcm4che.net.Status;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.StringUtils;
import org.dcm4chee.archive.entity.Availability;
import org.dcm4chee.archive.entity.FileSystem;
import org.dcm4chee.archive.entity.FileSystemStatus;
import org.dcm4chee.archive.fs.FileSystemService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class FileSystemServiceImpl implements FileSystemService {

    private EntityManagerFactory emf;

    public void setEntityManagerFactory(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public FileSystem selectStorageFileSystem(String groupID, String defaultURI)
            throws DicomServiceException {
        EntityManager em = emf.createEntityManager();
        try {
            return selectStorageFileSystem(em, groupID, defaultURI);
        } finally {
            em.close();
        }
    }

    private FileSystem selectStorageFileSystem(EntityManager em,
            String groupID, String defaultURI) throws DicomServiceException {
        TypedQuery<FileSystem> selectCurFileSystem =
                em.createNamedQuery(FileSystem.FIND_BY_GROUP_ID_AND_STATUS, FileSystem.class)
                .setParameter(1, groupID)
                .setParameter(2, FileSystemStatus.RW);
        try {
            return selectCurFileSystem.getSingleResult();
        } catch (NoResultException e) {
            List<FileSystem> resultList = 
                    em.createNamedQuery(FileSystem.FIND_BY_GROUP_ID, FileSystem.class)
                        .setParameter(1, groupID)
                        .getResultList();
            for (FileSystem fs : resultList) {
                if (fs.getStatus() == FileSystemStatus.Rw) {
                    fs.setStatus(FileSystemStatus.RW);
                    em.flush();
                    return fs;
                }
            }
            if (resultList.isEmpty() && defaultURI != null) {
                return initFileSystem(em, groupID, defaultURI);
            }
            throw new DicomServiceException(Status.OutOfResources,
                    "No writeable File System in File System Group " + groupID);
        }
    }

    private FileSystem initFileSystem(EntityManager em, String groupID,
            String defaultURI) {
        FileSystem fs = new FileSystem();
        fs.setGroupID(groupID);
        fs.setURI(StringUtils.replaceSystemProperties(defaultURI));
        fs.setAvailability(Availability.ONLINE);
        fs.setStatus(FileSystemStatus.RW);
        try {
            em.persist(fs);
            em.flush();
            return fs;
        } catch (PersistenceException e) {
            throw e;
        }
    }


}
