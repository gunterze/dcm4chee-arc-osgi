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
 * Portions created by the Initial Developer are Copyright (C) 2012
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

package org.dcm4chee.archive.test.patient;


import javax.persistence.EntityManager;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.soundex.ESoundex;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.patient.PatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Umberto Cappellini <umberto.cappellini@agfa.com>
 * 
 */
public class PatientServiceTest {

    final static Logger LOG = LoggerFactory.getLogger(PatientServiceTest.class);

    private static final String PID_1234 = "PATIENT_SERVICE_TEST-1234";
    private static final String PID_5678 = "PATIENT_SERVICE_TEST-5678";
    private static final String ISSUER_X = "DCM4CHEE_TESTDATA_X";
    private static final String ISSUER_Y = "DCM4CHEE_TESTDATA_Y";
    private static final String TEST_1234 = "Test PatientService 1234";
    private static final String TEST_1234_X = "Test PatientService 1234-X";
    private static final String TEST_1234_Y = "Test PatientService 1234-Y";
    private static final String TEST_5678_X = "Test PatientService 5678-X";

    private PatientService patientService;
    private long millis;

    // injected by blueprint
    public void setPatientService(PatientService patientService) {
        this.patientService = patientService;
    }

    public void init() throws Exception {
        millis = System.currentTimeMillis();
        
        LOG.info("PatientServiceTest.clearData");
        patientService.clearData();

        LOG.info("PatientServiceTest.testFindUniqueOrCreatePatient");
        testFindUniqueOrCreatePatient();
        
        LOG.info("PatientServiceTest.end in "
                + (System.currentTimeMillis() - millis) + " ms");
    }

    public void destroy() throws Exception {

    }

    public void testFindUniqueOrCreatePatient() throws Exception {
        Patient pat1234 = patientService.findUniqueOrCreatePatient(
                new AttributeFilter(ParamFactory.PATIENT_ATTRS),
                new ESoundex(), attrs(TEST_1234, PID_1234, null), true, true);
        Patient pat1234X = patientService.findUniqueOrCreatePatient(
                new AttributeFilter(ParamFactory.PATIENT_ATTRS),
                new ESoundex(), attrs(TEST_1234_X, PID_1234, ISSUER_X), true,
                true);
        Patient pat1234Y = patientService.findUniqueOrCreatePatient(
                new AttributeFilter(ParamFactory.PATIENT_ATTRS),
                new ESoundex(), attrs(TEST_1234_Y, PID_1234, ISSUER_Y), true,
                true);
        assertEquals(pat1234.getPk(), pat1234X.getPk());
        assertEquals(TEST_1234,
                pat1234X.getAttributes().getString(Tag.PatientName));
        assertNotEquals(pat1234X.getPk(),pat1234Y.getPk());
    }

    private Attributes attrs(String name, String pid, String issuer) {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.PatientName, VR.PN, name);
        attrs.setString(Tag.PatientID, VR.LO, pid);
        if (issuer != null)
            attrs.setString(Tag.IssuerOfPatientID, VR.LO, issuer);
        return attrs;
    }

    private void assertEquals(Object expected,
            Object actual) {
        if (expected == null && actual == null)
            return;
        if (expected != null && expected.equals(actual))
            return;
        else
            throw new Error("Not Equal:" + expected+","+ actual);
    }
    
    private void assertNotEquals(Object expected,
            Object actual) {
        if (expected == null && actual != null)
            return;
        if (expected != null && actual == null)
            return;
        if (expected == null && actual == null) 
            throw new Error("Equal:" + expected+","+ actual);
        if (!expected.equals(actual))
            return;
        else
            throw new Error("Equal:" + expected+","+ actual);
    }
}
