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
package org.sonar.batch.sensor;

import com.google.common.base.Preconditions;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.measure.MetricFinder;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.sensor.duplication.Duplication;
import org.sonar.api.batch.sensor.duplication.internal.DefaultDuplication;
import org.sonar.api.batch.sensor.highlighting.internal.DefaultHighlighting;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.issue.Issue.Severity;
import org.sonar.api.batch.sensor.measure.Measure;
import org.sonar.api.batch.sensor.measure.internal.DefaultMeasure;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.config.Settings;
import org.sonar.api.design.Dependency;
import org.sonar.api.issue.Issuable;
import org.sonar.api.issue.internal.DefaultIssue;
import org.sonar.api.measures.Formula;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.PersistenceMode;
import org.sonar.api.measures.SumChildDistributionFormula;
import org.sonar.api.resources.Directory;
import org.sonar.api.resources.File;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.Scopes;
import org.sonar.api.rule.RuleKey;
import org.sonar.batch.duplication.DuplicationCache;
import org.sonar.batch.highlighting.SyntaxHighlightingData;
import org.sonar.batch.index.BatchResource;
import org.sonar.batch.index.ComponentDataCache;
import org.sonar.batch.index.DefaultIndex;
import org.sonar.batch.index.ResourceCache;
import org.sonar.batch.sensor.coverage.CoverageExclusions;
import org.sonar.core.component.ComponentKeys;
import org.sonar.core.source.SnapshotDataTypes;

public class DefaultSensorStorage implements SensorStorage {

  private static final String USES = "USES";
  private final MetricFinder metricFinder;
  private final Project project;
  private final ResourcePerspectives perspectives;
  private final DefaultIndex sonarIndex;
  private final CoverageExclusions coverageExclusions;
  private final DuplicationCache duplicationCache;
  private final ResourceCache resourceCache;
  private final ComponentDataCache componentDataCache;

  public DefaultSensorStorage(MetricFinder metricFinder, Project project,
    ResourcePerspectives perspectives,
    Settings settings, FileSystem fs, ActiveRules activeRules, ComponentDataCache componentDataCache,
    DuplicationCache duplicationCache, DefaultIndex sonarIndex, CoverageExclusions coverageExclusions,
    ResourceCache resourceCache) {
    this.metricFinder = metricFinder;
    this.project = project;
    this.perspectives = perspectives;
    this.componentDataCache = componentDataCache;
    this.sonarIndex = sonarIndex;
    this.coverageExclusions = coverageExclusions;
    this.duplicationCache = duplicationCache;
    this.resourceCache = resourceCache;
  }

  private Metric findMetricOrFail(String metricKey) {
    Metric m = (Metric) metricFinder.findByKey(metricKey);
    if (m == null) {
      throw new IllegalStateException("Unknow metric with key: " + metricKey);
    }
    return m;
  }

  @Override
  public void store(Measure newMeasure) {
    DefaultMeasure measure = (DefaultMeasure) newMeasure;
    org.sonar.api.measures.Metric m = findMetricOrFail(measure.metric().key());
    org.sonar.api.measures.Measure measureToSave = new org.sonar.api.measures.Measure(m);
    setValueAccordingToMetricType(newMeasure, m, measureToSave);
    measureToSave.setFromCore(measure.isFromCore());
    InputFile inputFile = newMeasure.inputFile();
    if (inputFile != null) {
      Formula formula = newMeasure.metric() instanceof org.sonar.api.measures.Metric ?
        ((org.sonar.api.measures.Metric) newMeasure.metric()).getFormula() : null;
      if (formula instanceof SumChildDistributionFormula
        && !Scopes.isHigherThanOrEquals(Scopes.FILE, ((SumChildDistributionFormula) formula).getMinimumScopeToPersist())) {
        measureToSave.setPersistenceMode(PersistenceMode.MEMORY);
      }
      File sonarFile = getFile(inputFile);
      if (coverageExclusions.accept(sonarFile, measureToSave)) {
        sonarIndex.addMeasure(sonarFile, measureToSave);
      }
    } else {
      sonarIndex.addMeasure(project, measureToSave);
    }
  }

  private void setValueAccordingToMetricType(Measure<?> measure, org.sonar.api.measures.Metric<?> m, org.sonar.api.measures.Measure measureToSave) {
    switch (m.getType()) {
      case BOOL:
        measureToSave.setValue(Boolean.TRUE.equals(measure.value()) ? 1.0 : 0.0);
        break;
      case INT:
      case MILLISEC:
        measureToSave.setValue(Double.valueOf((Integer) measure.value()));
        break;
      case FLOAT:
      case PERCENT:
      case RATING:
        measureToSave.setValue((Double) measure.value());
        break;
      case STRING:
      case LEVEL:
      case DATA:
      case DISTRIB:
        measureToSave.setData((String) measure.value());
        break;
      case WORK_DUR:
        measureToSave.setValue(Double.valueOf((Long) measure.value()));
        break;
      default:
        throw new UnsupportedOperationException("Unsupported type :" + m.getType());
    }
  }

  @Override
  public void store(Issue issue) {
    Resource r;
    InputPath inputPath = issue.inputPath();
    if (inputPath != null) {
      if (inputPath instanceof InputDir) {
        r = Directory.create(inputPath.relativePath());
      } else {
        r = File.create(inputPath.relativePath());
      }
    } else {
      r = project;
    }
    Issuable issuable = perspectives.as(Issuable.class, r);
    if (issuable == null) {
      return;
    }
    issuable.addIssue(toDefaultIssue(project.getKey(), ComponentKeys.createEffectiveKey(project, r), issue));
  }

  public static DefaultIssue toDefaultIssue(String projectKey, String componentKey, Issue issue) {
    Severity overridenSeverity = issue.overridenSeverity();
    return new org.sonar.core.issue.DefaultIssueBuilder()
      .componentKey(componentKey)
      .projectKey(projectKey)
      .ruleKey(RuleKey.of(issue.ruleKey().repository(), issue.ruleKey().rule()))
      .effortToFix(issue.effortToFix())
      .line(issue.line())
      .message(issue.message())
      .severity(overridenSeverity != null ? overridenSeverity.name() : null)
      .build();
  }

  private File getTestResource(InputFile testFile) {
    File testRes = File.create(testFile.relativePath());
    testRes.setQualifier(Qualifiers.UNIT_TEST_FILE);
    // Reload
    testRes = sonarIndex.getResource(testRes);
    if (testRes == null) {
      throw new IllegalArgumentException("Provided input file is not indexed or not a test file: " + testFile);
    }
    return testRes;
  }

  private File getMainResource(InputFile mainFile) {
    File mainRes = File.create(mainFile.relativePath());
    // Reload
    mainRes = sonarIndex.getResource(mainRes);
    if (mainRes == null) {
      throw new IllegalArgumentException("Provided input file is not indexed or not a main file: " + mainRes);
    }
    return mainRes;
  }

  private File getFile(InputFile file) {
    if (file.type() == InputFile.Type.MAIN) {
      return getMainResource(file);
    } else {
      return getTestResource(file);
    }
  }

  @Override
  public void store(org.sonar.api.batch.sensor.dependency.Dependency dep) {
    BatchResource fromBatchResource = resourceCache.get(dep.fromKey());
    BatchResource toBatchResource = resourceCache.get(dep.toKey());
    Preconditions.checkNotNull(fromBatchResource, "Unable to find origin resource " + dep.fromKey());
    Preconditions.checkNotNull(toBatchResource, "Unable to find destination resource " + dep.toKey());
    File fromResource = (File) fromBatchResource.resource();
    File toResource = (File) toBatchResource.resource();
    if (sonarIndex.getEdge(fromResource, toResource) != null) {
      throw new IllegalStateException("Dependency between " + dep.fromKey() + " and " + dep.toKey() + " was already saved.");
    }
    Directory fromParent = fromResource.getParent();
    Directory toParent = toResource.getParent();
    Dependency parentDep = null;
    if (!fromParent.equals(toParent)) {
      parentDep = sonarIndex.getEdge(fromParent, toParent);
      if (parentDep != null) {
        parentDep.setWeight(parentDep.getWeight() + 1);
      } else {
        parentDep = new Dependency(fromParent, toParent).setUsage(USES).setWeight(1);
        parentDep = sonarIndex.addDependency(parentDep);
      }
    }
    sonarIndex.addDependency(new Dependency(fromResource, toResource)
      .setUsage(USES)
      .setWeight(dep.weight())
      .setParent(parentDep));
  }

  @Override
  public void store(Duplication duplication) {
    duplicationCache.put(duplication.originBlock().resourceKey(), (DefaultDuplication) duplication);
  }

  @Override
  public void store(DefaultHighlighting highlighting) {
    String componentKey = ((DefaultInputFile) highlighting.inputFile()).key();
    componentDataCache.setData(componentKey, SnapshotDataTypes.SYNTAX_HIGHLIGHTING, new SyntaxHighlightingData(highlighting.getSyntaxHighlightingRuleSet()));
  }
}
