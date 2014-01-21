/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2013 SonarSource
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
package org.sonar.plugins.cpd;

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.resources.AbstractLanguage;
import org.sonar.api.resources.Java;
import org.sonar.api.resources.Language;
import org.sonar.api.resources.Languages;
import org.sonar.batch.scan.language.ModuleLanguages;
import org.sonar.plugins.cpd.index.IndexFactory;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class CpdSensorTest {

  SonarEngine sonarEngine;
  SonarBridgeEngine sonarBridgeEngine;
  CpdSensor sensor;
  Settings settings;
  private Language phpLanguage;

  @Before
  public void setUp() {
    IndexFactory indexFactory = mock(IndexFactory.class);
    sonarEngine = new SonarEngine(indexFactory, null, null);
    sonarBridgeEngine = new SonarBridgeEngine(indexFactory, null, null);
    settings = new Settings(new PropertyDefinitions(CpdPlugin.class));
    phpLanguage = new AbstractLanguage("php", "PHP") {

      @Override
      public String[] getFileSuffixes() {
        return null;
      }
    };
    sensor = new CpdSensor(sonarEngine, sonarBridgeEngine, settings, new ModuleLanguages(settings, new Languages()));
  }

  @Test
  public void test_global_skip() {
    settings.setProperty("sonar.cpd.skip", true);
    assertThat(sensor.isSkipped(Java.INSTANCE)).isTrue();
  }

  @Test
  public void should_not_skip_by_default() {
    assertThat(sensor.isSkipped(Java.INSTANCE)).isFalse();
  }

  @Test
  public void should_skip_by_language() {
    settings.setProperty("sonar.cpd.skip", false);
    settings.setProperty("sonar.cpd.php.skip", true);

    assertThat(sensor.isSkipped(phpLanguage)).isTrue();
    assertThat(sensor.isSkipped(Java.INSTANCE)).isFalse();
  }

  @Test
  public void test_engine() {
    assertThat(sensor.getEngine(Java.INSTANCE)).isSameAs(sonarEngine);
    assertThat(sensor.getEngine(phpLanguage)).isSameAs(sonarBridgeEngine);
  }

}
