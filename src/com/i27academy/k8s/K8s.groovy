package com.i27academy.k8s;

class K8s{
    def jenkins
    K8s(jenkins){
        this.jenkins = jenkins
    }

    //Method to authenticate to kubernetes cluster
    def auth_login(clusterName, zone, projectID){
        jenkins.echo "****** Entering into kubernetes authentication/login method *******"
        jenkins.sh "gcloud compute instances list"
        jenkins.echo "****** Create the kube Config file for the environment *******"
        jenkins.sh "gcloud container clusters get-credentials ${clusterName} --zone ${zone} --project ${projectID}"
        jenkins.sh "kubectl get nodes"
    }

    //Method to deploy to the application
    def k8sDeploy(fileName, docker_image, namespace){
        jenkins.echo "****** Entering into kubernetes Deployment Method *******"
        jenkins.sh "sed -i 's|DIT|${docker_image}|g' ./.cicd/${fileName}"
 }


    // Method for Helm deployments
    def k8sHelmChartDeploy(appName, env, helmChartPath, imageTag, namespace) {

        jenkins.echo "****** Entering into kubernetes Helm Deployment Method *******"
        jenkins.sh "helm version"

        jenkins.echo "********Lets Verify Helm Chart exists with that name******"

        def chartExists = jenkins.sh(
            script: "helm list -n ${namespace} -q | grep -x '${appName}-${env}-chart'",
            returnStatus: true
        )

        if (chartExists == 0) {
            jenkins.echo "This Chart Exists"
            jenkins.echo "Upgrading the Chart"

            jenkins.sh """
                helm upgrade ${appName}-${env}-chart ${helmChartPath} \
                -f .cicd/helm_values/values_${env}.yaml \
                --set image.tag=${imageTag} \
                -n ${namespace}
            """
        } else {
            jenkins.echo "This Chart Does Not Exist"
            jenkins.echo "Installing the Chart"

            jenkins.sh """
                helm install ${appName}-${env}-chart ${helmChartPath} \
                -f .cicd/helm_values/values_${env}.yaml \
                --set image.tag=${imageTag} \
                -n ${namespace}
            """
        }
    }
    
    //Clone the Shared Library
    def gitClone(){
        jenkins.echo "********** Cloning the shared Library **********"
        jenkins.sh "git clone -b main https://github.com/sujanreddy76/i27-shared-library.git"
        jenkins.echo "********** Listing the files in the workspace ***********"
        jenkins.sh "ls -la"
        jenkins.echo "******** Listing the files in the shared library **********"
        jenkins.sh "ls -la i27-shared-library"
    }

}