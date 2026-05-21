import com.i27academy.builds.Docker;

def call(Map pipelineParams) {

    //An instance of the class Docker is created
    Docker docker = new Docker(this)
    pipeline {
        agent {
            label 'k8s-slave'
        }
        //{ choice(name: 'CHOICES', choices: ['one', 'two', 'three'], description: '') }
        parameters {
            choice(name: 'scanOnly',
                choices: 'no\nyes',
                description: 'Do you want to Scan the application:?'
            )
            choice(name: 'buildOnly',
                choices: 'no\nyes',
                description: 'Do you want to Build the application:?'
            )        
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
        // Tools configured in jenkins-master
        tools {
            maven 'Maven-3.8.8'
            jdk 'JDK-17'
        }
        environment {
            APPLICATION_NAME = "${pipelineParams.appName}"
            //Below are the HostPorts
            DEV_HOST_PORT = "${pipelineParams.devHostPort}"
            TST_HOST_PORT = "${pipelineParams.tstHostPort}"
            STG_HOST_PORT = "${pipelineParams.stgHostPort}"
            PROD_HOST_PORT = "${pipelineParams.prodHostPort}"
            //Below is the ContainerPort
            CONT_PORT = "${pipelineParams.contPort}"
            POM_VERSION = readMavenPom().getVersion()
            POM_PACKAGING = readMavenPom().getPackaging()
            DOCKER_HUB = "docker.io/sujanreddy76"
            DOCKER_CREDS = credentials('dockerhub_creds') //username and password
        }
        stages {
            stage('Build') {
                when {
                    anyOf {
                        expression {
                            params.dockerPush == 'yes'
                            params.buildOnly == 'yes'
                        }
                    }
                }
                //This is where build for eureka application happens
                steps {
                    script {
                        docker.buildApp("${env.APPLICATION_NAME}")
                    }
                    // echo "Building ${APPLICATION_NAME} Application"
                    // sh 'mvn clean package -DskipTests=true'
                    // //mvn clean package -DskipTests=true
                    // //mvn clean package -Dmaven.test.skip=true
                    // archive 'target/*.jar'
                }
            }
            // stage('Unit Tests') {
            //     steps {
            //         echo "************Performing Unit tests for ${APPLICATION_NAME} Application************"
            //         sh 'mvn test'
            //     }
            // }

            stage('SonarQube'){
                when {
                    anyOf {
                        expression {
                            params.dockerPush == 'yes'
                            params.buildOnly == 'yes'                        
                            params.scanOnly == 'yes'
                        }
                    }
                }
                steps {
                    // Code quality needs to be implemented in this stage
                    // Before we execute or write the code, make sure sonarqube-scanner plugin is installed
                    // Sonar details are configured in the manage jenkins > system 
                    echo "***********************Starting Sonar Scans with Quality Gates*********************"
                    withSonarQubeEnv('SonarQube') {//SonarQube is the name we configured in Manage Jenkins > system > SonarQube servers, It should match exactly
                        sh """
                            mvn sonar:sonar \
                                -Dsonar.projectKey=i27-eureka \
                                -Dsonar.host.url=http://34.55.4.214:9000 \
                                -Dsonar.login=sqa_e077ec93f2d4816fb8ec6703a8b8b07857b01d84
                        """    
                    }
                    timeout (time: 2, unit: 'MINUTES'){ //NANOSECONDS, SECONDS, MINUTES, HOURS, DAYS
                            waitForQualityGate abortPipeline: true
                    }
                }
            }
            // stage('BuildFormat') {
            //     steps{
            //         script{ 
            //             //Existing: i27-eureka-0.0.1-SNAPSHOT.jar
            //             //Destination: i27-eureka-CurrentBuildNumber-branchName.packaging
            //           sh """
            //             echo "Testing Jar Source: i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING}"
            //             echo "Testing Jar Destination format: i27-${env.APPLICATION_NAME}-${currentBuild.number}-${BRANCH_NAME}.${env.POM_PACKAGING}"
            //           """

            //         }
            //     }
            // }
            stage('Docker Build Push') {
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
                        imageValidation().call()
                        dockerDeploy('dev', "${env.DEV_HOST_PORT}", "${env.CONT_PORT}").call()
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
                            input message: "Deploying ${env.APPLICATION_NAME} to Production??", ok: 'yes', submitter: 'sivasre,sujanacademy'
                        }
                       // dockerDeploy('prod', '8761', '8761').call()  
                        dockerDeploy('prod', "${env.PROD_HOST_PORT}", "${env.CONT_PORT}").call()    

                    }
                }
            }         

        }
        post {
            // Only run if the pipeline or stage has success status
            success {
                script {
                    // Send email notification with custom message
                    def subject = "Pipeline ${currentBuild.currentResult}: Job: ${env.JOB_NAME}, Build Number: ${env.BUILD_NUMBER}"
                    def body = "Build Number: ${env.BUILD_NUMBER} \n" +
                                "status: ${currentBuild.currentResult} \n" +
                                "Job URL: ${env.BUILD_URL}"
                    //Send email notification using method
                    sendEmailNotification('jaya.sujan.kumar@gmail.com', subject, body)                   
                }
            
            }
            // Only run if the pipeline or stage has failure status
            failure {
                script {
                    // Send email notification with custom message
                    def subject = "Pipeline ${currentBuild.currentResult}: Job: ${env.JOB_NAME}, Build Number: ${env.BUILD_NUMBER}"
                    def body = "Build Number: ${env.BUILD_NUMBER} \n" +
                                "status: ${currentBuild.currentResult} \n" +
                                "Job URL: ${env.BUILD_URL}"
                    //Send email notification using method
                    sendEmailNotification('jaya.sujan.kumar@gmail.com', subject, body)                   
                }
            }
        }        

    }
    
}


//This Jenkinsfile is for Eureka Deployment



// imageValidation
def imageValidation() {
    return {
        println("******** Attempting to pull the Docker Images **********")
        try {
            sh "docker pull ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
            println("************ Image is Pulled Successfully ************")
        }
        catch(Exception e) {
            println("******* OOPS, The docker image with this tag is not available in the repo, So Building the Application and creating the Image**********")
            buildApp().call()
            dockerBuildAndPush().call()
        }
    }
}

//Building the Application
def buildApp(){
    return {
                echo "Building ${APPLICATION_NAME} Application"
                sh 'mvn clean package -DskipTests=true'
                archive 'target/*.jar'
    }
}

// Method for docker build and push
def dockerBuildAndPush(){
    return {
        echo "**************** Building Docker Image *******************"
        sh "cp ${WORKSPACE}/target/i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} ./.cicd"
        sh "docker build --no-cache --build-arg JAR_SOURCE=i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} -t ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT} ./.cicd/"
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
        withCredentials([usernamePassword(credentialsId: 'john_docker_vm_passwd', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
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

