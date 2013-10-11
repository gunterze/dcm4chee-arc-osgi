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

package org.dcm4chee.archive.store.impl;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.DicomInputStream.IncludeBulkData;
import org.dcm4che.io.DicomOutputStream;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.PDVInputStream;
import org.dcm4che.net.Status;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicCStoreSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.AttributesFormat;
import org.dcm4chee.archive.ArchiveService;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.entity.FileSystem;
import org.dcm4chee.archive.fs.FileSystemService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class CStoreSCP extends BasicCStoreSCP {

    private ArchiveService archiveService;
    private FileSystemService fileSystemService;

    public CStoreSCP() {
        super("*");
    }

    public ArchiveService getArchiveService() {
        return archiveService;
    }

    public void setArchiveService(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    public FileSystemService getFileSystemService() {
        return fileSystemService;
    }

    public void setFileSystemService(FileSystemService fileSystemService) {
        this.fileSystemService = fileSystemService;
    }

    public void init() {
        archiveService.getServiceRegistry().addDicomService(this);
    }

    public void destroy() {
        archiveService.getServiceRegistry().removeDicomService(this);
    }

    @Override
    protected void store(Association as, PresentationContext pc,
            Attributes rq, PDVInputStream data, Attributes rsp)
            throws IOException {

        try {
            String sourceAET = as.getRemoteAET();
            String cuid = rq.getString(Tag.AffectedSOPClassUID);
            String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
            String tsuid = pc.getTransferSyntax();
            Attributes fmi = as.createFileMetaInformation(iuid, cuid, tsuid);
    
            ArchiveAEExtension aeExt = archiveAEExtensionOf(as);
            FileSystem fs = selectStorageFileSystem(as, aeExt);
            Path fsPath = fs.getPath();
            Path spoolPath = createSpoolPath(fsPath, aeExt, sourceAET, cuid);
            File spoolFile = spoolPath.toFile();
            MessageDigest digest = messageDigestOf(aeExt);
            storeTo(as, fmi, data, spoolFile, digest);
            Attributes attrs = parse(spoolFile);
            Path filePath = move(spoolPath, fsPath, aeExt, attrs);
            // TO DO
        } catch (Exception e) {
            if (e instanceof DicomServiceException) {
                throw (DicomServiceException) e;
            } else {
                throw new DicomServiceException(Status.ProcessingFailure, e);
            }
        }
    }

    private Path move(Path spoolPath, Path fsPath, ArchiveAEExtension aeExt,
            Attributes attrs) throws Exception{
        AttributesFormat filePathFormat = aeExt.getStorageFilePathFormat();
        if (filePathFormat == null)
            throw new DicomServiceException(
                    Status.ProcessingFailure,
                    "No StorageFilePathFormat configured for "
                            + aeExt.getApplicationEntity().getAETitle());

        Path filePath;
        synchronized (filePathFormat) {
            filePath = fsPath.resolve(filePathFormat.format(attrs));
        }
        Files.createDirectories(filePath.getParent());
        for (;;) {
            try {
                return Files.move(spoolPath, filePath);
            } catch (FileAlreadyExistsException e) {
                filePath = filePath.resolveSibling(
                        filePath.getFileName().toString() + '-');
            }
        }
    }

    private Attributes parse(File file) throws IOException {
        try (DicomInputStream in = new DicomInputStream(file)) {
            in.setIncludeBulkData(IncludeBulkData.URI);
            return in.readDataset(-1, -1);
        }
    }

    private void storeTo(Association as, Attributes fmi, PDVInputStream data,
            File file, MessageDigest digest) throws Exception {
        try (DicomOutputStream out = digest == null
                ? new DicomOutputStream(file)
                : new DicomOutputStream(
                        new BufferedOutputStream(
                                new DigestOutputStream(
                                        new FileOutputStream(file), digest)),
                            UID.ExplicitVRLittleEndian)) {
            out.writeFileMetaInformation(fmi);
            data.copyTo(out);
        }
    }

    private Path createSpoolPath(Path fsPath, ArchiveAEExtension aeExt,
            String sourceAET, String cuid) throws DicomServiceException {
        try {
            String spoolDirectoryPath = aeExt.getSpoolDirectoryPath();
            if (spoolDirectoryPath == null)
                return Files.createTempFile("dcm", ".dcm");
    
            Path dir = fsPath.resolve(spoolDirectoryPath).resolve(sourceAET).resolve(cuid);
            return Files.createTempFile(Files.createDirectories(dir), "dcm", ".dcm");
        } catch (IOException e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
    }

    private MessageDigest messageDigestOf(ArchiveAEExtension aeExt)
            throws NoSuchAlgorithmException {
        String algorithm = aeExt.getDigestAlgorithm();
        return algorithm != null
                ? MessageDigest.getInstance(algorithm)
                : null;
    }

    private ArchiveAEExtension archiveAEExtensionOf(Association as)
            throws DicomServiceException {
        ApplicationEntity ae = as.getApplicationEntity();
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        if (aeExt == null)
            throw new DicomServiceException(
                    Status.ProcessingFailure,
                    "No ArchiveAEExtension configured for "
                            + ae.getAETitle());
        return aeExt;
    }


    private FileSystem selectStorageFileSystem(Association as,
            ArchiveAEExtension aeExt) throws Exception {
        FileSystem fs = (FileSystem) as.getProperty(FileSystem.class.getName());
        if (fs == null) {
            fs = fileSystemService.selectStorageFileSystem(
                    aeExt.getFileSystemGroupID(),
                    aeExt.getInitFileSystemURI());
            as.setProperty(FileSystem.class.getName(), fs);
        }
        return fs;
    }


}
