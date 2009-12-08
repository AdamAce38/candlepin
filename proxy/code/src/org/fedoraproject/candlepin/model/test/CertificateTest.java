/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */



package org.fedoraproject.candlepin.model.test;

import java.util.List;

import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Certificate;

import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;


public class CertificateTest extends DatabaseTestFixture {

    @Before
    public void setUpTestObjects() {
        
        String ownerName = "Example Corporation";
        Owner owner = new Owner(ownerName);
        
        String cert = "This is not actually a certificate. No entitlements for you!";
        
        Certificate certificate = new Certificate(cert, owner);
        beginTransaction();
        em.persist(owner);
        em.persist(certificate);
        commitTransaction();
    }
    
    
    @Test
    public void testGetCertificate() {
        Certificate newCertificate = new Certificate();
        
        // doesn't actually do anything yet        
    }
    
    @Test
    public void testList() throws Exception {
        beginTransaction();
        
        List<Certificate> certificates = em.createQuery("select c from Certificate as c").getResultList();
        int beforeCount = certificates.size();
        
     
        String certname = "this is a test";
        for (int i = 0; i < 10; i++) {
            Owner owner = new Owner("owner" + i);
            em.persist(owner);
//            em.persist(new Certificate());
            em.persist(new Certificate(certname, owner));
        }
        commitTransaction();
        
        certificates =  em.createQuery("select c from Certificate as c").getResultList();
        int afterCount = certificates.size();
        assertEquals(10, afterCount - beforeCount);
        
    }
    
    @Test
    public void testLookup() throws Exception {
        
        Owner owner = new Owner("test company");
        Certificate certificate = new Certificate("not a cert", owner);
        
        beginTransaction();
        em.persist(owner);
        em.persist(certificate);
        commitTransaction();
        Certificate lookedUp = (Certificate)em.find(Certificate.class, certificate.getId());
        assertEquals(certificate.getId(), lookedUp.getId());
        assertEquals(certificate.getCertificate(), lookedUp.getCertificate());

    }
}

