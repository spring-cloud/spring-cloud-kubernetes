#!/usr/bin/groovy
node{

  checkout scm

  def pom = readMavenPom file: 'pom.xml'

  def githubOrganisation = 'fabric8io'
  def projectName = 'spring-cloud-kubernetes'
  def dockerOrganisation = 'fabric8'
  def artifactIdToWatchInCentral = 'spring-cloud-starter-kubernetes'
  def artifactIdToWatchInCentralExtension = 'jar'
  def imagesToPromoteToDockerHub = []

  kubernetes.pod('buildpod').withImage('fabric8/maven-builder:1.1')
  .withPrivileged(true)
  .withSecret('jenkins-maven-settings','/root/.m2')
  .withSecret('jenkins-ssh-config','/root/.ssh')
  .withSecret('jenkins-git-ssh','/root/.ssh-git')
  .withSecret('jenkins-release-gpg','/root/.gnupg')
  .inside {

    sh 'chmod 600 /root/.ssh-git/ssh-key'
    sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
    sh 'chmod 700 /root/.ssh-git'
    sh 'chmod 600 /root/.gnupg/pubring.gpg'
    sh 'chmod 600 /root/.gnupg/secring.gpg'
    sh 'chmod 600 /root/.gnupg/trustdb.gpg'
    sh 'chmod 700 /root/.gnupg'

    sh "git remote set-url origin git@github.com:${githubOrganisation}/${projectName}.git"

    def stagedProject = stageProject{
      project = githubOrganisation+"/"+projectName
      useGitTagForNextVersion = true
    }

    String pullRequestId = release {
      projectStagingDetails = stagedProject
      project = githubOrganisation+"/"+projectName
      useGitTagForNextVersion = true
      helmPush = false
    }

    if (pullRequestId != null && pullRequestId.size() > 0){
      waitUntilPullRequestMerged{
        name = githubOrganisation+"/"+projectName
        prId = pullRequestId
      }
    }

    // lets check for spring-cloud-starter-kubernetes jar to detect when sonartype -> central sync has happened
    waitUntilArtifactSyncedWithCentral {
      repo = 'http://central.maven.org/maven2/'
      groupId = pom.groupId
      artifactId = artifactIdToWatchInCentral
      version = stagedProject[1]
      ext = artifactIdToWatchInCentralExtension
    }
  }
}
