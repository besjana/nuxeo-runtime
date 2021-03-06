/*
 * (C) Copyright 2006-2012 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     bstefanescu, jcarsique
 */
package org.nuxeo.connect.update.standalone;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.common.xmap.XMap;
import org.nuxeo.connect.update.LocalPackage;
import org.nuxeo.connect.update.NuxeoValidationState;
import org.nuxeo.connect.update.PackageData;
import org.nuxeo.connect.update.PackageDependency;
import org.nuxeo.connect.update.PackageException;
import org.nuxeo.connect.update.PackageState;
import org.nuxeo.connect.update.PackageType;
import org.nuxeo.connect.update.PackageUpdateService;
import org.nuxeo.connect.update.PackageVisibility;
import org.nuxeo.connect.update.ProductionState;
import org.nuxeo.connect.update.Validator;
import org.nuxeo.connect.update.Version;
import org.nuxeo.connect.update.model.Form;
import org.nuxeo.connect.update.model.TaskDefinition;
import org.nuxeo.connect.update.task.Task;
import org.nuxeo.connect.update.xml.FormsDefinition;
import org.nuxeo.connect.update.xml.PackageDefinitionImpl;
import org.nuxeo.connect.update.xml.TaskDefinitionImpl;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
public class LocalPackageImpl implements LocalPackage {

    protected String id;

    protected PackageState state;

    protected LocalPackageData data;

    protected PackageDefinitionImpl def;

    private PackageUpdateService service;

    public LocalPackageImpl(File file, int state, PackageUpdateService pus)
            throws PackageException {
        this(null, file, state, pus);
    }

    public LocalPackageImpl(ClassLoader parent, File file, int state,
            PackageUpdateService pus) throws PackageException {
        this.state = PackageState.getByValue(state);
        this.service = pus;
        XMap xmap = StandaloneUpdateService.getXmap();
        if (xmap == null) { // for tests
            xmap = StandaloneUpdateService.createXmap();
        }
        try {
            this.data = new LocalPackageData(parent, file);
            InputStream in = new FileInputStream(data.getManifest());
            def = (PackageDefinitionImpl) xmap.load(in);
        } catch (FileNotFoundException e) {
            throw new PackageException(
                    "Invalid package - no package.xml file found in package "
                            + file.getName());
        } catch (Exception e) {
            throw new PackageException(
                    "Failed to load package.xml descriptor for package "
                            + file.getName(), e);
        }
        id = def.getId();
    }

    /**
     * @since 5.7
     */
    public LocalPackageImpl(File file, PackageState state,
            PackageUpdateService pus) throws PackageException {
        this(file, state.getValue(), pus);
    }

    @Deprecated
    @Override
    public void setState(int state) {
        this.state = PackageState.getByValue(state);
    }

    @Override
    public void setState(PackageState state) {
        this.state = state;
    }

    @Override
    public PackageData getData() {
        return data;
    }

    @Override
    public File getInstallFile() throws PackageException {
        return data.getEntry(LocalPackage.INSTALL);
    }

    @Override
    public File getUninstallFile() throws PackageException {
        return data.getEntry(LocalPackage.UNINSTALL);
    }

    @Override
    public String getLicenseType() {
        return def.getLicenseType();
    }

    @Override
    public String getLicenseUrl() {
        return def.getLicenseUrl();
    }

    @Override
    public String getLicenseContent() throws PackageException {
        File file = data.getEntry(LocalPackage.LICENSE);
        if (file.isFile()) {
            try {
                return FileUtils.readFile(file);
            } catch (Exception e) {
                throw new PackageException(
                        "Failed to read license.txt file for package: "
                                + getId());
            }
        }
        return null;
    }

    @Override
    public String getClassifier() {
        return def.getClassifier();
    }

    @Override
    public String getDescription() {
        return def.getDescription();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return def.getName();
    }

    @Override
    public String getTitle() {
        return def.getTitle();
    }

    @Override
    public int getState() {
        return state.getValue();
    }

    @Override
    public String[] getTargetPlatforms() {
        return def.getTargetPlatforms();
    }

    @Override
    public PackageDependency[] getDependencies() {
        return def.getDependencies();
    }

    @Override
    public PackageDependency[] getConflicts() {
        return def.getConflicts();
    }

    @Override
    public PackageDependency[] getProvides() {
        return def.getProvides();
    }

    @Override
    public PackageType getType() {
        return def.getType();
    }

    @Override
    public String getHomePage() {
        return def.getHomePage();
    }

    @Override
    public Version getVersion() {
        return def.getVersion();
    }

    @Override
    public String getVendor() {
        return def.getVendor();
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public Task getInstallTask() throws PackageException {
        if (def.getInstaller() == null) {
            def.setInstaller(new TaskDefinitionImpl(
                    service.getDefaultInstallTaskType(), false));
        } else if (def.getInstaller().getType() == null) {
            def.getInstaller().setType(service.getDefaultInstallTaskType());
        }
        return getTask(def.getInstaller());
    }

    @Override
    public Task getUninstallTask() throws PackageException {
        if (def.getUninstaller() == null) {
            def.setUninstaller(new TaskDefinitionImpl(
                    service.getDefaultUninstallTaskType(), false));
        } else if (def.getUninstaller().getType() == null) {
            def.getUninstaller().setType(service.getDefaultUninstallTaskType());
        }
        return getTask(def.getUninstaller());
    }

    protected Task getTask(TaskDefinition tdef) throws PackageException {
        Task task = null;
        try {
            task = (Task) data.loadClass(tdef.getType()).getConstructor(
                    PackageUpdateService.class).newInstance(service);
        } catch (Exception e) {
            throw new PackageException("Could not instantiate custom task "
                    + tdef.getType() + " for package " + getId(), e);
        }
        task.initialize(this, tdef.getRequireRestart());
        return task;
    }

    @Override
    public Validator getValidator() throws PackageException {
        if (def.getValidator() != null) {
            try {
                return (Validator) data.loadClass(def.getValidator()).getConstructor().newInstance();
            } catch (Exception e) {
                throw new PackageException(
                        "Could not instantiate custom validator "
                                + def.getValidator() + " for package "
                                + getId(), e);
            }
        }
        return null;
    }

    public Form[] getForms(String path) throws PackageException {
        File file = data.getEntry(path);
        if (file.isFile()) {
            FileInputStream in = null;
            try {
                in = new FileInputStream(file);
                FormsDefinition forms = (FormsDefinition) StandaloneUpdateService.getXmap().load(
                        in);
                return forms.getForms();
            } catch (Exception e) {
                throw new PackageException("Failed to load forms file: " + file);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Form[] getValidationForms() throws PackageException {
        return getForms(LocalPackage.VALIDATION_FORMS);
    }

    @Override
    public Form[] getInstallForms() throws PackageException {
        return getForms(LocalPackage.INSTALL_FORMS);
    }

    @Override
    public Form[] getUninstallForms() throws PackageException {
        return getForms(LocalPackage.UNINSTALL_FORMS);
    }

    @Override
    public String getTermsAndConditionsContent() throws PackageException {
        File file = data.getEntry(LocalPackage.TERMSANDCONDITIONS);
        if (file.isFile()) {
            try {
                return FileUtils.readFile(file);
            } catch (Exception e) {
                throw new PackageException(
                        "Failed to read license.txt file for package: "
                                + getId());
            }
        }
        return null;
    }

    @Override
    public boolean requireTermsAndConditionsAcceptance() {
        return def.requireTermsAndConditionsAcceptance();
    }

    @Override
    public ProductionState getProductionState() {
        return def.getProductionState();
    }

    @Override
    public NuxeoValidationState getValidationState() {
        return def.getValidationState();
    }

    @Override
    public boolean isSupported() {
        return def.isSupported();
    }

    @Override
    public boolean supportsHotReload() {
        return def.supportsHotReload();
    }

    @Override
    public PackageVisibility getVisibility() {
        return def.getVisibility();
    }

}
