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

package org.dcm4chee.archive.rest;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.xml.ws.Endpoint;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Umberto Cappellini <umberto.cappellini@agfa.com>
 * 
 */
public class HttpServiceTracker extends ServiceTracker {
    private static final Logger LOG = LoggerFactory
            .getLogger(HttpServiceTracker.class);
    
    private static String REST_PATH = "/cxf";

    private Endpoint endpoint;
    private Servlet cxfServlet;

    public HttpServiceTracker(BundleContext context) {
        super(context, HttpService.class.getName(), null);
    }

    public void setCxfServlet(Servlet cxfServlet) {
        this.cxfServlet = cxfServlet;
    }

    public void init() throws Exception {
        this.open();
    }

    public void destroy() throws Exception {
        this.close();
    }

    public HttpService addingService(ServiceReference reference) {

        HttpService httpService = (HttpService) super.addingService(reference);

        if (httpService != null) {
            try {
                httpService.registerServlet(REST_PATH, cxfServlet, null,
                        null);
            } catch (NamespaceException e) {
                // registration fails because the alias is already in use.
                LOG.error("CXF Servlet non registered", e);

            } catch (ServletException e) {
                // if the servlet's init method throws an exception,
                // or the given servlet object has already been registered at a
                // different alias
                if (e.getMessage()!=null && e.getMessage().contains("servlet already registered"))
                    LOG.warn("CXF Servlet already registered. Path {} not used.",REST_PATH);
                else
                    LOG.error("CXF Servlet non registered", e);
            }
        }
        return httpService;
    }

    public void removedService(ServiceReference reference,
            HttpService httpService) {
        this.endpoint.stop();
        
        try {
            if (httpService != null) {
                httpService.unregister(REST_PATH);
            }
        } catch (Exception e) {
            LOG.warn("Error uregistering CXF Servlet at path {}.", REST_PATH);
        }
    }

}
