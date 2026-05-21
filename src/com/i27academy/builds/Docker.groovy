package com.i27academy.builds;

class Docker {
    //Write all the methods here
    def jenkins
    Docker(jenkins) {
        this.jenkins = jenkins
    }

    //Application Build
    def buildApp(appName) {
        jenkins.sh """
            echo "Building the $appName application"
            sh 'mvn clean package -DskipTests=true'
            archive 'target/*.jar'
        """
    }


}
