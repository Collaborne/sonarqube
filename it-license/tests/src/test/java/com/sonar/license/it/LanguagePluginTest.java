/*
 * Copyright (C) 2009-2013 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonar.license.it;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.build.SonarRunner;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.ResourceLocation;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.wsclient.services.PropertyUpdateQuery;

import java.io.File;

import static org.fest.assertions.Assertions.assertThat;

public class LanguagePluginTest {

  @Rule
  public Orchestrator orchestrator = Orchestrator.builderEnv()
    .addPlugin(FileLocation.of("../plugins/sonar-faux-cobol-plugin/target/sonar-faux-cobol-plugin-1.0-SNAPSHOT.jar"))
    .addPlugin(FileLocation.of("../plugins/sonar-faux-cpp-plugin/target/sonar-faux-cpp-plugin-1.0-SNAPSHOT.jar"))
    .build();

  /**
   * LICENSE-13
   */
  @Test
  public void ignore_bad_language_license_if_different_project_language() {
    // no licenses

    MavenBuild build = MavenBuild.create(new File("projects/java-sample/pom.xml")).setCleanSonarGoals();
    BuildResult result = orchestrator.executeBuildQuietly(build);
    assertThat(result.getLogs()).excludes("COBOL ENABLED");
  }

  /**
   * LICENSE-13
   */
  @Test
  public void fail_if_bad_language_license() {
    // no licenses

    MavenBuild build = MavenBuild.create(new File("projects/cobol-sample/pom.xml")).setCleanSonarGoals();
    BuildResult result = orchestrator.executeBuildQuietly(build);
    assertThat(result.getStatus()).isNotEqualTo(0);

    if (LicenseVersion.isGreaterThanOrEqualTo(orchestrator, "2.3")) {
      assertThat(result.getLogs()).contains("No license for plugin cobol");
    } else {
      assertThat(result.getLogs()).contains("No valid license for plugin cobol");
    }
  }

  @Test
  public void enable_language_plugin_if_valid_license() {
    // Restore empty profile as it doesn't work for commercial language
    orchestrator.getServer().restoreProfile(ResourceLocation.create("/empty-cobol.xml"));
    String validLicense = orchestrator.plugins().licenses().get("cobol");
    orchestrator.getServer().getAdminWsClient().update(new PropertyUpdateQuery()
      .setKey("sonar.cobol.license.secured")
      .setValue(validLicense));

    // Restart to have empty profile created now that license is valid
    orchestrator.restartSonar();

    MavenBuild build = MavenBuild.create(new File("projects/cobol-sample/pom.xml")).setCleanSonarGoals();

    BuildResult result = orchestrator.executeBuildQuietly(build);
    assertThat(result.getLogs()).contains("cobol plugin licensed to");
    assertThat(result.getLogs()).contains("COBOL ENABLED");
  }

  // LICENSE-29
  @Test
  public void enable_several_languages_in_same_plugin() {
    orchestrator.getServer().restoreProfile(ResourceLocation.create("/empty-cpp.xml"));
    orchestrator.getServer().restoreProfile(ResourceLocation.create("/empty-c.xml"));
    String validLicense = orchestrator.plugins().licenses().get("cpp");
    orchestrator.getServer().getAdminWsClient().update(new PropertyUpdateQuery()
      .setKey("sonar.cpp.license.secured")
      .setValue(validLicense));

    // Restart to have empty profile created now that license is valid
    orchestrator.restartSonar();

    // CPP
    SonarRunner build = SonarRunner.create(new File("projects/cpp-sample/"))
      .setProperty("sonar.language", "cpp");

    BuildResult result = orchestrator.executeBuildQuietly(build);
    assertThat(result.getLogs()).contains("cpp plugin licensed to");
    assertThat(result.getLogs()).contains("CPP ENABLED");

    // C
    build = SonarRunner.create(new File("projects/cpp-sample/"))
      .setProperty("sonar.language", "c");

    result = orchestrator.executeBuildQuietly(build);
    assertThat(result.getLogs()).contains("cpp plugin licensed to");
    assertThat(result.getLogs()).contains("CPP ENABLED");
  }
}
