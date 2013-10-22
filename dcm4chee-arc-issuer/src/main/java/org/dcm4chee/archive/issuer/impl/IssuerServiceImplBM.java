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
 * Portions created by the Initial Developer are Copyright (C) 2011-2013
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

package org.dcm4chee.archive.issuer.impl;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import javax.transaction.UserTransaction;

import org.dcm4chee.archive.entity.Issuer;
import org.dcm4chee.archive.issuer.IssuerService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class IssuerServiceImplBM implements IssuerService {

    private EntityManagerFactory emf;
    private UserTransaction utx;

    public void setEntityManagerFactory(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public void setUserTransaction(UserTransaction utx) {
        this.utx = utx;
    }

    @Override
    public Issuer findOrCreate(Issuer issuer) {
        EntityManager em = emf.createEntityManager();
        boolean commit = false;
        try {
            if (utx.getStatus() == javax.transaction.Status.STATUS_NO_TRANSACTION) {
                utx.begin();
                commit = true;
            }
            em.joinTransaction();
            Issuer result = findOrCreate(em, issuer);
            if (commit)
                utx.commit();
            return result;
        } catch (Exception e) {
            if (commit)
                try { utx.rollback(); } catch (Exception ignore) {}
            throw new RuntimeException(e);
        } finally {
            em.close();
        }
    }

    private Issuer findOrCreate(EntityManager em, Issuer issuer) {
        try {
            String entityID = issuer.getLocalNamespaceEntityID();
            String entityUID = issuer.getUniversalEntityID();
            String entityUIDType = issuer.getUniversalEntityIDType();
            TypedQuery<Issuer> query;
            if (entityID == null) {
                query = em.createNamedQuery(Issuer.FIND_BY_ENTITY_UID, Issuer.class)
                    .setParameter(1, entityUID)
                    .setParameter(2, entityUIDType);
            } else if (entityUID == null) {
                query = em.createNamedQuery(Issuer.FIND_BY_ENTITY_ID, Issuer.class)
                    .setParameter(1, entityID);
            } else {
                query = em.createNamedQuery(Issuer.FIND_BY_ENTITY_ID_OR_UID, Issuer.class)
                    .setParameter(1, entityID)
                    .setParameter(2, entityUID)
                    .setParameter(3, entityUIDType);
            }
            return query.getSingleResult();
        } catch (NoResultException e) {
            em.persist(issuer);
            em.flush();
            em.refresh(issuer);
            return issuer;
        }
    }
}