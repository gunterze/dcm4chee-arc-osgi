package org.dcm4chee.archive.retrieve.scp.impl;

import org.dcm4che.data.UID;

public class PatientStudyOnlyCMoveSCP extends CMoveSCP {
    public PatientStudyOnlyCMoveSCP() {
        super(UID.PatientStudyOnlyQueryRetrieveInformationModelMOVERetired,
                "PATIENT", "STUDY");
    }
}