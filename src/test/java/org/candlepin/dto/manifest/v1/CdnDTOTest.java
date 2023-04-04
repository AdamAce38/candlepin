/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.dto.manifest.v1;

import org.candlepin.dto.AbstractDTOTest;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Test suite for the CdnDTO (manifest import/export) class
 */
public class CdnDTOTest extends AbstractDTOTest<CdnDTO> {

    protected Map<String, Object> values;

    public CdnDTOTest() {
        super(CdnDTO.class);

        CertificateDTO cdnCertificate = new CertificateDTO();
        cdnCertificate.setId("test_id");

        this.values = new HashMap<>();
        this.values.put("Id", "test_id");
        this.values.put("Label", "test_label");
        this.values.put("Name", "test_value");
        this.values.put("Url", "test_url");
        this.values.put("Certificate", cdnCertificate);
        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getInputValueForMutator(String field) {
        return this.values.get(field);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getOutputValueForAccessor(String field, Object input) {
        // Nothing to do here
        return input;
    }
}
