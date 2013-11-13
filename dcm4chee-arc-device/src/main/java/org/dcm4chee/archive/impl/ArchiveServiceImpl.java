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

package org.dcm4chee.archive.impl;

import javax.jms.Connection;
import javax.jms.JMSException;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.DicomConfiguration;
import org.dcm4che.imageio.codec.ImageReaderFactory;
import org.dcm4che.imageio.codec.ImageWriterFactory;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Device;
import org.dcm4che.net.DeviceService;
import org.dcm4che.net.hl7.HL7DeviceExtension;
import org.dcm4che.net.hl7.service.HL7ServiceRegistry;
import org.dcm4che.net.imageio.ImageReaderExtension;
import org.dcm4che.net.imageio.ImageWriterExtension;
import org.dcm4che.net.service.BasicCEchoSCP;
import org.dcm4che.net.service.DicomServiceRegistry;
import org.dcm4chee.archive.ArchiveService;
import org.dcm4chee.archive.conf.ArchiveDeviceExtension;
import org.dcm4chee.archive.jms.JmsService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class ArchiveServiceImpl extends DeviceService implements ArchiveService {

    private static String[] JBOSS_PROPERITIES = {
        "jboss.home",
        "jboss.modules",
        "jboss.server.base",
        "jboss.server.config",
        "jboss.server.data",
        "jboss.server.deploy",
        "jboss.server.log",
        "jboss.server.temp",
    };

    private String deviceName;
    private DicomConfiguration dicomConfig;
    private final DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    private final HL7ServiceRegistry hl7ServiceRegistry = new HL7ServiceRegistry();

    private Connection jmsConnection;
    private ApplicationEntityCache aeCache;



    public ArchiveServiceImpl() {
        serviceRegistry.addDicomService(new BasicCEchoSCP());
    }

    @Override
    public DicomServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }

    @Override
    public HL7ServiceRegistry getHL7ServiceRegistry() {
        return hl7ServiceRegistry;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public void setDicomConfiguration(DicomConfiguration dicomConfig) {
        this.dicomConfig = dicomConfig;
        this.aeCache = new ApplicationEntityCache(dicomConfig);
    }

    public void setJmsService(JmsService jmsService) {
        this.jmsConnection = jmsService.getConnection();
    }

    public void init() throws Exception {
        //addJBossDirURLSystemProperties();
        Device device = dicomConfig.findDevice(deviceName);
        init(device);
        device.setDimseRQHandler(serviceRegistry);
        device.getDeviceExtension(HL7DeviceExtension.class)
            .setHL7MessageListener(hl7ServiceRegistry);
        setConfigurationStaleTimeout();
        initImageReaderFactory();
        initImageWriterFactory();
        start();
    }

    private void initImageReaderFactory() {
        ImageReaderExtension ext = device.getDeviceExtension(ImageReaderExtension.class);
        if (ext != null)
            ImageReaderFactory.setDefault(ext.getImageReaderFactory());
        else
            ImageReaderFactory.resetDefault();
    }

    private void initImageWriterFactory() {
        ImageWriterExtension ext = device.getDeviceExtension(ImageWriterExtension.class);
        if (ext != null)
            ImageWriterFactory.setDefault(ext.getImageWriterFactory());
        else
            ImageWriterFactory.resetDefault();
    }

    public void destroy() {
        if (isRunning())
            stop();
    }

    @Override
    public ApplicationEntity findApplicationEntity(String aet)
            throws ConfigurationException {
        return aeCache.findApplicationEntity(aet);
    }

    @Override
    public void reload() throws Exception {
        device.reconfigure(dicomConfig.findDevice(device.getDeviceName()));
        setConfigurationStaleTimeout();
        initImageReaderFactory();
        initImageWriterFactory();
        device.rebindConnections();
    }

    private void setConfigurationStaleTimeout() {
        ArchiveDeviceExtension ext = device.getDeviceExtension(ArchiveDeviceExtension.class);
        int staleTimeout = ext.getConfigurationStaleTimeout();
        aeCache.setStaleTimeout(staleTimeout);
//        hl7AppCache.setStaleTimeout(staleTimeout);
//        WadoAttributesCache.INSTANCE.setStaleTimeout(ext.getWadoAttributesStaleTimeout());
    }

    @Override
    public void start() throws Exception {
        jmsConnection.start();
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        try {
            jmsConnection.stop();
        } catch (JMSException e) {
            // may already be closed
        }
    }
}
