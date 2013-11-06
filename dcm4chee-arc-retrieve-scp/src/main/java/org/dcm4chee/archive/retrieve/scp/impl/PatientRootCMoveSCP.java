package org.dcm4chee.archive.retrieve.scp.impl;

import org.dcm4che.data.UID;

public class PatientRootCMoveSCP extends CMoveSCP {
    public PatientRootCMoveSCP() {
        super(UID.PatientRootQueryRetrieveInformationModelMOVE,
                "PATIENT", "STUDY", "SERIES", "IMAGE");
    }
}