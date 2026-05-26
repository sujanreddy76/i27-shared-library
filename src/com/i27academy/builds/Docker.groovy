package com.i27academy.builds;

class Docker {
    //Write all the methods here
    def jenkins
    Docker(jenkins) {
        this.jenkins = jenkins
    }

    //Application Build
    def buildApp(appName) {
            jenkins.echo "Building the ${appName} application"

            jenkins.sh "mvn clean package -DskipTests=true"

            jenkins.archiveArtifacts artifacts: 'target/*.jar'
    }


}
