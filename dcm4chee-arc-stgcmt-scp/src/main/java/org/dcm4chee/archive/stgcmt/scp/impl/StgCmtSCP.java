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

package org.dcm4chee.archive.stgcmt.scp.impl;

import java.io.IOException;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;

import org.dcm4che.conf.api.ConfigurationNotFoundException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Commands;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.DimseRSP;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.Status;
import org.dcm4che.net.TransferCapability;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.pdu.RoleSelection;
import org.dcm4che.net.service.DicomService;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.net.service.DicomServiceRegistry;
import org.dcm4chee.archive.ArchiveService;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.jms.JmsService;
import org.dcm4chee.archive.jms.MessageCreator;
import org.dcm4chee.archive.stgcmt.StgCmtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class StgCmtSCP extends DicomService implements MessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(StgCmtSCP.class);

    private StgCmtService stgCmtService;

    private ArchiveService archiveService; 

    private JmsService jmsService; 

    private DicomServiceRegistry registry;

    private Session jmsSession;

    private Queue stgcmtSCPQueue;

    public StgCmtSCP() {
        super(UID.StorageCommitmentPushModelSOPClass);
    }

    public void setArchiveService(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    public void setStgCmtService(StgCmtService stgCmtService) {
        this.stgCmtService = stgCmtService;
    }

    public void setJmsService(JmsService jmsService) {
        this.jmsService = jmsService;
    }

    public void init() throws JMSException {
        registry = archiveService.getServiceRegistry();
        registry.addDicomService(this);
        jmsSession = jmsService.createSession();
        stgcmtSCPQueue = jmsSession.createQueue("stgcmtSCPQueue");
        jmsSession.createConsumer(stgcmtSCPQueue).setMessageListener(this);
    }

    public void destroy() throws JMSException {
        if (registry != null) {
            registry.removeDicomService(this);
            registry = null;
        }
        if (jmsSession != null) {
            jmsSession.close();
            jmsSession = null;
        }
    }

    @Override
    public void onDimseRQ(Association as, PresentationContext pc, Dimse dimse,
            Attributes rq, Attributes actionInfo) throws IOException {
        if (dimse != Dimse.N_ACTION_RQ)
            throw new DicomServiceException(Status.UnrecognizedOperation);

        int actionTypeID = rq.getInt(Tag.ActionTypeID, 0);
        if (actionTypeID != 1)
            throw new DicomServiceException(Status.NoSuchActionType)
                        .setActionTypeID(actionTypeID);

        Attributes rsp = Commands.mkNActionRSP(rq, Status.Success);
        String localAET = as.getLocalAET();
        String remoteAET = as.getRemoteAET();
        try {
            ApplicationEntity ae = as.getApplicationEntity();
            ApplicationEntity remoteAE = archiveService
                    .findApplicationEntity(remoteAET);
            ae.findCompatibelConnection(remoteAE);
            Attributes eventInfo = stgCmtService.calculateResult(actionInfo);
            ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
            scheduleNEventReport(localAET, remoteAET, eventInfo, 0,
                    aeExt != null ? aeExt.getStorageCommitmentDelay() * 1000L : 0);
        } catch (IncompatibleConnectionException e) {
            throw new DicomServiceException(Status.ProcessingFailure,
                    "No compatible connection to " + remoteAET);
        } catch (ConfigurationNotFoundException e) {
            throw new DicomServiceException(Status.ProcessingFailure,
                    "Unknown Calling AET: " + remoteAET);
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
        as.tryWriteDimseRSP(pc, rsp, null);
    }

    @Override
    public void onMessage(Message msg) {
        try {
            process((ObjectMessage) msg);
        } catch (Throwable th) {
            LOG.warn("Failed to process " + msg, th);
        }
    }

    private void process(ObjectMessage msg) throws JMSException {
        String remoteAET = msg.getStringProperty("RemoteAET");
        String localAET = msg.getStringProperty("LocalAET");
        int retries = msg.getIntProperty("Retries");
        Attributes eventInfo = (Attributes) msg.getObject();
        ApplicationEntity localAE = archiveService.getDevice()
                .getApplicationEntity(localAET);
        if (localAE == null) {
            LOG.warn("Failed to return Storage Commitment Result to {} - no such local AE: {}",
                    remoteAET, localAET);
            return;
        }
        TransferCapability tc = localAE.getTransferCapabilityFor(
                UID.StorageCommitmentPushModelSOPClass, TransferCapability.Role.SCP);
        if (tc == null) {
            LOG.warn("Failed to return Storage Commitment Result to {} - "
                   + "local AE: {} does not support Storage Commitment Push Model in SCP Role",
                    remoteAET, localAET);
            return;
        }
        AAssociateRQ aarq = new AAssociateRQ();
        aarq.addPresentationContext(
                        new PresentationContext(
                                1,
                                UID.StorageCommitmentPushModelSOPClass,
                                tc.getTransferSyntaxes()));
        aarq.addRoleSelection(
                new RoleSelection(UID.StorageCommitmentPushModelSOPClass, false, true));
        try {
            ApplicationEntity remoteAE = archiveService
                    .findApplicationEntity(remoteAET);
            Association as = localAE.connect(remoteAE, aarq);
            DimseRSP neventReport = as.neventReport(
                    UID.StorageCommitmentPushModelSOPClass,
                    UID.StorageCommitmentPushModelSOPInstance,
                    eventTypeId(eventInfo),
                    eventInfo, null);
            neventReport.next();
            try {
                as.release();
            } catch (IOException e) {
                LOG.info("{}: Failed to release association to {}", as, remoteAET);
            }
        } catch (Exception e) {
            ArchiveAEExtension aeExt = localAE.getAEExtension(ArchiveAEExtension.class);
            if (aeExt != null && retries < aeExt.getStorageCommitmentMaxRetries()) {
                int delay = aeExt.getStorageCommitmentRetryInterval();
                LOG.info("Failed to return Storage Commitment Result to {} - retry in {}s: {}",
                        remoteAET, delay, e);
                scheduleNEventReport(localAET, remoteAET, eventInfo, retries + 1, delay * 1000L);
            } else {
                LOG.warn("Failed to return Storage Commitment Result to {}: {}",
                        remoteAET, e);
            }
        }
    }

    private int eventTypeId(Attributes eventInfo) {
        return eventInfo.containsValue(Tag.FailedSOPSequence) ? 2 : 1;
    }

    private void scheduleNEventReport(final String localAET, final String remoteAET,
            final Attributes eventInfo, final int retries, long delay)
            throws JMSException {
        jmsService.sendMessage(stgcmtSCPQueue, new MessageCreator() {

            @Override
            public Message createMessage(Session session) throws JMSException {
                ObjectMessage msg = session.createObjectMessage(eventInfo);
                msg.setStringProperty("LocalAET", localAET);
                msg.setStringProperty("RemoteAET", remoteAET);
                msg.setIntProperty("Retries", retries);
                return msg;
            }},
         delay);
    }

}
