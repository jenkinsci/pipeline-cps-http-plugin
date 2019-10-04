/*
 * The MIT License
 *
 * Copyright 2019 Coveo Solutions Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflowhttp.cps;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.Queue;
import hudson.model.*;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.httpclient.RobustHTTPClient;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpGet;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsFlowFactoryAction2;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.flow.*;
import org.kohsuke.stapler.*;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.JOB;

@PersistIn(JOB)
public class CpsHttpFlowDefinition extends FlowDefinition {

  private final String scriptUrl;
  private final int retryCount;
  private final CachingConfiguration cachingConfiguration;
  private String credentialsId;

  @DataBoundConstructor
  public CpsHttpFlowDefinition(
      String scriptUrl, int retryCount, CachingConfiguration cachingConfiguration) {
    this.scriptUrl = scriptUrl.trim();
    this.retryCount = retryCount;
    this.cachingConfiguration = cachingConfiguration;
  }

  public String getScriptUrl() {
    return scriptUrl;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public CachingConfiguration getCachingConfiguration() {
    return cachingConfiguration;
  }

  public Instant getExpirationDate() {
    return Instant.now().plusSeconds(cachingConfiguration.getCachingSeconds());
  }

  public String getCredentialsId() {
    return credentialsId;
  }

  @DataBoundSetter
  public void setCredentialsId(String credentialsId) {
    this.credentialsId = Util.fixEmpty(credentialsId);
  }

  @Override
  public CpsFlowExecution create(
      FlowExecutionOwner owner, TaskListener listener, List<? extends Action> actions)
      throws Exception {

    // This little bit of code allows replays to work
    for (Action a : actions) {
      if (a instanceof CpsFlowFactoryAction2) {
        return ((CpsFlowFactoryAction2) a).create(this, owner, actions);
      }
    }

    Queue.Executable _build = owner.getExecutable();
    if (!(_build instanceof Run)) {
      throw new IOException("Can only pull a Jenkinsfile in a run");
    }
    Run<?, ?> build = (Run<?, ?>) _build;

    EnvVars envVars = build.getEnvironment(listener);
    String expandedScriptUrl = envVars.expand(scriptUrl);
    listener.getLogger().println("Fetching pipeline from " + expandedScriptUrl);

    RobustHTTPClient client = new RobustHTTPClient();
    client.setStopAfterAttemptNumber(retryCount + 1);

    HttpGet httpGet = new HttpGet(expandedScriptUrl);
    if (credentialsId != null) {
      UsernamePasswordCredentials credentials =
          CredentialsMatchers.firstOrNull(
              CredentialsProvider.lookupCredentials(
                  UsernamePasswordCredentials.class,
                  Jenkins.get(),
                  ACL.SYSTEM,
                  Collections.emptyList()),
              CredentialsMatchers.withId(credentialsId));
      if (credentials != null) {
        String encoded =
            Base64.getEncoder()
                .encodeToString(
                    (credentials.getUsername() + ":" + credentials.getPassword())
                        .getBytes(StandardCharsets.UTF_8));
        httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        CredentialsProvider.track(build, credentials);
      }
    }

    AtomicReference<String> scriptReference = new AtomicReference<>(null);

    boolean shouldCache = true;
    if (cachingConfiguration == null) {
      listener.getLogger().println("No caching config. Fetching from HTTP");
      shouldCache = false;
    }
    if (shouldCache && cachingConfiguration.isExcluded(envVars)) {
      listener.getLogger().println("Caching excluded from environment variables");
      shouldCache = false;
    }

    if (shouldCache) {
      Map<String, CacheEntry> pipelineCache = CacheEntry.cache;

      if (pipelineCache.containsKey(expandedScriptUrl)) {
        listener.getLogger().println("Fetching from cache");
        if (pipelineCache.get(expandedScriptUrl).expirationDate.isBefore(Instant.now())) {
          listener.getLogger().println("Cache is expired. Clearing");
          pipelineCache.remove(expandedScriptUrl);
        }
      } else {
        listener.getLogger().println("Cache miss. Actually fetching from HTTP");
      }

      if (!pipelineCache.containsKey(expandedScriptUrl)) {
        client.connect(
            "get pipeline",
            "get pipeline from " + expandedScriptUrl,
            c -> c.execute(httpGet),
            response -> {
              try (InputStream is = response.getEntity().getContent()) {
                String script = IOUtils.toString(is, "UTF-8");
                pipelineCache.put(expandedScriptUrl, new CacheEntry(getExpirationDate(), script));
              }
            },
            listener);
      }

      scriptReference.set(pipelineCache.get(expandedScriptUrl).script);
    } else {
      client.connect(
          "get pipeline",
          "get pipeline from " + expandedScriptUrl,
          c -> c.execute(httpGet),
          response -> {
            try (InputStream is = response.getEntity().getContent()) {
              String script = IOUtils.toString(is, "UTF-8");
              scriptReference.set(script);
            }
          },
          listener);
    }

    Queue.Executable queueExec = owner.getExecutable();
    FlowDurabilityHint hint =
        (queueExec instanceof Run)
            ? DurabilityHintProvider.suggestedFor(((Run) queueExec).getParent())
            : GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint();
    return new CpsFlowExecution(scriptReference.get(), true, owner, hint);
  }

  @Extension
  public static class DescriptorImpl extends FlowDefinitionDescriptor {
    @Override
    @Nonnull
    public String getDisplayName() {
      return "Pipeline script from HTTP";
    }

    public Collection<? extends SCMDescriptor<?>> getApplicableDescriptors() {
      StaplerRequest req = Stapler.getCurrentRequest();
      Job<?, ?> job = req != null ? req.findAncestorObject(Job.class) : null;
      return SCM._for(job);
    }

    public FormValidation doCheckExcludedCasesString(@QueryParameter String value) {
      for (String splitValue : value.split(" ")) {
        splitValue = splitValue.trim();
        if (splitValue.isEmpty()) {
          continue;
        }
        if (splitValue.split("=").length != 2) {
          return FormValidation.error("Each entry must contain one equal sign");
        }
      }

      return FormValidation.ok();
    }

    public ListBoxModel doFillCredentialsIdItems(
        @AncestorInPath Item item,
        @QueryParameter String scriptUrl,
        @QueryParameter String credentialsId) {
      final StandardListBoxModel result = new StandardListBoxModel();
      if (item == null) {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
          return result.includeCurrentValue(credentialsId);
        }
      } else {
        if (!item.hasPermission(Item.EXTENDED_READ)
            && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
          return result.includeCurrentValue(credentialsId);
        }
      }
      return result
          .includeEmptyValue()
          .includeMatchingAs(
              ACL.SYSTEM,
              Jenkins.get(),
              StandardUsernamePasswordCredentials.class,
              URIRequirementBuilder.fromUri(scriptUrl).build(),
              CredentialsMatchers.always())
          .includeCurrentValue(credentialsId);
    }
  }
}
