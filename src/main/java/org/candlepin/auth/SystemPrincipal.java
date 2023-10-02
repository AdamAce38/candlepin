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
package org.candlepin.auth;

/**
 * SystemPrincipal
 */
public class SystemPrincipal extends Principal {
    /**
     * Set the serialVersionUID to the value of the previous version auto-generated by Java, as the
     * last known change won't affect functionality here. We just have to hope the auto-generated
     * previous version is always generated the same way.
     */
    private static final long serialVersionUID = -2122749997316786617L;

    public static final String NAME = "System";

    @Override
    public String getType() {
        return "system";
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean hasFullAccess() {
        return true;
    }

    @Override
    public AuthenticationMethod getAuthenticationMethod() {
        return AuthenticationMethod.SYSTEM;
    }

}
