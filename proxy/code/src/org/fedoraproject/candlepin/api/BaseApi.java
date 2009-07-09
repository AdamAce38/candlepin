/**
 * Copyright (c) 2008 Red Hat, Inc.
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
package org.fedoraproject.candlepin.api;

import org.apache.log4j.Logger;

import com.sun.jersey.api.representation.Form;

import org.fedoraproject.candlepin.model.BaseModel;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.util.MethodUtil;

import java.util.Iterator;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;


public abstract class BaseApi {

    /**
     * Logger for this class
     */
    private static final Logger log = Logger.getLogger(BaseApi.class);

    @GET @Path("/{uuid}")
    @Produces("text/plain")
    public Object get(@PathParam("uuid") String uuid) {
        Object o = ObjectFactory.get().
            lookupByUUID(getApiClass(), uuid);
        return o;
    }
    
    @GET @Path("/list")
    @Produces("text/plain")
    public String list() {
        StringBuffer retval = new StringBuffer();
        List objects =  ObjectFactory.get().listObjectsByClass(getApiClass());
        for (int i = 0; i < objects.size(); i++) {
            retval.append(objects.get(i).toString());
            retval.append("\n");
        }
        
        return retval.toString();
    }
    
    @POST
    @Produces("text/plain")
    public void post(Form form) {
        String name = form.getFirst("name");
        if (name == null) {
            throw new RuntimeException("Missing name parameter.");
        }
        String newuuid = BaseModel.generateUUID();
        Object args[] = new Object[1];
        args[0] = newuuid;
        BaseModel newobject = (BaseModel) 
            MethodUtil.callNewMethod(getApiClass().getName(), args);
        // newobject.setName(name);
        // newobject.setUuid(newuuid);
        Iterator i = form.keySet().iterator();
        while (i.hasNext()) {
            String key = (String) i.next();
            String value = form.getFirst(key); 
            log.debug("value : " + value);
            MethodUtil.callSetter(newobject, key, value);
        }
        if (log.isDebugEnabled()) {
            log.debug("before store name: " + newobject.getName());
            log.debug("before store uuid: " + newobject.getUuid());
        }
        ObjectFactory.get().store(newobject);
    }

    protected abstract Class getApiClass();

}
