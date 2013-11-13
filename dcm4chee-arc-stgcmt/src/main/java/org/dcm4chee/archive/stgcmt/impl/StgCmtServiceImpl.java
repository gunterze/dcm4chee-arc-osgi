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

package org.dcm4chee.archive.stgcmt.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.persistence.EntityManager;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.net.Status;
import org.dcm4chee.archive.entity.Utils;
import org.dcm4chee.archive.entity.query.types.QInstance;
import org.dcm4chee.archive.query.common.QueryBuilder;
import org.dcm4chee.archive.stgcmt.StgCmtService;
import org.hibernate.Session;

import com.mysema.query.BooleanBuilder;
import com.mysema.query.Tuple;
import com.mysema.query.jpa.hibernate.HibernateQuery;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class StgCmtServiceImpl implements StgCmtService {

    private Session session;

    public void setEntityManager(EntityManager em) {
        this.session = em.unwrap(Session.class);
    }

    @Override
    public Attributes calculateResult(Attributes actionInfo) {
        Sequence requestSeq = actionInfo.getSequence(Tag.ReferencedSOPSequence);
        int size = requestSeq.size();
        String[] sopIUIDs = new String[size];
        for (int i = 0; i < size; i++)
            sopIUIDs[i] = requestSeq.get(i).getString(Tag.ReferencedSOPInstanceUID);
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(QueryBuilder.uids(QInstance.instance.sopInstanceUID, sopIUIDs, false));
        builder.and(QInstance.instance.replaced.isFalse());
        List<Tuple> list = new HibernateQuery(session)
            .from(QInstance.instance)
            .where(builder)
            .list(
                QInstance.instance.sopClassUID,
                QInstance.instance.sopInstanceUID,
                QInstance.instance.retrieveAETs,
                QInstance.instance.externalRetrieveAET);
        String[] commonRetrieveAETs = null;
        HashMap<String,Tuple> map = new HashMap<String,Tuple>(list.size() * 4 / 3);
        for (Tuple tuple : list) {
            String[] retrieveAETs = { 
                    tuple.get(2, String.class),
                    tuple.get(3, String.class)
            };
            if (map.isEmpty())
                commonRetrieveAETs = retrieveAETs;
            else if (commonRetrieveAETs != null)
                if (!Arrays.equals(commonRetrieveAETs, retrieveAETs))
                    commonRetrieveAETs = null;
            map.put(tuple.get(1, String.class), tuple);
        }
        Attributes eventInfo = new Attributes(4);
        if (commonRetrieveAETs != null)
            Utils.setRetrieveAET(eventInfo, commonRetrieveAETs[0], commonRetrieveAETs[1]);
        eventInfo.setString(Tag.TransactionUID, VR.UI, actionInfo.getString(Tag.TransactionUID));
        Sequence successSeq = eventInfo.newSequence(Tag.ReferencedSOPSequence, size);
        Sequence failedSeq = eventInfo.newSequence(Tag.FailedSOPSequence, size);
        for (Attributes refSOP : requestSeq) {
            Tuple tuple = map.get(refSOP.getString(Tag.ReferencedSOPInstanceUID));
            if (tuple == null)
                failedSeq.add(refSOP(refSOP, Status.NoSuchObjectInstance));
            else if (!refSOP.getString(Tag.ReferencedSOPClassUID)
                    .equals(tuple.get(0, String.class)))
                failedSeq.add(refSOP(refSOP, Status.ClassInstanceConflict));
            else
                successSeq.add(refSOP(refSOP, commonRetrieveAETs,
                        tuple.get(2, String.class),
                        tuple.get(3, String.class)));
        }
        if (failedSeq.isEmpty())
            eventInfo.remove(Tag.FailedSOPSequence);
        return eventInfo;
    }

    private static Attributes refSOP(Attributes refSOP,
            String[] commonRetrieveAETs, String retrieveAETs, String externalRetrieveAET) {
        Attributes attrs = new Attributes(3);
        if (commonRetrieveAETs == null)
            Utils.setRetrieveAET(attrs, retrieveAETs, externalRetrieveAET);
        attrs.setString(Tag.ReferencedSOPClassUID, VR.UI,
                refSOP.getString(Tag.ReferencedSOPClassUID));
        attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
                refSOP.getString(Tag.ReferencedSOPInstanceUID));
        return attrs ;
    }

    private static Attributes refSOP(Attributes refSOP, int failureReason) {
        Attributes attrs = new Attributes(3);
        attrs.setString(Tag.ReferencedSOPClassUID, VR.UI,
                refSOP.getString(Tag.ReferencedSOPClassUID));
        attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
                refSOP.getString(Tag.ReferencedSOPInstanceUID));
        attrs.setInt(Tag.FailureReason, VR.US, failureReason);
        return attrs ;
    }
}
