package org.dcm4chee.archive.retrieve.scp.impl;

import org.dcm4che.data.UID;

public class StudyRootCGetSCP extends CGetSCP {
    public StudyRootCGetSCP() {
        super(UID.StudyRootQueryRetrieveInformationModelGET,
                "STUDY", "SERIES", "IMAGE");
    }
}