/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.openshift.utils.odo;

import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.intellij.common.utils.MessagesHelper;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.jboss.tools.intellij.openshift.tree.application.ApplicationRootNodeOdo;
import org.jboss.tools.intellij.openshift.tree.application.ApplicationsRootNode;
import org.jboss.tools.intellij.openshift.utils.KubernetesClientFactory;
import org.jboss.tools.intellij.openshift.utils.OdoCluster;
import org.jboss.tools.intellij.openshift.utils.ToolFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.awaitility.Awaitility.with;
import static org.jboss.tools.intellij.openshift.Constants.PLUGIN_FOLDER;
import static org.mockito.Mockito.mock;

@RunWith(JUnit4.class)
public abstract class OdoCliTest extends BasePlatformTestCase {

  // disabled since there is no more service operator available, and service binding operator is deprecated. see https://github.com/redhat-developer/service-binding-operator#deprecation-notice
  // see https://operatorhub.io/operator/cloud-native-postgresql/ STABLE channel for versions
  public static final String SERVICE_TEMPLATE = "cloud-native-postgresql";
  public static final String SERVICE_KUBE_VERSION = "v1.16.2";
  public static final String SERVICE_OPENSHIFT_VERSION = "v1.22.1";

  public static final String SERVICE_CRD = "clusters.postgresql.k8s.enterprisedb.io";
  public static final String REGISTRY_URL = "https://registry.stage.devfile.io";
  public static final String REGISTRY_NAME = "RegistryForITTests";

  protected OdoFacade odo;

  protected ApplicationsRootNode rootNode = mock(ApplicationsRootNode.class);

  private final OdoProcessHelper processHelper = new OdoProcessHelper();

  protected Random random = new Random();

  protected static final String PROJECT_PREFIX = "prj";

  protected static final String COMPONENT_PREFIX = "comp";

  protected static final String SERVICE_PREFIX = "srv";

  protected static final String REGISTRY_PREFIX = "reg";

  private TestDialog previousTestDialog;
  private KubernetesClient client;

  @Before
  public void init() throws Exception {
    this.previousTestDialog = MessagesHelper.setTestDialog(TestDialog.OK);
    this.client = new KubernetesClientFactory().create();
    loginCluster();
    this.odo = createOdo(client).get();
  }

  @After
  public void cleanup() {
    MessagesHelper.setTestDialog(previousTestDialog);
  }

  private void loginCluster() {
    ToolFactory.getInstance().createOc(client).whenComplete((ocTool, throwable) -> {
      try {
        OdoCluster.INSTANCE.login(ocTool.get());
      } catch (IOException e) {
        throw new RuntimeException("Could not log into " + OdoCluster.CLUSTER_URL + " as user " + OdoCluster.CLUSTER_USER, e);
      }
    });
  }

  private CompletableFuture<OdoFacade> createOdo(KubernetesClient client) {
    return ToolFactory.getInstance()
      .createOdo(client, getProject())
      .thenApply(tool -> new ApplicationRootNodeOdo(tool.get(), false, rootNode, processHelper));
  }

  protected void createProject(String project) throws IOException, ExecutionException, InterruptedException {
    odo.createProject(project);
    odo = createOdo(client).get();
  }

  protected void createComponent(String project, String component, String starter, String projectPath) throws IOException, ExecutionException, InterruptedException {
    createProject(project);
    odo.createComponent("go", REGISTRY_NAME, component, projectPath
      , null, starter);
  }

  protected void createComponent(String project, String component, String projectPath) throws IOException, ExecutionException, InterruptedException {
    createComponent(project, component, null, projectPath);
  }

  protected void cleanLocalProjectDirectory(String projectPath) throws IOException {
    FileUtil.delete(new File(projectPath, PLUGIN_FOLDER).toPath());
    FileUtil.delete(new File(projectPath, "kubernetes").toPath());
    FileUtil.delete(new File(projectPath + "/devfile.yaml").toPath());
  }

  protected OperatorCRD getOperatorCRD(ServiceTemplate serviceTemplate) {
    return serviceTemplate.getCRDs().stream().filter(c -> c.getName().equals(SERVICE_CRD)).findFirst().orElse(null);
  }

  protected ServiceTemplate getServiceTemplate() throws IOException {
    with().pollDelay(10, TimeUnit.SECONDS).await().atMost(5, TimeUnit.MINUTES).until(() -> !odo.getServiceTemplates().isEmpty());
    with().pollDelay(10, TimeUnit.SECONDS).await().atMost(5, TimeUnit.MINUTES).until(() -> odo.getServiceTemplates().stream().anyMatch(s -> s.getName().equals(SERVICE_TEMPLATE + "." + (odo.isOpenShift() ? SERVICE_OPENSHIFT_VERSION : SERVICE_KUBE_VERSION))));
    return odo.getServiceTemplates().stream().filter(s -> s.getName().equals(SERVICE_TEMPLATE + "." + (odo.isOpenShift() ? SERVICE_OPENSHIFT_VERSION : SERVICE_KUBE_VERSION))).findFirst().orElse(null);
  }

  protected void createService(String project, ServiceTemplate serviceTemplate, OperatorCRD crd, String service) throws IOException {
    odo.createService(project, serviceTemplate, crd, service, null, false);
  }
}
