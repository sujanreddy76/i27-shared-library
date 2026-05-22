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

}