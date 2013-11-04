package org.dcm4chee.archive.request;

import org.dcm4chee.archive.entity.Patient;

public class PatientMismatchException extends RuntimeException {

    private static final long serialVersionUID = 6053634947778779905L;

    public PatientMismatchException(Object source, Patient expected, Patient actual) {
        super("Expected " + expected + " associated by " + source
                + ", but was actually " + actual);
    }
}
