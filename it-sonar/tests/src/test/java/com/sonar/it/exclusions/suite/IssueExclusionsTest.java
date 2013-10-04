/*
 * Copyright (C) 2009-2012 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonar.it.exclusions.suite;

import com.sonar.it.ItUtils;
import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.SonarRunner;
import com.sonar.orchestrator.locator.FileLocation;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.sonar.wsclient.services.Resource;
import org.sonar.wsclient.services.ResourceQuery;

import static org.fest.assertions.Assertions.assertThat;

public class IssueExclusionsTest {

  private static final String PROJECT_KEY = "com.sonarsource.it.samples:multi-modules-exclusions";
  private static final String PROJECT_DIR = "exclusions/xoo-multi-modules";

  @ClassRule
  public static Orchestrator orchestrator = ExclusionsTestSuite.ORCHESTRATOR;

  @Before
  public void resetData() {
    orchestrator.getDatabase().truncateInspectionTables();
  }

  @Test
  public void should_not_exclude_anything() {
    scan();

    checkIssueCountBySeverity(70, 2, 57, 4, 0, 7);
  }

  @Test
  public void should_ignore_all_files() {
    scan(
      "sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "**/*.xoo",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*",
      "sonar.issue.ignore.multicriteria.1.lineRange", "*"
      );

    checkIssueCountBySeverity(7, 0, 0, 0, 0, 7);
  }

  @Test
  public void should_enforce_only_on_one_file() {
    scan(
      "sonar.issue.enforce.multicriteria", "1",
      "sonar.issue.enforce.multicriteria.1.resourceKey", "**/HelloA1.xoo",
      "sonar.issue.enforce.multicriteria.1.ruleKey", "*",
      "sonar.issue.enforce.multicriteria.1.lineRange", "*"
      );

    checkIssueCountBySeverity(
      1 /* tag */ + 18 /* lines in HelloA1.xoo */ + 1 /* file */ + 7,
      0 + 1,
      0 + 18,
      0 + 1,
      0,
      7);
  }


  @Test
  public void should_ignore_files_with_regexp() {
    scan(
      "sonar.issue.ignore.allfile", "1",
      "sonar.issue.ignore.allfile.1.fileRegexp", "EXTERMINATE-ALL-ISSUES"
      );

    checkIssueCountBySeverity(
      70 - 1 /* tag */ - 18 /* lines in HelloA1.xoo */ - 1 /* file */,
      2 - 1,
      57 - 18,
      4 - 1,
      0,
      7);
  }

  @Test
  public void should_ignore_block_with_regexp() {
    scan(
      "sonar.issue.ignore.block", "1",
      "sonar.issue.ignore.block.1.beginBlockRegexp", "MUTE-SONAR",
      "sonar.issue.ignore.block.1.endBlockRegexp", "UNMUTE-SONAR"
      );

    checkIssueCountBySeverity(
      70 - 1 /* tag */ - 5 /* lines in HelloA2.xoo */,
      2 - 1,
      57 - 5,
      4,
      0,
      7);
  }

  @Test
  public void should_ignore_to_end_of_file() {
    scan(
      "sonar.issue.ignore.block", "1",
      "sonar.issue.ignore.block.1.beginBlockRegexp", "MUTE-SONAR",
      "sonar.issue.ignore.block.1.endBlockRegexp", ""
      );

    checkIssueCountBySeverity(
      70 - 1 /* tag */ - 7 /* remaining lines in HelloA2.xoo */,
      2 - 1,
      57 - 7,
      4,
      0,
      7);
  }

  @Test
  public void should_ignore_one_per_line_on_single_package() {
    scan(
      "sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "com/sonar/it/samples/modules/a1/*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "xoo:OneIssuePerLine",
      "sonar.issue.ignore.multicriteria.1.lineRange", "*"
      );

    checkIssueCountBySeverity(
      70 - 18 /* lines in HelloA1.xoo */,
      2,
      57 - 18,
      4,
      0,
      7);
  }

  @Test
  public void should_ignore_one_per_line_everywhere_on_line_range() {
    scan(
      "sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "**/*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "xoo:OneIssuePerLine",
      "sonar.issue.ignore.multicriteria.1.lineRange", "[3-5]"
      );

    checkIssueCountBySeverity(
      70 - 4 * 3 /* 3 lines per file */,
      2,
      57 - 4 * 3,
      4,
      0,
      7);
  }

  @Test
  public void should_apply_exclusions_from_multiple_sources() {
    scan(
      "sonar.issue.ignore.allfile", "1",
      "sonar.issue.ignore.allfile.1.fileRegexp", "EXTERMINATE-ALL-ISSUES",
      "sonar.issue.ignore.block", "1",
      "sonar.issue.ignore.block.1.beginBlockRegexp", "MUTE-SONAR",
      "sonar.issue.ignore.block.1.endBlockRegexp", "UNMUTE-SONAR",
      "sonar.issue.ignore.multicriteria", "1,2",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "com/sonar/it/samples/modules/b1/*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "xoo:OneIssuePerLine",
      "sonar.issue.ignore.multicriteria.1.lineRange", "*",
      "sonar.issue.ignore.multicriteria.2.resourceKey", "**/*",
      "sonar.issue.ignore.multicriteria.2.ruleKey", "xoo:OneIssuePerLine",
      "sonar.issue.ignore.multicriteria.2.lineRange", "[3-5]"
    );

    checkIssueCountBySeverity(
      70 - 1 /* tag in HelloA1.xoo */ - 1 /* tag in HelloA2.xoo */
        - 18 /* lines in HelloA1.xoo */ - 5 /* lines in HelloA2.xoo */ - 12 /* lines in HelloB1.xoo */
        - (4 - 2) * 3 /* 3 lines per file, HelloA1.xoo and HelloB1.xoo already silenced */
        - 1 /* HelloA1.xoo file */,
      2 - 2,
      57 - 18 - 5 - 12 - (4 - 2) * 3,
      4 - 1,
      0,
      7);
  }

  @Test
  public void should_log_bad_line_range() {
    checkAnalysisFails(
      "sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "**/*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*",
      "sonar.issue.ignore.multicriteria.1.lineRange", "notALineRange"
      );
  }

  @Test
  public void should_log_missing_resource_key() {
    checkAnalysisFails(
      "sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*",
      "sonar.issue.ignore.multicriteria.1.lineRange", "[]"
      );
  }

  @Test
  public void should_log_missing_rule_key() {
    checkAnalysisFails(
      "sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "",
      "sonar.issue.ignore.multicriteria.1.lineRange", "[]"
      );
  }

  @Test
  public void should_log_missing_block_start() {
    checkAnalysisFails(
      "sonar.issue.ignore.block", "1",
      "sonar.issue.ignore.block.1.beginBlockRegexp", "",
      "sonar.issue.ignore.block.1.endBlockRegexp", "UNMUTE-SONAR"
    );
  }

  @Test
  public void should_log_missing_whole_file_regexp() {
    checkAnalysisFails(
      "sonar.issue.ignore.allfile", "1",
      "sonar.issue.ignore.allfile.1.fileRegexp", ""
    );
  }

  @Test
  public void should_not_ignore_issues_if_line_range_is_empty() {
    scan(
      "sonar.issue.ignore.multicriteria", "1",
      "sonar.issue.ignore.multicriteria.1.resourceKey", "**/*",
      "sonar.issue.ignore.multicriteria.1.ruleKey", "*",
      "sonar.issue.ignore.multicriteria.1.lineRange", "[]"
      );

    checkIssueCountBySeverity(70, 2, 57, 4, 0, 7);
  }

  protected BuildResult scan(String... properties) {
    orchestrator.getServer().restoreProfile(FileLocation.ofClasspath("/com/sonar/it/issue/IssueTest/with-many-rules.xml"));
    SonarRunner scan = SonarRunner.create(ItUtils.locateProjectDir(PROJECT_DIR))
      .setProperties("sonar.cpd.skip", "true")
      .setProperties(properties)
      //.setProperties("sonar.verbose", "true")
      .setProfile("with-many-rules");
    return orchestrator.executeBuildQuietly(scan);
  }

  private void checkIssueCountBySeverity(int total, int taggedXoo, int perLine, int perFile, int blocker, int perModule) {
    Resource project = orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics(PROJECT_KEY, "violations", "info_violations", "minor_violations", "major_violations",
      "blocker_violations", "critical_violations"));
    assertThat(project.getMeasureIntValue("violations")).isEqualTo(total);
    assertThat(project.getMeasureIntValue("info_violations")).isEqualTo(taggedXoo);     // Has tag 'xoo'
    assertThat(project.getMeasureIntValue("minor_violations")).isEqualTo(perLine);      // One per line
    assertThat(project.getMeasureIntValue("major_violations")).isEqualTo(perFile);      // One per file
    assertThat(project.getMeasureIntValue("blocker_violations")).isEqualTo(blocker);
    assertThat(project.getMeasureIntValue("critical_violations")).isEqualTo(perModule); // One per module
  }

  private void checkAnalysisFails(String... properties) {
    BuildResult buildResult = scan(properties);
    assertThat(buildResult.getStatus()).isNotEqualTo(0);
    assertThat(buildResult.getLogs().indexOf("SonarException")).isGreaterThan(0);
  }
}
