package org.dcm4chee.archive.retrieve.scp.impl;

import org.dcm4che.data.UID;

public class PatientRootCGetSCP extends CGetSCP {
    public PatientRootCGetSCP() {
        super(UID.PatientRootQueryRetrieveInformationModelGET,
                "PATIENT", "STUDY", "SERIES", "IMAGE");
    }
}