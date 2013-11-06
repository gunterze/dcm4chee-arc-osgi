package org.dcm4chee.archive.retrieve.scp.impl;

import org.dcm4che.data.UID;

public class WithoutBulkDataCGetSCP extends CGetSCP {
    public WithoutBulkDataCGetSCP() {
        super(UID.CompositeInstanceRetrieveWithoutBulkDataGET,
                "IMAGE");
    }
}