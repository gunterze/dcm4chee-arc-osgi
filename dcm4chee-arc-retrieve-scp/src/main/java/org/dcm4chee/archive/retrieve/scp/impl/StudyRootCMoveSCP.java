package org.dcm4chee.archive.retrieve.scp.impl;

import org.dcm4che.data.UID;

public class StudyRootCMoveSCP extends CMoveSCP {
    public StudyRootCMoveSCP() {
        super(UID.StudyRootQueryRetrieveInformationModelMOVE,
                "STUDY", "SERIES", "IMAGE");
    }
}