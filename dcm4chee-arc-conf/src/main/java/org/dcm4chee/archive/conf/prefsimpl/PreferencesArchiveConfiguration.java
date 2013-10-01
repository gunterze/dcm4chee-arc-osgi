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

package org.dcm4chee.archive.conf.prefsimpl;

import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.prefs.PreferencesDicomConfigurationExtension;
import org.dcm4che.conf.prefs.PreferencesUtils;
import org.dcm4che.conf.prefs.hl7.PreferencesHL7ConfigurationExtension;
// import org.dcm4che.conf.prefs.imageio.PreferencesCompressionRulesConfiguration;
import org.dcm4che.data.Code;
import org.dcm4che.data.ValueSelector;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Device;
import org.dcm4che.net.hl7.HL7Application;
import org.dcm4che.util.AttributesFormat;
import org.dcm4che.util.TagUtils;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.conf.ArchiveDeviceExtension;
import org.dcm4chee.archive.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.archive.conf.Entity;
import org.dcm4chee.archive.conf.StoreDuplicate;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class PreferencesArchiveConfiguration
    extends PreferencesDicomConfigurationExtension
    implements PreferencesHL7ConfigurationExtension {

    @Override
    protected void storeTo(Device device, Preferences prefs) {
        ArchiveDeviceExtension arcDev =
                device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcDev == null)
            return;

        prefs.putBoolean("dcmArchiveDevice", true);
        PreferencesUtils.storeNotNull(prefs, "dcmIncorrectWorklistEntrySelectedCode",
                arcDev.getIncorrectWorklistEntrySelectedCode());
        PreferencesUtils.storeNotNull(prefs, "dcmRejectedForQualityReasonsCode",
                arcDev.getRejectedForQualityReasonsCode());
        PreferencesUtils.storeNotNull(prefs, "dcmRejectedForPatientSafetyReasonsCode",
                arcDev.getRejectedForPatientSafetyReasonsCode());
        PreferencesUtils.storeNotNull(prefs, "dcmIncorrectModalityWorklistEntryCode",
                arcDev.getIncorrectModalityWorklistEntryCode());
        PreferencesUtils.storeNotNull(prefs, "dcmDataRetentionPeriodExpiredCode",
                arcDev.getDataRetentionPeriodExpiredCode());
        PreferencesUtils.storeNotNull(prefs, "dcmFuzzyAlgorithmClass",
                arcDev.getFuzzyAlgorithmClass());
        PreferencesUtils.storeNotDef(prefs, "dcmConfigurationStaleTimeout",
                arcDev.getConfigurationStaleTimeout(), 0);
        PreferencesUtils.storeNotDef(prefs, "dcmWadoAttributesStaleTimeout",
                arcDev.getWadoAttributesStaleTimeout(), 0);
    }

    @Override
    protected void storeChilds(Device device, Preferences deviceNode) {
        ArchiveDeviceExtension arcDev =
                device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcDev == null)
            return;

        Preferences afsNode = deviceNode.node("dcmAttributeFilter");
        for (Entity entity : Entity.values())
            storeTo(arcDev.getAttributeFilter(entity), afsNode.node(entity.name()));
    }

    @Override
    protected void storeChilds(ApplicationEntity ae, Preferences aeNode) {
        ArchiveAEExtension arcAE = ae.getAEExtension(ArchiveAEExtension.class);
        if (arcAE == null)
            return;

        config.store(arcAE.getAttributeCoercions(), aeNode);
//        PreferencesCompressionRulesConfiguration
//                .store(arcAE.getCompressionRules(), aeNode);
        storeStoreDuplicates(arcAE.getStoreDuplicates(), aeNode);
    }

    private void storeStoreDuplicates(List<StoreDuplicate> sds, Preferences aeNode) {
        Preferences sdsNode = aeNode.node("dcmStoreDuplicate");
        int index = 1;
        for (StoreDuplicate sd : sds)
            storeTo(sd, sdsNode.node("" + index ++));
    }

    private void storeTo(StoreDuplicate sd, Preferences prefs) {
        PreferencesUtils.storeNotNull(prefs, "dcmStoreDuplicateCondition", sd.getCondition());
        PreferencesUtils.storeNotNull(prefs, "dcmStoreDuplicateAction", sd.getAction());
    }

    private static void storeTo(AttributeFilter filter, Preferences prefs) {
        storeTags(prefs, "dcmTag", filter.getSelection());
        PreferencesUtils.storeNotNull(prefs, "dcmCustomAttribute1", filter.getCustomAttribute1());
        PreferencesUtils.storeNotNull(prefs, "dcmCustomAttribute2", filter.getCustomAttribute2());
        PreferencesUtils.storeNotNull(prefs, "dcmCustomAttribute3", filter.getCustomAttribute3());
    }

    private static void storeTags(Preferences prefs, String key, int[] tags) {
        if (tags.length != 0) {
            int count = 0;
            for (int tag : tags)
                prefs.put(key + '.' + (++count), TagUtils.toHexString(tag));
            prefs.putInt(key + ".#", count);
        }
    }

    @Override
    protected void storeTo(ApplicationEntity ae, Preferences prefs) {
        ArchiveAEExtension arcAE = ae.getAEExtension(ArchiveAEExtension.class);
        if (arcAE == null)
            return;

        prefs.putBoolean("dcmArchiveNetworkAE", true);
        PreferencesUtils.storeNotNull(prefs, "dcmFileSystemGroupID", arcAE.getFileSystemGroupID());
        PreferencesUtils.storeNotNull(prefs, "dcmInitFileSystemURI", arcAE.getInitFileSystemURI());
        PreferencesUtils.storeNotNull(prefs, "dcmSpoolDirectoryPath", arcAE.getSpoolDirectoryPath());
        PreferencesUtils.storeNotNull(prefs, "dcmStorageFilePathFormat", arcAE.getStorageFilePathFormat());
        PreferencesUtils.storeNotNull(prefs, "dcmDigestAlgorithm", arcAE.getDigestAlgorithm());
        PreferencesUtils.storeNotNull(prefs, "dcmExternalRetrieveAET", arcAE.getExternalRetrieveAET());
        PreferencesUtils.storeNotEmpty(prefs, "dcmRetrieveAET", arcAE.getRetrieveAETs());
        PreferencesUtils.storeNotDef(prefs, "dcmMatchUnknown", arcAE.isMatchUnknown(), false);
        PreferencesUtils.storeNotDef(prefs, "dcmSendPendingCGet", arcAE.isSendPendingCGet(), false);
        PreferencesUtils.storeNotDef(prefs, "dcmSendPendingCMoveInterval", arcAE.getSendPendingCMoveInterval(), 0);
        PreferencesUtils.storeNotDef(prefs, "dcmSuppressWarningCoercionOfDataElements",
                arcAE.isSuppressWarningCoercionOfDataElements(), false);
        PreferencesUtils.storeNotDef(prefs, "dcmStoreOriginalAttributes",
                arcAE.isStoreOriginalAttributes(), false);
        PreferencesUtils.storeNotDef(prefs, "dcmPreserveSpoolFileOnFailure",
                arcAE.isPreserveSpoolFileOnFailure(), false);
        PreferencesUtils.storeNotNull(prefs, "dcmModifyingSystem", arcAE.getModifyingSystem());
        PreferencesUtils.storeNotDef(prefs, "dcmStgCmtDelay", arcAE.getStorageCommitmentDelay(), 0);
        PreferencesUtils.storeNotDef(prefs, "dcmStgCmtMaxRetries", arcAE.getStorageCommitmentMaxRetries(), 0);
        PreferencesUtils.storeNotDef(prefs, "dcmStgCmtRetryInterval", arcAE.getStorageCommitmentRetryInterval(),
                    ArchiveAEExtension.DEF_RETRY_INTERVAL);
        PreferencesUtils.storeNotEmpty(prefs, "dcmFwdMppsDestination", arcAE.getForwardMPPSDestinations());
        PreferencesUtils.storeNotDef(prefs, "dcmFwdMppsMaxRetries", arcAE.getForwardMPPSMaxRetries(), 0);
        PreferencesUtils.storeNotDef(prefs, "dcmFwdMppsRetryInterval", arcAE.getForwardMPPSRetryInterval(),
                    ArchiveAEExtension.DEF_RETRY_INTERVAL);
        PreferencesUtils.storeNotEmpty(prefs, "dcmIanDestination", arcAE.getIANDestinations());
        PreferencesUtils.storeNotDef(prefs, "dcmIanMaxRetries", arcAE.getIANMaxRetries(), 0);
        PreferencesUtils.storeNotDef(prefs, "dcmIanRetryInterval", arcAE.getIANRetryInterval(),
                    ArchiveAEExtension.DEF_RETRY_INTERVAL);
        PreferencesUtils.storeNotDef(prefs, "dcmReturnOtherPatientIDs", arcAE.isReturnOtherPatientIDs(), false);
        PreferencesUtils.storeNotDef(prefs, "dcmReturnOtherPatientNames", arcAE.isReturnOtherPatientNames(), false);
        PreferencesUtils.storeNotDef(prefs, "dcmShowRejectedInstances", arcAE.isShowRejectedInstances(), false);
        PreferencesUtils.storeNotNull(prefs, "hl7PIXConsumerApplication", arcAE.getLocalPIXConsumerApplication());
        PreferencesUtils.storeNotNull(prefs, "hl7PIXManagerApplication", arcAE.getRemotePIXManagerApplication());
        PreferencesUtils.storeNotDef(prefs, "dcmQidoMaxNumberOfResults", arcAE.getQIDOMaxNumberOfResults(), 0);
    }

    @Override
    public void storeTo(HL7Application hl7App, Preferences prefs) {
        ArchiveHL7ApplicationExtension arcHL7App =
                hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        if (arcHL7App == null)
            return;

        prefs.putBoolean("dcmArchiveHL7Application", true);
        PreferencesUtils.storeNotEmpty(prefs, "labeledURI", arcHL7App.getTemplatesURIs());
    }

    @Override
    protected void loadFrom(Device device, Preferences prefs)
            throws CertificateException, BackingStoreException {
        if (!prefs.getBoolean("dcmArchiveDevice", false))
            return;

        ArchiveDeviceExtension arcdev = new ArchiveDeviceExtension();
        device.addDeviceExtension(arcdev);

        arcdev.setIncorrectWorklistEntrySelectedCode(new Code(
                prefs.get("dcmIncorrectWorklistEntrySelectedCode", null)));
        arcdev.setRejectedForQualityReasonsCode(new Code(
                prefs.get("dcmRejectedForQualityReasonsCode", null)));
        arcdev.setRejectedForPatientSafetyReasonsCode(new Code(
                prefs.get("dcmRejectedForPatientSafetyReasonsCode", null)));
        arcdev.setIncorrectModalityWorklistEntryCode(new Code(
                prefs.get("dcmIncorrectModalityWorklistEntryCode", null)));
        arcdev.setDataRetentionPeriodExpiredCode(new Code(
                prefs.get("dcmDataRetentionPeriodExpiredCode", null)));
        arcdev.setFuzzyAlgorithmClass(prefs.get("dcmFuzzyAlgorithmClass", null));
        arcdev.setConfigurationStaleTimeout(
                prefs.getInt("dcmConfigurationStaleTimeout", 0));
        arcdev.setWadoAttributesStaleTimeout(
                prefs.getInt("dcmWadoAttributesStaleTimeout", 0));
    }

    @Override
    protected void loadChilds(Device device, Preferences deviceNode)
            throws BackingStoreException, ConfigurationException {
        ArchiveDeviceExtension arcdev =
                device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcdev == null)
            return;

        loadAttributeFilters(arcdev, deviceNode);
    }

    @Override
    protected void loadFrom(ApplicationEntity ae, Preferences prefs) {
        if (!prefs.getBoolean("dcmArchiveNetworkAE", false))
            return;

        ArchiveAEExtension arcae = new ArchiveAEExtension();
        ae.addAEExtension(arcae);
        arcae.setFileSystemGroupID(prefs.get("dcmFileSystemGroupID", null));
        arcae.setInitFileSystemURI(prefs.get("dcmInitFileSystemURI", null));
        arcae.setSpoolDirectoryPath(prefs.get("dcmSpoolDirectoryPath", null));
        arcae.setStorageFilePathFormat(
                AttributesFormat.valueOf(
                        prefs.get("dcmStorageFilePathFormat", null)));
        arcae.setDigestAlgorithm(prefs.get("dcmDigestAlgorithm", null));
        arcae.setExternalRetrieveAET(prefs.get("dcmExternalRetrieveAET", null));
        arcae.setRetrieveAETs(PreferencesUtils.stringArray(prefs, "dcmRetrieveAET"));
        arcae.setMatchUnknown(prefs.getBoolean("dcmMatchUnknown", false));
        arcae.setSendPendingCGet(prefs.getBoolean("dcmSendPendingCGet", false));
        arcae.setSendPendingCMoveInterval(prefs.getInt("dcmSendPendingCMoveInterval", 0));
        arcae.setSuppressWarningCoercionOfDataElements(
                prefs.getBoolean("dcmSuppressWarningCoercionOfDataElements", false));
        arcae.setPreserveSpoolFileOnFailure(
                prefs.getBoolean("dcmPreserveSpoolFileOnFailure", false));
        arcae.setStoreOriginalAttributes(
                prefs.getBoolean("dcmStoreOriginalAttributes", false));
        arcae.setModifyingSystem(prefs.get("dcmModifyingSystem", null));
        arcae.setStorageCommitmentDelay(prefs.getInt("dcmStgCmtDelay", 0));
        arcae.setStorageCommitmentMaxRetries(prefs.getInt("dcmStgCmtMaxRetries", 0));
        arcae.setStorageCommitmentRetryInterval(prefs.getInt("dcmStgCmtRetryInterval",
                ArchiveAEExtension.DEF_RETRY_INTERVAL));
        arcae.setForwardMPPSDestinations(PreferencesUtils.stringArray(prefs, "dcmFwdMppsDestination"));
        arcae.setForwardMPPSMaxRetries(prefs.getInt("dcmFwdMppsMaxRetries", 0));
        arcae.setForwardMPPSRetryInterval(prefs.getInt("dcmFwdMppsRetryInterval",
                ArchiveAEExtension.DEF_RETRY_INTERVAL));
        arcae.setIANDestinations(PreferencesUtils.stringArray(prefs, "dcmIanDestination"));
        arcae.setIANMaxRetries(prefs.getInt("dcmIanMaxRetries", 0));
        arcae.setIANRetryInterval(prefs.getInt("dcmIanRetryInterval",
                ArchiveAEExtension.DEF_RETRY_INTERVAL));
        arcae.setReturnOtherPatientIDs(
                prefs.getBoolean("dcmReturnOtherPatientIDs", false));
        arcae.setReturnOtherPatientNames(
                prefs.getBoolean("dcmReturnOtherPatientNames", false));
        arcae.setShowRejectedInstances(
                prefs.getBoolean("dcmShowRejectedInstances", false));
        arcae.setLocalPIXConsumerApplication(prefs.get("hl7PIXConsumerApplication", null));
        arcae.setRemotePIXManagerApplication(prefs.get("hl7PIXManagerApplication", null));
        arcae.setQIDOMaxNumberOfResults(prefs.getInt("dcmQidoMaxNumberOfResults", 0));
    }

    @Override
    protected void loadChilds(ApplicationEntity ae, Preferences aeNode)
            throws BackingStoreException {
        ArchiveAEExtension arcae = ae.getAEExtension(ArchiveAEExtension.class);
        if (arcae == null)
            return;

        config.load(arcae.getAttributeCoercions(), aeNode);
//        PreferencesCompressionRulesConfiguration
//                .load(arcae.getCompressionRules(), aeNode);
        loadStoreDuplicates(arcae.getStoreDuplicates(), aeNode);
    }

    private void loadStoreDuplicates(List<StoreDuplicate> sds, Preferences aeNode)
            throws BackingStoreException {
        Preferences sdsNode = aeNode.node("dcmStoreDuplicate");
        for (String index : sdsNode.childrenNames())
            sds.add(storeDuplicate(sdsNode.node(index)));
    }

    private StoreDuplicate storeDuplicate(Preferences prefs) {
        return new StoreDuplicate(
                StoreDuplicate.Condition.valueOf(prefs.get("dcmStoreDuplicateCondition", null)),
                StoreDuplicate.Action.valueOf(prefs.get("dcmStoreDuplicateAction", null)));
    }

    @Override
    public void loadFrom(HL7Application hl7App, Preferences prefs) {
        if (!prefs.getBoolean("dcmArchiveHL7Application", false))
            return;

        ArchiveHL7ApplicationExtension arcHL7App =
                new ArchiveHL7ApplicationExtension();
        hl7App.addHL7ApplicationExtension(arcHL7App);
        arcHL7App.setTemplatesURIs(PreferencesUtils.stringArray(prefs, "labeledURI"));
    }

    private static void loadAttributeFilters(ArchiveDeviceExtension device, Preferences deviceNode)
            throws BackingStoreException {
        Preferences afsNode = deviceNode.node("dcmAttributeFilter");
        for (String entity : afsNode.childrenNames()) {
            Preferences acNode = afsNode.node(entity);
            AttributeFilter filter = new AttributeFilter(tags(acNode, "dcmTag"));
            filter.setCustomAttribute1(
                    ValueSelector.valueOf(acNode.get("dcmCustomAttribute1", null)));
            filter.setCustomAttribute2(
                    ValueSelector.valueOf(acNode.get("dcmCustomAttribute2", null)));
            filter.setCustomAttribute3(
                    ValueSelector.valueOf(acNode.get("dcmCustomAttribute3", null)));
            device.setAttributeFilter(
                    Entity.valueOf(entity), filter);
        }
    }

    private static int[] tags(Preferences prefs, String key) {
        int n = prefs.getInt(key + ".#", 0);
        int[] is = new int[n];
        for (int i = 0; i < n; i++)
            is[i] = Integer.parseInt(prefs.get(key + '.' + (i+1), null), 16);
        return is;
    }

    @Override
    protected void storeDiffs(Device a, Device b, Preferences prefs) {
        ArchiveDeviceExtension aa = a.getDeviceExtension(ArchiveDeviceExtension.class);
        ArchiveDeviceExtension bb = b.getDeviceExtension(ArchiveDeviceExtension.class);
        if (aa == null || bb == null)
            return;
        
        PreferencesUtils.storeDiff(prefs, "dcmIncorrectWorklistEntrySelectedCode",
                aa.getIncorrectWorklistEntrySelectedCode(),
                bb.getIncorrectWorklistEntrySelectedCode());
        PreferencesUtils.storeDiff(prefs, "dcmRejectedForQualityReasonsCode",
                aa.getRejectedForQualityReasonsCode(),
                bb.getRejectedForQualityReasonsCode());
        PreferencesUtils.storeDiff(prefs, "dcmRejectedForPatientSafetyReasonsCode",
                aa.getRejectedForPatientSafetyReasonsCode(),
                bb.getRejectedForPatientSafetyReasonsCode());
        PreferencesUtils.storeDiff(prefs, "dcmIncorrectModalityWorklistEntryCode",
                aa.getIncorrectModalityWorklistEntryCode(),
                bb.getIncorrectModalityWorklistEntryCode());
        PreferencesUtils.storeDiff(prefs, "dcmDataRetentionPeriodExpiredCode",
                aa.getDataRetentionPeriodExpiredCode(),
                bb.getDataRetentionPeriodExpiredCode());
        PreferencesUtils.storeDiff(prefs, "dcmFuzzyAlgorithmClass",
                aa.getFuzzyAlgorithmClass(),
                bb.getFuzzyAlgorithmClass());
        PreferencesUtils.storeDiff(prefs, "dcmConfigurationStaleTimeout",
                aa.getConfigurationStaleTimeout(),
                bb.getConfigurationStaleTimeout(),
                0);
        PreferencesUtils.storeDiff(prefs, "dcmWadoAttributesStaleTimeout",
                aa.getWadoAttributesStaleTimeout(),
                bb.getWadoAttributesStaleTimeout(),
                0);
    }

    @Override
    protected void storeDiffs(ApplicationEntity a, ApplicationEntity b,
            Preferences prefs) {
         ArchiveAEExtension aa = a.getAEExtension(ArchiveAEExtension.class);
         ArchiveAEExtension bb = b.getAEExtension(ArchiveAEExtension.class);
         if (aa == null || bb == null)
             return;

         PreferencesUtils.storeDiff(prefs, "dcmFileSystemGroupID",
                 aa.getFileSystemGroupID(),
                 bb.getFileSystemGroupID());
         PreferencesUtils.storeDiff(prefs, "dcmInitFileSystemURI",
                 aa.getInitFileSystemURI(),
                 bb.getInitFileSystemURI());
         PreferencesUtils.storeDiff(prefs, "dcmSpoolDirectoryPath",
                 aa.getSpoolDirectoryPath(),
                 bb.getSpoolDirectoryPath());
         PreferencesUtils.storeDiff(prefs, "dcmStorageFilePathFormat",
                 aa.getStorageFilePathFormat(),
                 bb.getStorageFilePathFormat());
         PreferencesUtils.storeDiff(prefs, "dcmDigestAlgorithm",
                 aa.getDigestAlgorithm(),
                 bb.getDigestAlgorithm());
         PreferencesUtils.storeDiff(prefs, "dcmExternalRetrieveAET",
                 aa.getExternalRetrieveAET(),
                 bb.getExternalRetrieveAET());
         PreferencesUtils.storeDiff(prefs, "dcmRetrieveAET",
                 aa.getRetrieveAETs(),
                 bb.getRetrieveAETs());
         PreferencesUtils.storeDiff(prefs, "dcmMatchUnknown",
                 aa.isMatchUnknown(),
                 bb.isMatchUnknown(),
                 false);
         PreferencesUtils.storeDiff(prefs, "dcmSendPendingCGet",
                 aa.isSendPendingCGet(),
                 bb.isSendPendingCGet(),
                 false);
         PreferencesUtils.storeDiff(prefs, "dcmSendPendingCMoveInterval",
                 aa.getSendPendingCMoveInterval(),
                 bb.getSendPendingCMoveInterval(),
                 0);
         PreferencesUtils.storeDiff(prefs, "dcmSuppressWarningCoercionOfDataElements",
                 aa.isSuppressWarningCoercionOfDataElements(),
                 bb.isSuppressWarningCoercionOfDataElements(),
                 false);
         PreferencesUtils.storeDiff(prefs, "dcmStoreOriginalAttributes",
                 aa.isStoreOriginalAttributes(),
                 bb.isStoreOriginalAttributes(),
                 false);
         PreferencesUtils.storeDiff(prefs, "dcmPreserveSpoolFileOnFailure",
                 aa.isPreserveSpoolFileOnFailure(),
                 bb.isPreserveSpoolFileOnFailure(),
                 false);
         PreferencesUtils.storeDiff(prefs, "dcmModifyingSystem",
                 aa.getModifyingSystem(),
                 bb.getModifyingSystem());
         PreferencesUtils.storeDiff(prefs, "dcmStgCmtDelay",
                 aa.getStorageCommitmentDelay(),
                 bb.getStorageCommitmentDelay(),
                 0);
         PreferencesUtils.storeDiff(prefs, "dcmStgCmtMaxRetries",
                 aa.getStorageCommitmentMaxRetries(),
                 bb.getStorageCommitmentMaxRetries(),
                 0);
         PreferencesUtils.storeDiff(prefs, "dcmStgCmtRetryInterval",
                 aa.getStorageCommitmentRetryInterval(),
                 bb.getStorageCommitmentRetryInterval(),
                 ArchiveAEExtension.DEF_RETRY_INTERVAL);
         PreferencesUtils.storeDiff(prefs, "dcmFwdMppsDestination",
                 aa.getForwardMPPSDestinations(),
                 bb.getForwardMPPSDestinations());
         PreferencesUtils.storeDiff(prefs, "dcmFwdMppsMaxRetries",
                 aa.getForwardMPPSMaxRetries(),
                 bb.getForwardMPPSMaxRetries(),
                 0);
         PreferencesUtils.storeDiff(prefs, "dcmFwdMppsRetryInterval",
                 aa.getForwardMPPSRetryInterval(),
                 bb.getForwardMPPSRetryInterval(),
                 ArchiveAEExtension.DEF_RETRY_INTERVAL);
         PreferencesUtils.storeDiff(prefs, "dcmIanDestination",
                 aa.getIANDestinations(),
                 bb.getIANDestinations());
         PreferencesUtils.storeDiff(prefs, "dcmIanMaxRetries",
                 aa.getIANMaxRetries(),
                 bb.getIANMaxRetries(),
                 0);
         PreferencesUtils.storeDiff(prefs, "dcmIanRetryInterval",
                 aa.getIANRetryInterval(),
                 bb.getIANRetryInterval(),
                 ArchiveAEExtension.DEF_RETRY_INTERVAL);
         PreferencesUtils.storeDiff(prefs, "dcmReturnOtherPatientIDs",
                 aa.isReturnOtherPatientIDs(),
                 bb.isReturnOtherPatientIDs(),
                 false);
         PreferencesUtils.storeDiff(prefs, "dcmReturnOtherPatientNames",
                 aa.isReturnOtherPatientNames(),
                 bb.isReturnOtherPatientNames(),
                 false);
         PreferencesUtils.storeDiff(prefs, "dcmShowRejectedInstances",
                 aa.isShowRejectedInstances(),
                 bb.isShowRejectedInstances(),
                 false);
         PreferencesUtils.storeDiff(prefs, "hl7PIXConsumerApplication",
                 aa.getLocalPIXConsumerApplication(),
                 bb.getLocalPIXConsumerApplication());
         PreferencesUtils.storeDiff(prefs, "hl7PIXManagerApplication",
                 aa.getRemotePIXManagerApplication(),
                 bb.getRemotePIXManagerApplication());
         PreferencesUtils.storeDiff(prefs, "dcmQidoMaxNumberOfResults",
                 aa.getQIDOMaxNumberOfResults(),
                 bb.getQIDOMaxNumberOfResults(),
                 0);
    }

    @Override
    protected void mergeChilds(Device prev, Device device,
            Preferences deviceNode) throws BackingStoreException {
        ArchiveDeviceExtension aa =
                prev.getDeviceExtension(ArchiveDeviceExtension.class);
        ArchiveDeviceExtension bb =
                device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (aa == null || bb == null)
            return;

        Preferences afsNode = deviceNode.node("dcmAttributeFilter");
        for (Entity entity : Entity.values())
            storeDiffs(afsNode.node(entity.name()), aa.getAttributeFilter(entity),
                    bb.getAttributeFilter(entity));
    }

    @Override
    public void storeDiffs(HL7Application a, HL7Application b, Preferences prefs) {
         ArchiveHL7ApplicationExtension aa =
                 a.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
         ArchiveHL7ApplicationExtension bb =
                 b.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
         if (aa == null || bb == null)
             return;

         PreferencesUtils.storeDiff(prefs, "labeledURI",
                 aa.getTemplatesURIs(),
                 bb.getTemplatesURIs());
    }

    private void storeDiffs(Preferences prefs, AttributeFilter prev, AttributeFilter filter) {
        storeTags(prefs, "dcmTag", filter.getSelection());
        storeDiffTags(prefs, "dcmTag", 
                prev.getSelection(),
                filter.getSelection());
        PreferencesUtils.storeDiff(prefs, "dcmCustomAttribute1",
                prev.getCustomAttribute1(),
                filter.getCustomAttribute1());
        PreferencesUtils.storeDiff(prefs, "dcmCustomAttribute2",
                prev.getCustomAttribute2(),
                filter.getCustomAttribute2());
        PreferencesUtils.storeDiff(prefs, "dcmCustomAttribute3",
                prev.getCustomAttribute3(),
                filter.getCustomAttribute3());
    }

    private void storeDiffTags(Preferences prefs, String key, int[] prevs, int[] vals) {
        if (!Arrays.equals(prevs, vals)) {
            PreferencesUtils.removeKeys(prefs, key, vals.length, prevs.length);
            storeTags(prefs, key, vals);
        }
    }

    @Override
    protected void mergeChilds(ApplicationEntity prev, ApplicationEntity ae,
            Preferences aePrefs) throws BackingStoreException {
        ArchiveAEExtension aa = prev.getAEExtension(ArchiveAEExtension.class);
        ArchiveAEExtension bb = ae.getAEExtension(ArchiveAEExtension.class);
        if (aa == null || bb == null)
            return;

        config.merge(aa.getAttributeCoercions(), bb.getAttributeCoercions(), aePrefs);
//        PreferencesCompressionRulesConfiguration
//            .merge(aa.getCompressionRules(), bb.getCompressionRules(), aePrefs);

        mergeStoreDuplicates(aa.getStoreDuplicates(), bb.getStoreDuplicates(), aePrefs);
    }

    private void mergeStoreDuplicates(List<StoreDuplicate> prevs, List<StoreDuplicate> sds,
            Preferences aePrefs) throws BackingStoreException {
        Preferences sdsNode = aePrefs.node("dcmStoreDuplicate");
        int index = 1;
        Iterator<StoreDuplicate> prevIter = prevs.iterator();
        for (StoreDuplicate sd : sds) {
            Preferences sdNode = sdsNode.node("" + index++);
            if (prevIter.hasNext())
                storeDiffs(sdNode, prevIter.next(), sd);
            else
                storeTo(sd, sdNode);
        }
        while (prevIter.hasNext()) {
            prevIter.next();
            sdsNode.node("" + index++).removeNode();
        }
    }

    private void storeDiffs(Preferences prefs, StoreDuplicate a, StoreDuplicate b) {
        PreferencesUtils.storeDiff(prefs, "dcmStoreDuplicateCondition",
                a.getCondition(), b.getCondition());
        PreferencesUtils.storeDiff(prefs, "dcmStoreDuplicateAction",
                a.getAction(), b.getAction());
    }

}
