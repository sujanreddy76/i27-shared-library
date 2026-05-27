import com.i27academy.builds.Docker;
import com.i27academy.k8s.K8s;

def call(Map pipelineParams) {

    //An instance of the class Docker is created
    Docker docker = new Docker(this)
     //An instance of the class K8s is created
    K8s k8s = new K8s(this)
    pipeline {
        agent {
            label 'k8s-slave'
        }
        //{ choice(name: 'CHOICES', choices: ['one', 'two', 'three'], description: '') }
        parameters {        
            choice(name: 'dockerPush',
                choices: 'no\nyes',
                description: 'Do you want to trigger the application build, docker build and docker Push:?'
            )        
            choice(name: 'deployToDev',
                choices: 'no\nyes',
                description: 'Do you want to Deploy the application to dev environment:?'
            )           
            choice(name: 'deployToTest',
                choices: 'no\nyes',
                description: 'Do you want to Deploy the application to test environment:?'
            )
            choice(name: 'deployToStage',
                choices: 'no\nyes',
                description: 'Do you want to Deploy the application to stage environment:?'
            )           
            choice(name: 'deployToProd',
                choices: 'no\nyes',
                description: 'Do you want to Deploy the application to prod environment:?'
            )   

        }

        environment {
            APPLICATION_NAME = "${pipelineParams.appName}"
            DOCKER_HUB = "docker.io/sujanreddy76"
            DOCKER_CREDS = credentials('dockerhub_creds') //username and password

            //Below are kubernetes details
            DEV_CLUSTER_NAME = "i27-cluster"
            DEV_CLUSTER_ZONE = "us-central1-a"
            DEV_PROJECT_ID = "project-d124de7c-f08a-4d92-977"

            //k8s file names env variables
            K8S_DEV_FILE = "k8s_dev.yaml"
            K8S_TST_FILE = "k8s_tst.yaml"
            K8S_STG_FILE = "k8s_stg.yaml"
            K8S_PRD_FILE = "k8s_prd.yaml"
            
            //Namespace definition
            DEV_NAMESPACE = "cart-dev-ns"
            TST_NAMESPACE = "cart-tst-ns"
            STG_NAMESPACE = "cart-stg-ns"
            PRD_NAMESPACE = "cart-prd-ns"

        }
        stages {
            stage('Docker Build and Push') {
                when {
                    anyOf {
                        expression {
                            params.dockerPush == 'yes'
                        }
                    }
                }
                steps {
                    script {
                        dockerBuildAndPush().call()
                    }
                }
            }
            stage('Deploy to Dev Env') {
            when {
                    expression {
                        params.deployToDev == 'yes'
                    }
                }            
                steps {
                    script {

                        //This will get the docker image name and store it in docker_image variable
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"

                        //This will login to the kubernetes cluster
                        k8s.auth_login("${env.DEV_CLUSTER_NAME}", "${env.DEV_CLUSTER_ZONE}", "${env.DEV_PROJECT_ID}")

                        //This will validate the image is available in DockerHub if it is not available it will build and push the image into DockerHub
                        imageValidation().call()

                        //deploying to kubernetes cluster in cart-dev-ns namespace
                        //(fileName, docker_image, namespace)
                        k8s.k8sDeploy("${env.K8S_DEV_FILE}", docker_image, "${env.DEV_NAMESPACE}")
                    }  
                }
                // a mail should trigger based on the status
                // jenkins url should be sent as an a email
            }
            stage('Deploy to Test Env') {
            when {
                    expression {
                        params.deployToTest == 'yes'
                    }
                }             
                steps {
                    script {
                        imageValidation().call()
                        //dockerDeploy('tst', '6761', '8761').call()   
                        dockerDeploy('tst', "${env.TST_HOST_PORT}", "${env.CONT_PORT}").call()    
                    }
                }
            } 
            stage('Deploy to Stage Env') {
            when {
                allOf {
                    anyOf {
                        expression {
                            params.deployToStage == 'yes'
                        }                    
                    }
                    anyOf {
                        branch 'release/*'
                        tag pattern: "v\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}", comparator: "REGEXP" // v1.2.3 is the correct one, v123 is the wrong one
                    }                
                }

            }             
                steps {
                    script {
                        imageValidation().call()
                        //dockerDeploy('stg', '7761', '8761').call()   
                        dockerDeploy('stg', "${env.STG_HOST_PORT}", "${env.CONT_PORT}").call()    

                    }
                }
            } 
            stage('Deploy to Prod Env') {
            when {
                allOf {
                    anyOf {
                        expression {
                            params.deployToProd == 'yes'
                        }                    
                    }
                    anyOf {
                        tag pattern: "v\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}", comparator: "REGEXP" // v1.2.3 is the correct one, v123 is the wrong one
                    }                
                }
                }             
                steps {
                    script {
                        timeout(time: 300, unit: 'SECONDS') { //SECONDS, MINUTES, HOURS
                            input message: "Deploying ${env.APPLICATION_NAME} to Production??", ok: 'yes', submitter: 'sujanSRE,sivaTechlead'
                        }
                       // dockerDeploy('prod', '8761', '8761').call()  
                        dockerDeploy('prod', "${env.PROD_HOST_PORT}", "${env.CONT_PORT}").call()    

                    }
                }
            }         

        }
        // post {
        //     // Only run if the pipeline or stage has success status
        //     success {
        //         script {
        //             // Send email notification with custom message
        //             def subject = "Pipeline ${currentBuild.currentResult}: Job: ${env.JOB_NAME}, Build Number: ${env.BUILD_NUMBER}"
        //             def body = "Build Number: ${env.BUILD_NUMBER} \n" +
        //                         "status: ${currentBuild.currentResult} \n" +
        //                         "Job URL: ${env.BUILD_URL}"
        //             //Send email notification using method
        //             sendEmailNotification('jaya.sujan.kumar@gmail.com', subject, body)                   
        //         }
            
        //     }
        //     // Only run if the pipeline or stage has failure status
        //     failure {
        //         script {
        //             // Send email notification with custom message
        //             def subject = "Pipeline ${currentBuild.currentResult}: Job: ${env.JOB_NAME}, Build Number: ${env.BUILD_NUMBER}"
        //             def body = "Build Number: ${env.BUILD_NUMBER} \n" +
        //                         "status: ${currentBuild.currentResult} \n" +
        //                         "Job URL: ${env.BUILD_URL}"
        //             //Send email notification using method
        //             sendEmailNotification('jaya.sujan.kumar@gmail.com', subject, body)                   
        //         }
        //     }
        // }        

    }
    
}


//This Jenkinsfile is for Clothing Deployment



// imageValidation
def imageValidation() {
    return {
        println("******** Attempting to pull the Docker Images **********")
        try {
            sh "docker pull ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
            println("************ Image is Pulled Successfully ************")
        }
        catch(Exception e) {
            println("******* OOPS, The docker image with this tag is not available in the repo, So Building or creating the Image and pushing into DockerHub **********")
            dockerBuildAndPush().call()
        }
    }
}


// Method for docker build and push
def dockerBuildAndPush(){
    return {
        echo "**************** Building Docker Image *******************"
        sh "docker build --no-cache -t ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT} ./.cicd/Dockerfile"
        echo "**************** Login to docker registry *******************"
        sh "docker login -u ${DOCKER_CREDS_USR} -p ${DOCKER_CREDS_PSW}"
        echo "**************** Push Image to docker registry *******************"
        sh "docker push ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"        

    }
}

// Method for Docker Deployment as containers in different environments
def dockerDeploy(envDeploy, hostPort, contPort){
    return {
        echo "****************** Deploy to $envDeploy Env ******************"
        withCredentials([usernamePassword(credentialsId: 'john_docker_vm_password', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
            // some block
            //We will communicate to the docker-server vm
            script {
                try {
                    //If the container with (eureka-dev is slready running the below try code will execute)
                    //Stop the container
                    sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no '$USERNAME'@$dev_ip \"docker stop ${env.APPLICATION_NAME}-$envDeploy \""
                    //Remove the container
                    sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no '$USERNAME'@$dev_ip \"docker rm ${env.APPLICATION_NAME}-$envDeploy \""
                }
                catch(err) {
                    //The below code will execute if it is the 1st time the container is running with eureka-dev name
                    echo "Error caught: $err"
                }
                //The below code will execute if try is success
                //Command/syntax to use sshpass
                // sshpass -p !4u2tryhack ssh -o StrictHostKeyChecking=no username@host.example.com
                sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no '$USERNAME'@$dev_ip \"docker container run -dit -p $hostPort:$contPort --name ${env.APPLICATION_NAME}-$envDeploy ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}\""
            }
        }

    }
}

//For eureka lets use the below port numbers
//Container port will be 8761 only, The Host port will change based on the environment(eg: dev, test..etc)
//dev: HostPort = 5761
//tst: HostPort = 6761
//stg: HostPort = 7761
//prod: HostPort = 8761

//Method to send email notification
def sendEmailNotification(String recipient, String subject, String body) {
    mail ( //mail() is available in jenkins
       to: recipient,
       subject: subject,
       body: body
    )
}

