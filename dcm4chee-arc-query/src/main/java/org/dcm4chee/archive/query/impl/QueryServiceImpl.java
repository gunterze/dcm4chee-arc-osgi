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
 * Portions created by the Initial Developer are Copyright (C) 2011
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

package org.dcm4chee.archive.query.impl;

import java.sql.Connection;
import java.sql.SQLException;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.IDWithIssuer;
import org.dcm4che.net.service.QueryRetrieveLevel;
import org.dcm4chee.archive.query.Query;
import org.dcm4chee.archive.query.QueryParam;
import org.dcm4chee.archive.query.QueryService;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class QueryServiceImpl implements QueryService {

    private DataSource dataSource;

    private SessionFactory sessionFactory;

    private SeriesService seriesService;
    
    public BundleContext bcontext;
    
    public void setBcontext(BundleContext bcontext) {
            this.bcontext = bcontext;
    }
    
    private EntityManager em;

    public EntityManager getEntityManager() {
        return em;
    }

    public void setEntityManager(EntityManager em) {
        this.em = em;
    }    

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public SeriesService getSeriesService() {
        return seriesService;
    }

    public void setSeriesService(SeriesService seriesService) {
        this.seriesService = seriesService;
    }

    Connection getConnection() throws SQLException {
        if (dataSource == null)
            throw new IllegalStateException("dataSource not initialized");

        return dataSource.getConnection();
    }

    StatelessSession openStatelessSession(Connection connection) 
    {
//        ServiceReference<SessionFactory> factoryRef = bcontext.getServiceReference(SessionFactory.class);
//        SessionFactory factory = bcontext.getService(factoryRef);
//        
//        return factory.openStatelessSession(connection);
        
        Session s = em.unwrap(Session.class);
        return s.getSessionFactory().openStatelessSession(connection);

    }

    @Override
    public Query createQuery(QueryRetrieveLevel qrlevel,
            IDWithIssuer[] pids, Attributes keys, QueryParam queryParam)
                    throws Exception {
        switch (qrlevel) {
        case PATIENT:
            return createPatientQuery(pids, keys, queryParam);
        case STUDY:
            return createStudyQuery(pids, keys, queryParam);
        case SERIES:
            return createSeriesQuery(pids, keys, queryParam);
        case IMAGE:
            return createInstanceQuery(pids, keys, queryParam);
        default:
            throw new IllegalArgumentException("qrlevel: " + qrlevel);
        }
    }

    @Override
    public Query createPatientQuery(IDWithIssuer[] pids, Attributes keys,
            QueryParam queryParam) throws Exception {
        return new PatientQuery(this).init(pids, keys, queryParam);
    }

    @Override
    public Query createStudyQuery(IDWithIssuer[] pids, Attributes keys,
            QueryParam queryParam) throws Exception {
        return new StudyQuery(this).init(pids, keys, queryParam);
    }

    @Override
    public Query createSeriesQuery(IDWithIssuer[] pids, Attributes keys,
            QueryParam queryParam) throws Exception {
        return new SeriesQuery(this).init(pids, keys, queryParam);
    }

    @Override
    public Query createInstanceQuery(IDWithIssuer[] pids, Attributes keys,
            QueryParam queryParam) throws Exception {
        return new InstanceQuery(this).init(pids, keys, queryParam);
    }

}
