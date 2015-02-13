/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.issue.index;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import org.apache.commons.dbutils.DbUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.sonar.core.persistence.DbTester;
import org.sonar.server.db.DbClient;
import org.sonar.test.DbTests;

import java.sql.Connection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Category(DbTests.class)
public class IssueResultSetIteratorTest {

  @Rule
  public DbTester dbTester = new DbTester();

  DbClient client;
  Connection connection;

  @Before
  public void setUp() throws Exception {
    client = new DbClient(dbTester.database(), dbTester.myBatis());
    connection = dbTester.openConnection();
  }

  @After
  public void tearDown() throws Exception {
    DbUtils.closeQuietly(connection);
  }

  @Test
  public void iterator_over_issues() throws Exception {
    dbTester.prepareDbUnit(getClass(), "shared.xml");
    IssueResultSetIterator it = IssueResultSetIterator.create(client, connection, 0L);
    Map<String, IssueDoc> issuesByKey = Maps.uniqueIndex(it, new Function<IssueDoc, String>() {
      @Override
      public String apply(IssueDoc issue) {
        return issue.key();
      }
    });
    it.close();

    assertThat(issuesByKey).hasSize(3);

    IssueDoc issue = issuesByKey.get("ABC");
    assertThat(issue.key()).isEqualTo("ABC");
    assertThat(issue.assignee()).isEqualTo("guy1");
    assertThat(issue.componentUuid()).isEqualTo("FILE1");
    assertThat(issue.projectUuid()).isEqualTo("PROJECT1");
    assertThat(issue.moduleUuid()).isEqualTo("PROJECT1");
    assertThat(issue.modulePath()).isEqualTo(".PROJECT1.");
    assertThat(issue.filePath()).isEqualTo("src/main/java/Action.java");
    assertThat(issue.tags()).containsOnly("tag1", "tag2", "tag3");
    assertThat(issue.debt().toMinutes()).isGreaterThan(0L);

    issue = issuesByKey.get("BCD");
    assertThat(issue.key()).isEqualTo("BCD");
    assertThat(issue.assignee()).isEqualTo("guy1");
    assertThat(issue.componentUuid()).isEqualTo("MODULE1");
    assertThat(issue.projectUuid()).isEqualTo("PROJECT1");
    assertThat(issue.moduleUuid()).isEqualTo("MODULE1");
    assertThat(issue.modulePath()).isEqualTo(".PROJECT1.MODULE1.");
    assertThat(issue.filePath()).isNull();
    assertThat(issue.tags()).containsOnly("tag1", "tag2", "tag3");
    assertThat(issue.debt().toMinutes()).isGreaterThan(0L);

    issue = issuesByKey.get("DEF");
    assertThat(issue.key()).isEqualTo("DEF");
    assertThat(issue.assignee()).isEqualTo("guy2");
    assertThat(issue.componentUuid()).isEqualTo("FILE1");
    assertThat(issue.projectUuid()).isEqualTo("PROJECT1");
    assertThat(issue.moduleUuid()).isEqualTo("PROJECT1");
    assertThat(issue.modulePath()).isEqualTo(".PROJECT1.");
    assertThat(issue.filePath()).isEqualTo("src/main/java/Action.java");
    assertThat(issue.tags()).isEmpty();
    assertThat(issue.debt().toMinutes()).isGreaterThan(0L);
  }

  @Test
  public void extract_directory_path() throws Exception {
    dbTester.prepareDbUnit(getClass(), "extract_directory_path.xml");
    IssueResultSetIterator it = IssueResultSetIterator.create(client, connection, 0L);
    Map<String, IssueDoc> issuesByKey = Maps.uniqueIndex(it, new Function<IssueDoc, String>() {
      @Override
      public String apply(IssueDoc issue) {
        return issue.key();
      }
    });
    it.close();

    assertThat(issuesByKey).hasSize(3);

    // File in sub directoy
    assertThat(issuesByKey.get("ABC").directoryPath()).isEqualTo("src/main/java");

    // File in root directoy
    assertThat(issuesByKey.get("DEF").directoryPath()).isEqualTo("/");

    // Project
    assertThat(issuesByKey.get("EFG").directoryPath()).isNull();
  }

  @Test
  public void select_after_date() throws Exception {
    dbTester.prepareDbUnit(getClass(), "shared.xml");
    IssueResultSetIterator it = IssueResultSetIterator.create(client, connection, 1420000000000L);

    assertThat(it.hasNext()).isTrue();
    IssueDoc issue = it.next();
    assertThat(issue.key()).isEqualTo("DEF");

    assertThat(it.hasNext()).isFalse();
    it.close();
  }
}
