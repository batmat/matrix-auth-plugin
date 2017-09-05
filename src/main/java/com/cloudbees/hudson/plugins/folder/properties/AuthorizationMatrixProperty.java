/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc., Peter Hayes, Tom Huybrechts
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.hudson.plugins.folder.properties;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.Extension;
import hudson.model.Item;
import hudson.security.AuthorizationMatrixPropertyDescriptor;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.Permission;
import hudson.security.PermissionScope;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.security.SidACL;
import hudson.security.AbstractMatrixPropertyConverter;
import hudson.security.AuthorizationProperty;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.acegisecurity.acls.sid.Sid;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Holds ACL for {@link ProjectMatrixAuthorizationStrategy}.
 */
public class AuthorizationMatrixProperty extends AbstractFolderProperty<AbstractFolder<?>> implements AuthorizationProperty {

    private transient SidACL acl = new AclImpl();

    /**
     * List up all permissions that are granted.
     *
     * Strings are either the granted authority or the principal, which is not
     * distinguished.
     */
    private final Map<Permission, Set<String>> grantedPermissions = new HashMap<Permission, Set<String>>();

    private Set<String> sids = new HashSet<String>();

    private boolean blocksInheritance = false;

    protected AuthorizationMatrixProperty() {
    }

    public AuthorizationMatrixProperty(Map<Permission,? extends Set<String>> grantedPermissions) {
        // do a deep copy to be safe
        for (Entry<Permission,? extends Set<String>> e : grantedPermissions.entrySet())
            this.grantedPermissions.put(e.getKey(),new HashSet<String>(e.getValue()));
    }

    public Set<String> getGroups() {
        return sids;
    }

    /**
     * Returns all the (Permission,sid) pairs that are granted, in the multi-map form.
     *
     * @return
     *      read-only. never null.
     */
    public Map<Permission,Set<String>> getGrantedPermissions() {
        return Collections.unmodifiableMap(grantedPermissions);
    }

    /**
     * Adds to {@link #grantedPermissions}. Use of this method should be limited
     * during construction, as this object itself is considered immutable once
     * populated.
     */
    public void add(Permission p, String sid) {
        Set<String> set = grantedPermissions.get(p);
        if (set == null)
            grantedPermissions.put(p, set = new HashSet<String>());
        set.add(sid);
        sids.add(sid);
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor implements AuthorizationMatrixPropertyDescriptor<AuthorizationMatrixProperty> {

        @Override
        public AuthorizationMatrixProperty createProperty() {
            return new AuthorizationMatrixProperty();
        }

        @Override
        public PermissionScope getPermissionScope() {
            return PermissionScope.ITEM_GROUP;
        }

        @Override
        public AuthorizationMatrixProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return createNewInstance(req, formData, true);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractFolder> folder) {
            return isApplicable();
        }

        @Override
        public String getDisplayName() {
            return "Authorization Matrix";
        }

        public FormValidation doCheckName(@AncestorInPath AbstractFolder<?> folder, @QueryParameter String value) throws IOException, ServletException {
            return GlobalMatrixAuthorizationStrategy.DESCRIPTOR.doCheckName_(value, folder, Item.CONFIGURE);
        }
    }

    private final class AclImpl extends SidACL {
        @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "NP_BOOLEAN_RETURN_NULL",
                justification = "Because that is the way this SPI works")
        protected Boolean hasPermission(Sid sid, Permission p) {
            if (AuthorizationMatrixProperty.this.hasPermission(toString(sid),p))
                return true;
            return null;
        }
    }

    public SidACL getACL() {
        return acl;
    }

    /**
     * Sets the flag to block inheritance
     *
     * @param blocksInheritance
     */
    public void setBlocksInheritance(boolean blocksInheritance) {
        this.blocksInheritance = blocksInheritance;
    }

    /**
     * Returns true if the authorization matrix is configured to block
     * inheritance from the parent.
     *
     * @return
     */
    public boolean isBlocksInheritance() {
        return this.blocksInheritance;
    }

    /**
     * Persist {@link ProjectMatrixAuthorizationStrategy} as a list of IDs that
     * represent {@link ProjectMatrixAuthorizationStrategy#grantedPermissions}.
     */
    public static final class ConverterImpl extends AbstractMatrixPropertyConverter {
        public boolean canConvert(Class type) {
            return type == AuthorizationMatrixProperty.class;
        }

        @Override
        public AuthorizationProperty createSubject() {
            return new AuthorizationMatrixProperty();
        }
    }
}
