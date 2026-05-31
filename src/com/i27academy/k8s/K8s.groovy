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

    //Method for helm deployments
    def k8sHelmChartDeploy(appName, env, helmChartPath, imageTag, namespace) {
        jenkins.echo "****** Entering into kubernetes Helm Deployment Method *******"
        jenkins.sh "helm version"
        jenkins.echo "********** Installing the Chart ****************"
           // helm install <release-name> <chart-path> -f <values-file> --set image.tag=<tag> -n <namespace>
           // helm install eureka-dev-chart ${WORKSPACE}/i27-shared-library/chart -f .cicd/helm_values/values_${env}.yaml --set image.tag=${imageTag} -n cart-dev-ns
        jenkins.sh "helm install ${appName}-${env}-chart ${helmChartPath} -f .cicd/helm_values/values_${env}.yaml --set image.tag=${imageTag} -n ${namespace}"

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