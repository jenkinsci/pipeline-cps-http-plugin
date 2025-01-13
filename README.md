# HTTP Pipeline Plugin

[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/pipeline-cps-http-plugin/main)](https://ci.jenkins.io/job/plugins/job/pipeline-cps-http-plugin/)
[![Coverage](https://ci.jenkins.io/job/Plugins/job/pipeline-cps-http-plugin/job/main/badge/icon?status=${instructionCoverage}&subject=coverage&color=${colorInstructionCoverage})](https://ci.jenkins.io/job/Plugins/job/pipeline-cps-http-plugin/job/main)
[![LOC](https://ci.jenkins.io/job/Plugins/job/pipeline-cps-http-plugin/job/main/badge/icon?job=test&status=${lineOfCode}&subject=line%20of%20code&color=blue)](https://ci.jenkins.io/job/Plugins/job/pipeline-cps-http-plugin/job/main)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/oss-symbols-api.svg)](https://plugins.jenkins.io/oss-symbols-api/)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/pipeline-cps-http-plugin.svg?label=changelog)](https://github.com/jenkinsci/pipeline-cps-http-plugin/releases/latest)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/pipeline-cps-http-plugin.svg)](https://github.com/jenkinsci/pipeline-cps-http-plugin/graphs/contributors)

The current official plugin [workflow-cps](https://github.com/jenkinsci/workflow-cps-plugin/) does provide a way to retrieve a Jenkinsfile through a SCM, such as Git. The goal of this plugin is to provide another way to retrieve Jenkinsfiles via HTTP calls.

This is a way to separate to concerns : source code (SCM) and built artifacts (binaries). Built artifacts are immutable, tagged and often stored on a different kind of infrastructure. Since pipelines can be used to make production loads, it makes sense to host the libraries on a server with a production-level SLA for example. You can also make sure that your artefact repository is close to your pipelines and share the same SLA. Having your Jenkins and your artefact repository close limitsr latency and limits network issues.

## Context

The HTTP Pipeline Plugin was implemented to retrieve Jenkinsfiles through HTTP (or HTTPs) instead of a SCM.

## How to use the plugin

1. Create a pipeline job
2. Select the `Pipeline script from HTTP` option in the `Pipeline` section

![Example](example.png)

### Use with the GitHub API

URL: https://api.github.com/repos/[your_org]/[your_repo]/contents/[path_in_repo]?ref=master
Accept Header: application/vnd.github.VERSION.raw

## Contributing

You can contribute to this plugin by retrieving the source and following the [official Jenkins plugin tutorial](https://wiki.jenkins.io/display/JENKINS/Plugin+tutorial) to install, run, test and package it.

## Legal

This project is licensed under the terms of the [MIT license](LICENSE).
