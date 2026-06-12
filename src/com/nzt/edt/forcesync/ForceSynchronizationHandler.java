package com.nzt.edt.forcesync;

import java.lang.reflect.Method;
import java.util.Collection;

import com._1c.g5.v8.dt.core.platform.IDependentProject;
import com._1c.g5.v8.dt.core.platform.IExtensionProject;
import com._1c.g5.v8.dt.core.platform.IV8Project;
import com._1c.g5.v8.dt.core.platform.IV8ProjectManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.sync.v2.IInfobaseSynchronizationStateManager;
import com._1c.g5.v8.dt.platform.services.model.InfobaseReference;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.navigator.CommonNavigator;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

public class ForceSynchronizationHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IStructuredSelection selection = HandlerUtil.getCurrentStructuredSelection(event);
        Object element = selection.getFirstElement();

        if (!(element instanceof IInfobaseApplication app)) {
            return null;
        }

        InfobaseReference infobaseRef = app.getInfobase();
        IProject project = app.getProject();

        try {
            Object delegate = getStateManagerDelegate();
            if (delegate == null) {
                throw new ExecutionException("State manager not available");
            }

            Method forceSync = delegate.getClass().getMethod(
                "forceEdtSynchronization", InfobaseReference.class, IProject.class);

            forceSync.invoke(delegate, infobaseRef, project);

            IV8ProjectManager v8pm = lookupService(IV8ProjectManager.class);
            if (v8pm != null) {
                Collection<? extends IV8Project> extProjects =
                    v8pm.getProjects(IExtensionProject.class);
                for (IProject extProject :
                        IDependentProject.getDependent(project, extProjects)) {
                    forceSync.invoke(delegate, infobaseRef, extProject);
                }
            }
            refreshApplicationsView();
        } catch (ExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutionException("Failed to force synchronization", e);
        }

        return null;
    }

    private static void refreshApplicationsView() {
        Display.getDefault().asyncExec(() -> {
            IWorkbenchPage page = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow().getActivePage();
            if (page == null) return;
            IViewPart part = page.findView("com.e1c.g5.dt.applications.ui.view");
            if (part instanceof CommonNavigator nav) {
                nav.getCommonViewer().refresh(true);
            }
        });
    }

    private static Object getStateManagerDelegate() throws Exception {
        IInfobaseSynchronizationStateManager stateManager =
            lookupService(IInfobaseSynchronizationStateManager.class);
        if (stateManager == null) {
            return null;
        }

        Method getDelegate = stateManager.getClass().getMethod("getDelegate");
        return getDelegate.invoke(stateManager);
    }

    @SuppressWarnings("unchecked")
    private static <T> T lookupService(Class<T> type) {
        Bundle b = FrameworkUtil.getBundle(type);
        if (b == null)
            return null;
        BundleContext ctx = b.getBundleContext();
        if (ctx == null)
            return null;
        ServiceReference<T> ref = ctx.getServiceReference(type);
        if (ref == null)
            return null;
        return ctx.getService(ref);
    }
}
