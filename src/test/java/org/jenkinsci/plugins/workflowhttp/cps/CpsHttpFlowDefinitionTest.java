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

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class CpsHttpFlowDefinitionTest {

    @Test
    void testRunScriptFromUserContentWithDefault(JenkinsRule r) throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        Path path =
                Paths.get(r.jenkins.getRootPath().getRemote(), "userContent", "testRunScriptFromUserContent.groovy");
        Files.write(path, "echo 'Hello from HTTP'".getBytes());

        String url = r.jenkins.getRootUrl() + "userContent/testRunScriptFromUserContent.groovy";
        CpsHttpFlowDefinition def = new CpsHttpFlowDefinition(url);
        p.setDefinition(def);
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("Fetching pipeline from " + url, b);
        r.assertLogContains("No caching config. Fetching from HTTP", b);
        r.assertLogContains("Hello from HTTP", b);
    }

    @Test
    void testRunScriptFromUserContent(JenkinsRule r) throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        Path path =
                Paths.get(r.jenkins.getRootPath().getRemote(), "userContent", "testRunScriptFromUserContent.groovy");
        Files.write(path, "echo 'Hello from HTTP'".getBytes());

        String url = r.jenkins.getRootUrl() + "userContent/testRunScriptFromUserContent.groovy";
        CpsHttpFlowDefinition def = new CpsHttpFlowDefinition(url);
        def.setSetAcceptHeader("");
        def.setSetKeyHeader("key");
        def.setSetValueHeader("value");
        def.setRetryCount(3);
        def.setCachingConfiguration(null);

        p.setDefinition(def);
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("Fetching pipeline from " + url, b);
        r.assertLogContains("No caching config. Fetching from HTTP", b);
        r.assertLogContains("Hello from HTTP", b);
    }

    @Test
    void testFetchAgainWithoutCaching(JenkinsRule r) throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        Path path =
                Paths.get(r.jenkins.getRootPath().getRemote(), "userContent", "testFetchAgainWithoutCaching.groovy");
        Files.write(path, "echo 'Hello from HTTP'".getBytes());
        String url = r.jenkins.getRootUrl() + "userContent/testFetchAgainWithoutCaching.groovy";

        CpsHttpFlowDefinition def = new CpsHttpFlowDefinition(url);
        def.setSetAcceptHeader("");
        def.setSetKeyHeader("");
        def.setSetValueHeader("");
        def.setRetryCount(3);
        def.setCachingConfiguration(null);

        p.setDefinition(def);
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("Fetching pipeline from " + url, b);
        r.assertLogContains("No caching config. Fetching from HTTP", b);
        r.assertLogContains("Hello from HTTP", b);

        Files.write(path, "echo 'Hello from HTTP 2'".getBytes());
        def = new CpsHttpFlowDefinition(url);
        def.setSetAcceptHeader("");
        def.setSetKeyHeader("");
        def.setSetValueHeader("");
        def.setRetryCount(3);
        def.setCachingConfiguration(null);

        p.setDefinition(def);
        b = r.buildAndAssertSuccess(p);
        r.assertLogContains("Fetching pipeline from " + url, b);
        r.assertLogContains("No caching config. Fetching from HTTP", b);
        r.assertLogContains("Hello from HTTP 2", b);
    }

    @Test
    void testFetchAgainWithCaching(JenkinsRule r) throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        Path path = Paths.get(r.jenkins.getRootPath().getRemote(), "userContent", "testFetchAgainWithCaching.groovy");
        Files.write(path, "echo 'Hello from HTTP'".getBytes());
        String url = r.jenkins.getRootUrl() + "userContent/testFetchAgainWithCaching.groovy";

        CpsHttpFlowDefinition def = new CpsHttpFlowDefinition(url);
        def.setSetAcceptHeader("");
        def.setSetKeyHeader("");
        def.setSetValueHeader("");
        def.setRetryCount(3);
        def.setCachingConfiguration(new CachingConfiguration(5, ""));

        p.setDefinition(def);
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("Fetching pipeline from " + url, b);
        r.assertLogContains("Cache miss. Actually fetching from HTTP", b);
        r.assertLogContains("Hello from HTTP", b);

        Files.write(path, "echo 'Hello from HTTP 2'".getBytes());
        def = new CpsHttpFlowDefinition(url);
        def.setSetAcceptHeader("");
        def.setSetKeyHeader("");
        def.setSetValueHeader("");
        def.setRetryCount(3);
        def.setCachingConfiguration(new CachingConfiguration(5, ""));

        p.setDefinition(def);
        b = r.buildAndAssertSuccess(p);
        r.assertLogContains("Fetching pipeline from " + url, b);
        r.assertLogContains("Fetching from cache", b);
        r.assertLogContains("Hello from HTTP", b);
    }

    @Test
    void testFetchCachedExcludedCase(JenkinsRule r) throws Exception {
        ParameterDefinition paramDef = new StringParameterDefinition("CUSTOM_BUILD_PARAM", "This-IsMyValue", "");
        ParametersDefinitionProperty paramsDef = new ParametersDefinitionProperty(paramDef);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.addProperty(paramsDef);

        Path path = Paths.get(r.jenkins.getRootPath().getRemote(), "userContent", "testFetchCachedExcludedCase.groovy");
        Files.write(path, "echo 'Hello from HTTP'".getBytes());
        String url = r.jenkins.getRootUrl() + "userContent/testFetchCachedExcludedCase.groovy";

        CpsHttpFlowDefinition def = new CpsHttpFlowDefinition(url);
        def.setSetAcceptHeader("");
        def.setSetKeyHeader("");
        def.setSetValueHeader("");
        def.setRetryCount(3);
        def.setCachingConfiguration(new CachingConfiguration(5, "CUSTOM_BUILD_PARAM=*Is*"));

        p.setDefinition(def);
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("Fetching pipeline from " + url, b);
        r.assertLogContains("Caching excluded from environment variables", b);
        r.assertLogContains("Hello from HTTP", b);
    }

    @Test
    void testFetchCachedNotExcludedCase(JenkinsRule r) throws Exception {
        ParameterDefinition paramDef = new StringParameterDefinition("CUSTOM_BUILD_PARAM", "This-IsMyValue", "");
        ParametersDefinitionProperty paramsDef = new ParametersDefinitionProperty(paramDef);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.addProperty(paramsDef);

        Path path =
                Paths.get(r.jenkins.getRootPath().getRemote(), "userContent", "testFetchCachedNotExcludedCase.groovy");
        Files.write(path, "echo 'Hello from HTTP'".getBytes());
        String url = r.jenkins.getRootUrl() + "userContent/testFetchCachedNotExcludedCase.groovy";

        CpsHttpFlowDefinition def = new CpsHttpFlowDefinition(url);
        def.setSetAcceptHeader("");
        def.setSetKeyHeader("");
        def.setSetValueHeader("");
        def.setRetryCount(3);
        def.setCachingConfiguration(new CachingConfiguration(5, "CUSTOM_BUILD_PARAM=This-Is-Not*"));

        p.setDefinition(def);
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("Fetching pipeline from " + url, b);
        r.assertLogContains("Cache miss. Actually fetching from HTTP", b);
        r.assertLogContains("Hello from HTTP", b);
    }

    @Test
    void testFetchAgainWithExpiredCaching(JenkinsRule r) throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        Path path = Paths.get(
                r.jenkins.getRootPath().getRemote(), "userContent", "testFetchAgainWithExpiredCaching.groovy");
        Files.write(path, "echo 'Hello from HTTP'".getBytes());
        String url = r.jenkins.getRootUrl() + "userContent/testFetchAgainWithExpiredCaching.groovy";

        CpsHttpFlowDefinition def = new CpsHttpFlowDefinition(url);
        def.setSetAcceptHeader("");
        def.setSetKeyHeader("");
        def.setSetValueHeader("");
        def.setRetryCount(3);
        def.setCachingConfiguration(new CachingConfiguration(0, ""));

        p.setDefinition(def);
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("Fetching pipeline from " + url, b);
        r.assertLogContains("Cache miss. Actually fetching from HTTP", b);
        r.assertLogContains("Hello from HTTP", b);

        Files.write(path, "echo 'Hello from HTTP 2'".getBytes());
        def = new CpsHttpFlowDefinition(url);
        def.setSetAcceptHeader("");
        def.setSetKeyHeader("");
        def.setSetValueHeader("");
        def.setRetryCount(3);
        def.setCachingConfiguration(new CachingConfiguration(0, ""));

        p.setDefinition(def);
        b = r.buildAndAssertSuccess(p);
        r.assertLogContains("Fetching pipeline from " + url, b);
        r.assertLogContains("Cache is expired. Clearing", b);
        r.assertLogContains("Hello from HTTP 2", b);
    }

    @Test
    void testRunJenkinsHomePageAsPipeline(JenkinsRule r) throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        CpsHttpFlowDefinition def = new CpsHttpFlowDefinition(r.jenkins.getRootUrl());
        def.setSetAcceptHeader("");
        def.setSetKeyHeader("");
        def.setSetValueHeader("");
        def.setRetryCount(3);
        def.setCachingConfiguration(null);

        p.setDefinition(def);
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogContains("unexpected token", b);
    }

    @Test
    void testRetryCount(JenkinsRule r) throws Exception {
        String scriptUrl = "https://bad-website-jenkins-test.com/Jenkinsfile";
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        CpsHttpFlowDefinition def = new CpsHttpFlowDefinition(scriptUrl);
        def.setSetAcceptHeader("");
        def.setSetKeyHeader("");
        def.setSetValueHeader("");
        def.setRetryCount(3);
        def.setCachingConfiguration(null);

        p.setDefinition(def);
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        assertEquals(3, StringUtils.countMatches(b.getLog(), "Retrying get pipeline"));
    }
}
