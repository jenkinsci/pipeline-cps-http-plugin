package org.jenkinsci.plugins.workflowhttp.cps;

import hudson.EnvVars;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.List;

public class CachingConfiguration {
  private final int cachingMinutes;
  private final List<String[]> excludedCases;

  @DataBoundConstructor
  public CachingConfiguration(int cachingMinutes, String excludedCasesString) {
    this.cachingMinutes = cachingMinutes;
    this.excludedCases = new ArrayList<>();
    for (String excludedCase : excludedCasesString.split(" ")) {
      String[] values = excludedCase.split("=");
      if (values.length == 2) {
        this.excludedCases.add(new String[] {values[0], values[1]});
      }
    }
  }

  private static String createRegexFromGlob(String glob) {
    StringBuilder out = new StringBuilder("^");
    for (int i = 0; i < glob.length(); ++i) {
      final char c = glob.charAt(i);
      switch (c) {
        case '*':
          out.append(".*");
          break;
        case '?':
          out.append('.');
          break;
        case '.':
          out.append("\\.");
          break;
        case '\\':
          out.append("\\\\");
          break;
        default:
          out.append(c);
      }
    }
    out.append('$');
    return out.toString();
  }

  public int getCachingMinutes() {
    return cachingMinutes;
  }

  public int getCachingSeconds() {
    return cachingMinutes * 60;
  }

  public String getExcludedCasesString() {
    List<String> results = new ArrayList<>();
    for (String[] pair : excludedCases) {
      results.add(pair[0] + "=" + pair[1]);
    }
    return String.join(" ", results);
  }

  public boolean isExcluded(EnvVars envVars) {
    for (String[] pair : excludedCases) {
      String key = pair[0];
      String value = pair[1];
      String pattern = createRegexFromGlob(value);
      String envVar = envVars.get(key);
      if (envVar != null && envVar.matches(pattern)) {
        return true;
      }
    }
    return false;
  }
}
