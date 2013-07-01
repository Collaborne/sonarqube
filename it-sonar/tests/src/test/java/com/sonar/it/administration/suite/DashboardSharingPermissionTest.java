/*
 * Copyright (C) 2009-2012 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */

package com.sonar.it.administration.suite;

import com.sonar.it.ItUtils;
import com.sonar.orchestrator.Orchestrator;
import org.junit.ClassRule;
import org.junit.Test;
import org.sonar.wsclient.SonarClient;
import org.sonar.wsclient.user.UserParameters;

public class DashboardSharingPermissionTest {

  @ClassRule
  public static Orchestrator orchestrator = AdministrationTestSuite.ORCHESTRATOR;

  /**
   * SONAR-4099
   */
  @Test
  public void should_enable_dashboard_sharing() throws Exception {

    SonarClient client = ItUtils.newWsClientForAdmin(orchestrator);

    UserParameters canShareDashboard = UserParameters.create().login("can_share_dashboards").name("can_share_dashboards").password("password").passwordConfirmation("password");
    client.userClient().create(canShareDashboard);
    UserParameters cannotShareDashboard = UserParameters.create().login("cannot_share_dashboards").name("cannot_share_dashboards").password("password").passwordConfirmation("password");
    client.userClient().create(cannotShareDashboard);





  }
}
