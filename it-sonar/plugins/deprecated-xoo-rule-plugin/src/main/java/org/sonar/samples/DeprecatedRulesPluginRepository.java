package org.sonar.samples;

import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RuleRepository;

import java.util.Arrays;
import java.util.List;

public final class DeprecatedRulesPluginRepository extends RuleRepository {

  public DeprecatedRulesPluginRepository() {
    super("deprecated-repo", "xoo");
    setName("Deprecated Repo");
  }

  @Override
  public List<Rule> createRules() {
    return Arrays.asList(
      Rule.create("deprecated-repo", "deprecated-rule", "Deprecated rule").setStatus("DEPRECATED")
    );
  }
}