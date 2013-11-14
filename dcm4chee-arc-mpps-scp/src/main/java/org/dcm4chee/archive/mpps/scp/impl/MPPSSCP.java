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
 * Portions created by the Initial Developer are Copyright (C) 2012
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

package org.dcm4chee.archive.mpps.scp.impl;

import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Issuer;
import org.dcm4che.data.Tag;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Status;
import org.dcm4che.net.service.BasicMPPSSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.net.service.DicomServiceRegistry;
import org.dcm4chee.archive.ArchiveService;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.entity.PerformedProcedureStep;
import org.dcm4chee.archive.mpps.MPPSService;
import org.dcm4chee.archive.mpps.scu.MPPSSCU;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class MPPSSCP extends BasicMPPSSCP {

    private ArchiveService archiveService;
    private MPPSService mppsService;
    private MPPSSCU mppsSCU;
    private DicomServiceRegistry registry;

    public void setArchiveService(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    public void setMppsService(MPPSService mppsService) {
        this.mppsService = mppsService;
    }

    public void setMppsSCU(MPPSSCU mppsSCU) {
        this.mppsSCU = mppsSCU;
    }

    public void init() {
        registry = archiveService.getServiceRegistry();
        registry.addDicomService(this);
    }

    public void destroy() {
        if (registry != null) {
            registry.removeDicomService(this);
            registry = null;
        }
    }
    @Override
    protected Attributes create(Association as, Attributes rq,
            Attributes rqAttrs, Attributes rsp) throws DicomServiceException {
        String localAET = as.getLocalAET();
        String sourceAET = as.getRemoteAET();
        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
        ApplicationEntity ae = as.getApplicationEntity();
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        try {
//            try {
//                ApplicationEntity sourceAE = archiveService
//                        .findApplicationEntity(sourceAET);
//                Supplements.supplementMPPS(rqAttrs, sourceAE.getDevice());
//            } catch (ConfigurationNotFoundException e) {
//            }
            mppsService.createPerformedProcedureStep(iuid , rqAttrs,
                    aeExt.getStoreParam());
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
        for (String remoteAET : aeExt.getForwardMPPSDestinations())
            if (matchIssuerOfPatientID(remoteAET, rqAttrs))
                mppsSCU.createMPPS(localAET, remoteAET, iuid, rqAttrs);
        return null;
    }

    private boolean matchIssuerOfPatientID(String remoteAET, Attributes rqAttrs) {
        Issuer issuer = Issuer.fromIssuerOfPatientID(rqAttrs);
        if (issuer == null)
            return true;

        try {
            ApplicationEntity remoteAE = archiveService
                    .findApplicationEntity(remoteAET);
            return issuer.matches(remoteAE.getDevice().getIssuerOfPatientID());
        } catch (ConfigurationException e) {
            return true;
        }
    }

    @Override
    protected Attributes set(Association as, Attributes rq, Attributes rqAttrs,
            Attributes rsp) throws DicomServiceException {
        String localAET = as.getLocalAET();
        String iuid = rq.getString(Tag.RequestedSOPInstanceUID);
        ApplicationEntity ae = as.getApplicationEntity();
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        PerformedProcedureStep pps;
        try {
            pps = mppsService.updatePerformedProcedureStep(iuid, rqAttrs,
                    aeExt.getStoreParam());
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
        for (String remoteAET : aeExt.getForwardMPPSDestinations())
            if (matchIssuerOfPatientID(remoteAET, pps.getPatient().getAttributes()))
                mppsSCU.updateMPPS(localAET, remoteAET, iuid, rqAttrs);
//        List<Attributes> ians = ppsWithIAN.ians;
//        Archive r = Archive.getInstance();
//        if (ians != null && !ians.isEmpty())
//            for (String remoteAET1 : ae.getAEExtension(ArchiveAEExtension.class)
//                    .getIANDestinations())
//                for (Attributes ian : ians)
//                    r.scheduleIAN(ae.getAETitle(), remoteAET1, ian);
        return null;
    }

}
