// Jenkins Display Name
currentBuild.displayName = "$ENVIRONMENT-$MICROSERVICES-$TAG-#"+currentBuild.number
// Setting value for kubeconfig.  

def KUBE_CONFIG = env.ENVIRONMENT

def AWS_ENV = env.ENVIRONMENT

def BRANCH = env.ENVIRONMENT

REGION = getRegion(ENVIRONMENT)

def getRegion(env) {
  if (env == 'dr') {
    return 'us-west-2'
  }
  else {
    return 'us-east-1'
  }
}

if (env.ENVIRONMENT =~ "prod[a-z]*[A-B]"){
  BRANCH = "master"
} else if (env.ENVIRONMENT == "dr"){
  BRANCH = "master"
} else if (env.ENVIRONMENT =~ "accp[a-z]*[A-Z]"){
  BRANCH = "accp"  
} else if (env.ENVIRONMENT =~ "intg[a-z]*[A-Z]"){
  BRANCH = "intg"
} else if (env.ENVIRONMENT =~ "dev[a-z]*[A-Z]"){
  BRANCH = "dev"
}
else {
    BRANCH = env.ENVIRONMENT
}

if (env.ENVIRONMENT =~ "prod[a-z]*[A-Z]"){
  KUBE_CONFIG = "prod"
} else if (env.ENVIRONMENT =~ "accp[a-z]*[A-Z]"){
  KUBE_CONFIG = "accp"  
} else if (env.ENVIRONMENT =~ "intg[a-z]*[A-Z]"){
  KUBE_CONFIG = "intg"
} else if (env.ENVIRONMENT =~ "dev[a-z]*[A-Z]"){
  KUBE_CONFIG = "dev"
} else {
  KUBE_CONFIG = env.ENVIRONMENT
}

if (env.ENVIRONMENT =~ "prod[a-z]*[A-Z]"){
  AWS_ENV = "prod"
} else if (env.ENVIRONMENT == "dr"){
  AWS_ENV = "prod"
} else if (env.ENVIRONMENT =~ "accp[a-z]*[A-Z]"){
  AWS_ENV = "accp"  
} else if (env.ENVIRONMENT =~ "intg[a-z]*[A-Z]"){
  AWS_ENV = "intg"
} else if (env.ENVIRONMENT =~ "dev[a-z]*[A-Z]"){
  AWS_ENV = "dev"
}
else {
    AWS_ENV = env.ENVIRONMENT
}

if (env.ENVIRONMENT =~ "prod[a-z]*[A-B]"){
  CRED_ID = "sa_deploy_prod"
} else if (env.ENVIRONMENT == "dr"){
  CRED_ID = "sa_deploy_prod"
} else if (env.ENVIRONMENT == "accp" || env.ENVIRONMENT =~ "accp[a-z]*[A-Z]"){
  CRED_ID = "sa_deploy_accp"  
} else if (env.ENVIRONMENT == "intg" || env.ENVIRONMENT =~ "intg[a-z]*[A-Z]"){
  CRED_ID = "sa_deploy_intg"
} else if (env.ENVIRONMENT == "dev" || env.ENVIRONMENT =~ "dev[a-z]*[A-Z]"){
CRED_ID = "sa_deploy_dev"
}
else {
    CRED_ID = "sa_deploy_null"
}

// Define gated environments
CONTROLLED_ENVIRONMENTS = readJSON text: CONTROLLED_ENVIRONMENTS

//Approval gate
stage('Deploy approval'){
  if (CONTROLLED_ENVIRONMENTS.contains(ENVIRONMENT)){
                emailext attachLog: false,
                                body: "Deployment ${JOB_BASE_NAME} to Controlled Environment ${ENVIRONMENT} requires approval. ${BUILD_URL}input",
                                                replyTo: 'devops@venerableannuity.com',
                                                subject: "Approval required ${JOB_BASE_NAME}",
                                                to: APPROVER_LIST
                def feedback = input submitter: APPROVER_LIST, message: "This will deploy to the controlled environment $ENVIRONMENT"
  } 
  else if (LOWER_CONTROLLED_ENVIRONMENTS.contains(ENVIRONMENT)){
        emailext attachLog: false,
                                                body: "Deployment ${JOB_BASE_NAME} requires approval. ${BUILD_URL}input",
                                                replyTo: 'devops@venerableannuity.com',
                                                subject: "Approval required ${JOB_BASE_NAME}",
                                                to: LOWER_ENV_APPROVER_LIST
                    def feedback = input submitter: LOWER_ENV_APPROVER_LIST, message: "This will deploy to the controlled environment $ENVIRONMENT"
  }               
  else{
                                echo "Deployment to ${ENVIRONMENT} does not require approval."
  }
}

pipeline {
  agent {
        node {
         label "$NODE_LABEL"
        }
      }

  stages {
    stage('Retreiving Helm Charts') {  
        steps {
          git branch: '${BRANCH}',
               credentialsId: 'f06cf8c1-1b3c-44e0-bc68-35e0f8a1a6fa',
               url: 'https://github.tools.vaapps.net/EKS/ven-helm-charts.git'
        }
    }
      
    stage('Deploying Lifecad Services') {
      when {
        anyOf { 
          environment name: 'MICROSERVICES', value: 'lifecad-data-services'
          environment name: 'MICROSERVICES', value: 'lifecad-nonfinancialtran-services'
          environment name: 'MICROSERVICES', value: 'lifecad-financialtran-services'
          environment name: 'MICROSERVICES', value: 'lifecad-agent-services'
          environment name: 'MICROSERVICES', value: 'lifecad-financial-services'
          environment name: 'MICROSERVICES', value: 'lifecad-correspondence-services'
          environment name: 'MICROSERVICES', value: 'lifecad-contractentity-services'
          environment name: 'MICROSERVICES', value: 'lifecad-contractinfo-services'
          environment name: 'MICROSERVICES', value: 'lifecad-batchcycle-services'
        }
      } 
        steps {
          withCredentials([
            [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: CRED_ID, secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
            ]) {
            sh 'aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID'
            sh 'aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY'          
            dir('lifecad-services') {
            sh "helm upgrade --timeout 6m30s --install --kube-context ${KUBE_CONFIG} --atomic -n lifecad-services-${ENVIRONMENT.toLowerCase()} 
                                                --set tag=${TAG},environment=${ENVIRONMENT},active_profile=${ACTIVE_PROFILE},rhel_version=${RHEL_VERSION},region=${REGION} ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} 
                                                -f ${MICROSERVICES}-values.yaml ."
            sh "kubectl config use-context ${KUBE_CONFIG}"
            sh "kubectl rollout restart deploy ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} -n lifecad-services-${ENVIRONMENT.toLowerCase()}"     
            }
          }  
        }
    }
    stage('Deploying Garwin Services') {
      when {
        anyOf { 
          environment name: 'MICROSERVICES', value: 'garwin-validation-services'
          environment name: 'MICROSERVICES', value: 'garwin-data-services'
          environment name: 'MICROSERVICES', value: 'garwin-tranprocess-services'
          environment name: 'MICROSERVICES', value: 'garwin-goodorder-service'
          environment name: 'MICROSERVICES', value: 'garwin-contract-services'
          environment name: 'MICROSERVICES', value: 'garwin-quote-services'          
          environment name: 'MICROSERVICES', value: 'garwin-agent-services'
          environment name: 'MICROSERVICES', value: 'garwin-financial-services' 
          environment name: 'MICROSERVICES', value: 'garwin-contractinfo-services'
          environment name: 'MICROSERVICES', value: 'garwin-contractentity-services'
          environment name: 'MICROSERVICES', value: 'garwin-fin-services'
          environment name: 'MICROSERVICES', value: 'garwin-maintenance-services'
          environment name: 'MICROSERVICES', value: 'garwin-report-services'
        }
      } 
        steps {
          withCredentials([
            [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: CRED_ID, secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
            ]) {
            sh 'aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID'
            sh 'aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY'
            dir('garwin-services') {
            sh "helm upgrade --timeout 6m30s --debug --install --kube-context ${KUBE_CONFIG} --atomic -n garwin-services-${ENVIRONMENT.toLowerCase()} --set tag=${TAG},environment=${ENVIRONMENT},active_profile=${ACTIVE_PROFILE},rhel_version=${RHEL_VERSION},region=${REGION} ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} -f ${MICROSERVICES}-values.yaml ."
            sh "kubectl config use-context ${KUBE_CONFIG}"
            sh "kubectl rollout restart deploy ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} -n garwin-services-${ENVIRONMENT.toLowerCase()}"       
            }
          }    
        }          
    }
    stage('Deploying Garwin Apps') {
      when {
        anyOf { 
          environment name: 'MICROSERVICES', value: 'garwin-jmt-app'
          environment name: 'MICROSERVICES', value: 'basdashboard'
          environment name: 'MICROSERVICES', value: 'garwin-annuity-admin-app'

        }
      } 
        steps {
          withCredentials([
            [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: CRED_ID, secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
            ]) {
            sh 'aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID'
            sh 'aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY'
            dir('garwin-int-apps') {
            sh "helm upgrade --timeout 6m30s --install --kube-context ${KUBE_CONFIG} --atomic -n garwin-int-apps-${ENVIRONMENT.toLowerCase()} --set tag=${TAG},environment=${ENVIRONMENT},rhel_version=${RHEL_VERSION},region=${REGION} ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} -f ${MICROSERVICES}-values.yaml ."
            sh "kubectl config use-context ${KUBE_CONFIG}"
            sh "kubectl rollout restart deploy ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} -n garwin-int-apps-${ENVIRONMENT.toLowerCase()}"       
            }
          }
        }          
    }
    stage('Deploying generic internal service') {
      when {
        anyOf { 
          environment name: 'MICROSERVICES', value: 'bas-rits-app'
          environment name: 'MICROSERVICES', value: 'nscc-configurator-app'
        }
      } 
        steps {
          withCredentials([
            [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: CRED_ID, secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
            ]) {
            sh 'aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID'
            sh 'aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY'
            dir('generic-internal-service') {
            sh "helm upgrade --timeout 6m30s --install --kube-context ${KUBE_CONFIG} --atomic -n generic-internal-service-${ENVIRONMENT.toLowerCase()} --set tag=${TAG},environment=${ENVIRONMENT},rhel_version=${RHEL_VERSION},region=${REGION} ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} -f ${MICROSERVICES}-values.yaml ."
            sh "kubectl config use-context ${KUBE_CONFIG}"
            sh "kubectl rollout restart deploy ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} -n generic-internal-service-${ENVIRONMENT.toLowerCase()}"       
            }
          }
        }          
    }
    stage('Deploying annuity services app') {
      when {
        anyOf { 
                        environment name: 'MICROSERVICES', value: 'annuity-utility-services'
                        environment name: 'MICROSERVICES', value: 'annuity-entity-api'
                environment name: 'MICROSERVICES', value: 'annuity-agent-api'
                environment name: 'MICROSERVICES', value: 'annuity-contract-financial-api'
                        environment name: 'MICROSERVICES', value: 'annuity-webtransdb-api'
                environment name: 'MICROSERVICES', value: 'annuity-contract-entity-api'
                                      environment name: 'MICROSERVICES', value: 'annuity-contract-info-api'
        }
      } 
        steps {
          withCredentials([
            [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: CRED_ID, secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
            ]) {
            sh 'aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID'
            sh 'aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY'
            dir('annuity-services') {
            sh "helm upgrade --timeout 6m30s --debug --install --kube-context ${KUBE_CONFIG} --atomic -n annuity-services-${ENVIRONMENT.toLowerCase()} --set tag=${TAG},environment=${ENVIRONMENT},active_profile=${ACTIVE_PROFILE},rhel_version=${RHEL_VERSION},region=${REGION} ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} -f ${MICROSERVICES}-values.yaml ."
            sh "kubectl config use-context ${KUBE_CONFIG}"
            sh "kubectl rollout restart deploy ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} -n annuity-services-${ENVIRONMENT.toLowerCase()}"       
            }
          }
        }          
    }                           
    stage('Deploying Digtran Services') {
      when {
        anyOf { 
          environment name: 'MICROSERVICES', value: 'transaction-data-services'
          environment name: 'MICROSERVICES', value: 'transaction-orchestrator-services'
          environment name: 'MICROSERVICES', value: 'admin-common-services'
          environment name: 'MICROSERVICES', value: 'customer-entity-services'
          environment name: 'MICROSERVICES', value: 'claims-services'
          environment name: 'MICROSERVICES', value: 'tax-services'
          environment name: 'MICROSERVICES', value: 'newgen-services'
        }
      } 
        steps {
          withCredentials([
            [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: CRED_ID, secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
            ]) {
            sh 'aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID'
            sh 'aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY'
            dir('digtran-services') {
            sh "helm upgrade --timeout 6m30s --debug --install --kube-context ${KUBE_CONFIG} --atomic -n digtran-services-${ENVIRONMENT.toLowerCase()} --set tag=${TAG},environment=${ENVIRONMENT},active_profile=${ACTIVE_PROFILE},rhel_version=${RHEL_VERSION},region=${REGION} ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} -f ${MICROSERVICES}-values.yaml ."
            sh "kubectl config use-context ${KUBE_CONFIG}"
            sh "kubectl rollout restart deploy ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} -n digtran-services-${ENVIRONMENT.toLowerCase()}"      
            }
          }
        }
    }
    stage('Deploying Portal Services') {
      when {
        anyOf { 
          environment name: 'MICROSERVICES', value: 'profile-services'
          environment name: 'MICROSERVICES', value: 'portal-claims-api'
          environment name: 'MICROSERVICES', value: 'email-services'
                        environment name: 'MICROSERVICES', value: 'document-engine'
                        environment name: 'MICROSERVICES', value: 'venerableportal-services'
                        environment name: 'MICROSERVICES', value: 'portal-data-services'
                        environment name: 'MICROSERVICES', value: 'sharepoint-services'
          environment name: 'MICROSERVICES', value: 'audit-services'
          environment name: 'MICROSERVICES', value: 'portal-adminmodule-api'
          environment name: 'MICROSERVICES', value: 'portal-csr-api'
          environment name: 'MICROSERVICES', value: 'portal-transaction-api'
          environment name: 'MICROSERVICES', value: 'portal-bobreport-services'
          environment name: 'MICROSERVICES', value: 'portal-commission-services'
          environment name: 'MICROSERVICES', value: 'portal-customer-api'
          environment name: 'MICROSERVICES', value: 'portal-professional-api'
          environment name: 'MICROSERVICES', value: 'portal-userprofile-api'
        }
      } 
        steps {
          withCredentials([
            [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: CRED_ID, secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
            ]) {
            sh 'aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID'
            sh 'aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY'
            dir('portal-services') {
            sh "helm upgrade --timeout 6m30s --debug --install --kube-context ${KUBE_CONFIG} --atomic -n portal-services-${ENVIRONMENT.toLowerCase()} --set tag=${TAG},environment=${ENVIRONMENT},active_profile=${ACTIVE_PROFILE},rhel_version=${RHEL_VERSION},region=${REGION} ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} -f ${MICROSERVICES}-values.yaml ."
            sh "kubectl config use-context ${KUBE_CONFIG}"
            sh "kubectl rollout restart deploy ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} -n portal-services-${ENVIRONMENT.toLowerCase()}"      
            }
         }  
      }          
    }             
    stage('Deploying Document Services') {
      when {
        anyOf { 
          environment name: 'MICROSERVICES', value: 'ultradoc-services'
          
        }
      } 
        steps {
          withCredentials([
            [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: CRED_ID, secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
            ]) {
            sh 'aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID'
            sh 'aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY'
            dir('document-services') {
            sh "helm upgrade --timeout 6m30s --install --kube-context ${KUBE_CONFIG} --atomic -n document-services-${ENVIRONMENT.toLowerCase()} --set tag=${TAG},environment=${ENVIRONMENT},active_profile=${ACTIVE_PROFILE},rhel_version=${RHEL_VERSION},region=${REGION} ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} -f ${MICROSERVICES}-values.yaml ."
            sh "kubectl config use-context ${KUBE_CONFIG}"
            sh "kubectl rollout restart deploy ${MICROSERVICES}-${ENVIRONMENT.toLowerCase()} -n document-services-${ENVIRONMENT.toLowerCase()}"      
            }
          }
        }
    }
  }
  post {
    success {
      emailext mimeType: 'text/html', attachLog: true, body: 'Deployment completed successfully ', compressLog: false, recipientProviders: [requestor()], replyTo: 'devops@venerable.com', subject: '${BUILD_TAG}', to: "${SEND_NOTIFICATIONS}"
    }
    failure {
      emailext mimeType: 'text/html', attachLog: true, body: 'Deployment failed ', compressLog: false, recipientProviders: [requestor()], replyTo: 'devops@venerable.com', subject: '${BUILD_TAG}', to: "${SEND_NOTIFICATIONS}"
    }
    always {
      cleanWs()
    }
  }
}
