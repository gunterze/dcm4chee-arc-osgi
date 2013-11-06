package org.dcm4chee.archive.retrieve.scp.impl;

import org.dcm4che.data.UID;

public class PatientStudyOnlyCGetSCP extends CGetSCP {
    public PatientStudyOnlyCGetSCP() {
        super(UID.PatientStudyOnlyQueryRetrieveInformationModelMOVERetired,
                "PATIENT", "STUDY");
    }
}