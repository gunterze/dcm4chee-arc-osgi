package org.dcm4chee.archive;

import org.dcm4che.net.Device;
import org.dcm4che.net.hl7.service.HL7ServiceRegistry;
import org.dcm4che.net.service.DicomServiceRegistry;

public interface ArchiveService {

    boolean isRunning();

    void start() throws Exception;

    void stop();

    void reload() throws Exception;

    Device getDevice();

    DicomServiceRegistry getServiceRegistry();

    HL7ServiceRegistry getHL7ServiceRegistry();

}
