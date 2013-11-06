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
 * Portions created by the Initial Developer are Copyright (C) 2011
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

package org.dcm4chee.archive.retrieve.scp.impl;

import static org.dcm4che.net.service.BasicRetrieveTask.Service.C_GET;

import java.util.EnumSet;
import java.util.List;

import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.IDWithIssuer;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.QueryOption;
import org.dcm4che.net.Status;
import org.dcm4che.net.pdu.ExtendedNegotiation;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicCGetSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.net.service.DicomServiceRegistry;
import org.dcm4che.net.service.InstanceLocator;
import org.dcm4che.net.service.QueryRetrieveLevel;
import org.dcm4che.net.service.RetrieveTask;
import org.dcm4chee.archive.ArchiveService;
import org.dcm4chee.archive.common.QueryParam;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.query.common.QueryPatientNamesService;
import org.dcm4chee.archive.retrieve.RetrieveService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class CGetSCP extends BasicCGetSCP {

    private final String[] qrLevels;
    private final QueryRetrieveLevel rootLevel;
    private final boolean withoutBulkData;

    private ArchiveService archiveService;
    private RetrieveService retrieveService;
    private QueryPatientNamesService queryPatientNamesService;

    private DicomServiceRegistry registry = null; 

    public CGetSCP(String sopClass, String... qrLevels) {
        super(sopClass);
        this.qrLevels = qrLevels;
        this.rootLevel = QueryRetrieveLevel.valueOf(qrLevels[0]);
        this.withoutBulkData = sopClass.equals(
                UID.CompositeInstanceRetrieveWithoutBulkDataGET);
    }

    public void setArchiveService(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    public void setRetrieveService(RetrieveService retrieveService) {
        this.retrieveService = retrieveService;
    }

    public void setQueryPatientNamesService(QueryPatientNamesService queryPatientNamesService) {
        this.queryPatientNamesService = queryPatientNamesService;
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
    protected RetrieveTask calculateMatches(Association as, PresentationContext pc,
            Attributes rq, Attributes keys) throws DicomServiceException {
        QueryRetrieveLevel level = QueryRetrieveLevel.valueOf(keys, qrLevels);
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        ExtendedNegotiation extNeg = as.getAAssociateAC().getExtNegotiationFor(cuid);
        EnumSet<QueryOption> queryOpts = QueryOption.toOptions(extNeg);
        boolean relational = queryOpts.contains(QueryOption.RELATIONAL);
        level.validateRetrieveKeys(keys, rootLevel, relational);

        ApplicationEntity ae = as.getApplicationEntity();
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        try {
            QueryParam queryParam = QueryParam.valueOf(ae, queryOpts,
                    accessControlIDs());
            ApplicationEntity sourceAE = null;
            try {
                sourceAE = archiveService
                        .findApplicationEntity(as.getRemoteAET());
                queryParam.setDefaultIssuer(sourceAE.getDevice());
            } catch (ConfigurationException e) {
            }
            IDWithIssuer pid = IDWithIssuer.fromPatientIDWithIssuer(keys);
            if (pid != null && pid.getIssuer() == null)
                pid.setIssuer(queryParam.getDefaultIssuerOfPatientID());
//            IDWithIssuer[] pids = Archive.getInstance().pixQuery(ae, pid);
            IDWithIssuer[] pids = pid != null 
                    ? new IDWithIssuer[]{ pid }
                    : IDWithIssuer.EMPTY;
            List<InstanceLocator> matches = 
                    retrieveService.calculateMatches(pids, keys, queryParam);
            RetrieveTaskImpl retrieveTask = new RetrieveTaskImpl(
                    C_GET, as, pc, rq, matches, pids,
                    queryPatientNamesService, withoutBulkData);
            if (sourceAE != null)
                retrieveTask.setDestinationDevice(sourceAE.getDevice());
            retrieveTask.setSendPendingRSP(aeExt.isSendPendingCGet());
            retrieveTask.setReturnOtherPatientIDs(aeExt.isReturnOtherPatientIDs());
            retrieveTask.setReturnOtherPatientNames(aeExt.isReturnOtherPatientNames());
            return retrieveTask;
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToCalculateNumberOfMatches, e);
        }
    }

    private String[] accessControlIDs() {
        // TODO Auto-generated method stub
        return null;
    }

}
