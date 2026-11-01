// Pipeline CI de backend-ci : build, tests, analyses de securite, images Docker et publication sur Docker Hub.
//
// Prerequis de l'instance Jenkins :
//   - agent portant le label MI_AG, lance directement sur le Mac (agent.jar) avec Docker Desktop ;
//   - plugins : workflow-aggregator, credentials-binding, junit, github, pipeline-build-step ;
//   - credentials :
//       dockerhub-credentials : utilisateur et jeton d'acces Docker Hub ;
//       nvd-api-key           : texte secret, cle de l'API NVD, seulement si RUN_DEPCHECK est coche ;
//       sonar-token           : texte secret, seulement si RUN_SONAR est coche ;
//   - le profil Compose all ne tourne pas pendant un build (memoire du poste).
//
// Les outils (JDK 21, Trivy) tournent dans des conteneurs ephemeres : l'espace de travail y est monte au meme
// chemin que sur l'hote, et le dossier persistant ~/.cache/internatadmin-ci de l'hote sert de HOME (TOOL_HOME),
// avec le depot Maven de l'hote (~/.m2) monte dedans : dependances Maven, bases NVD et Trivy restent en cache.
// Aucun secret n'est ecrit dans une commande ni dans une image : uniquement des variables d'environnement.

def SERVICES = ['api-gateway', 'auth-service', 'audit-service', 'enfant-service',
                'personnel-service', 'paiement-service', 'comptabilite-service', 'pilotage-service']
// un changement dans ces chemins concerne toutes les images
def SHARED_PATHS = ['common/', 'pom.xml', 'Dockerfile', '.dockerignore', '.mvn/', 'mvnw']

// Lance une commande dans un conteneur outil, avec l'utilisateur de l'agent et la socket Docker de l'hote.
// Sous Docker Desktop, la socket vue du conteneur appartient a root:root (0660) : d'ou --group-add 0.
// extraArgs : options docker run supplementaires (variables transmises par nom, sans valeur).
// returnStatus : renvoie le code de sortie au lieu de faire echouer le stage.
def runTool(String image, String extraArgs, String command, boolean returnStatus = false) {
    sh returnStatus: returnStatus, script: """
        mkdir -p "\$HOME/.m2" "\$HOME/.cache/internatadmin-ci"
        docker run --rm -v "\$WORKSPACE:\$WORKSPACE" -w "\$WORKSPACE" \\
            -v "\$HOME/.cache/internatadmin-ci:\$TOOL_HOME" -v "\$HOME/.m2:\$TOOL_HOME/.m2" -e HOME="\$TOOL_HOME" \\
            -u "\$(id -u):\$(id -g)" --group-add 0 -v /var/run/docker.sock:/var/run/docker.sock \\
            ${extraArgs} ${image} ${command}
    """
}

// Maven dans le JDK 21. Testcontainers joint ses conteneurs par la passerelle de l'hote.
def mvnw(String args) {
    runTool(env.BUILD_IMAGE,
            '--add-host host.docker.internal:host-gateway ' +
            '-e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal ' +
            '-e TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock',
            "./mvnw -B ${args}")
}

// Services dont l'image doit etre reconstruite depuis le dernier build reussi.
def changedServices(List services, List sharedPaths, boolean buildAll) {
    def base = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT
    if (buildAll || !base || sh(returnStatus: true, script: "git cat-file -e ${base}^{commit}") != 0) {
        return services
    }
    def files = sh(returnStdout: true, script: "git diff --name-only ${base} HEAD").trim().readLines()
    if (files.any { f -> sharedPaths.any { p -> f == p || f.startsWith(p) } }) {
        return services
    }
    return services.findAll { s -> files.any { it.startsWith("${s}/") } }
}

pipeline {
    agent { label 'MI_AG' }

    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
        // la premiere analyse OWASP telecharge toute la base NVD
        timeout(time: 180, unit: 'MINUTES')
    }

    triggers {
        // webhook GitHub si Jenkins est joignable, sinon scrutation du depot
        githubPush()
        pollSCM('H/5 * * * *')
    }

    parameters {
        booleanParam(name: 'BUILD_ALL', defaultValue: false, description: 'Reconstruire les 8 images meme sans changement')
        booleanParam(name: 'RUN_DEPCHECK', defaultValue: true, description: 'Analyse des dependances OWASP Dependency-Check')
        string(name: 'DC_FAIL_CVSS', defaultValue: '9', description: 'Score CVSS a partir duquel OWASP Dependency-Check fait echouer le build')
        booleanParam(name: 'RUN_SONAR', defaultValue: false, description: 'Analyse SonarQube (serveur et credential sonar-token requis)')
        string(name: 'SONAR_HOST_URL', defaultValue: 'http://host.docker.internal:9000', description: 'URL du serveur SonarQube')
        booleanParam(name: 'TRIGGER_DEPLOY', defaultValue: false, description: 'Declencher le job de deploiement apres le push')
        string(name: 'DEPLOY_JOB', defaultValue: 'deployment-cd', description: 'Job Jenkins du depot deployment-cd')
        booleanParam(name: 'ALLOW_RUNNING_STACK', defaultValue: false, description: 'Autoriser le build alors que le profil Compose all tourne')
    }

    environment {
        BUILD_IMAGE = 'eclipse-temurin:21-jdk-alpine'
        TRIVY_IMAGE = 'aquasec/trivy:0.58.2'
        TRIVY_VULN_EXIT_CODE = '10'
        // HOME des conteneurs outils, adosse a ~/.cache/internatadmin-ci sur l'hote
        TOOL_HOME = '/ci-home'
        DC_VERSION = '12.1.0'
        SONAR_PLUGIN_VERSION = '5.0.0.4389'
        DOCKERHUB_USER = 'abdoulaye940'
        DOCKERHUB_REPO = 'internatadmin-backend'
        IMAGE_REPO = "${DOCKERHUB_USER}/${DOCKERHUB_REPO}"
        // tag de version lisible : <service>-1.<numero de build>
        VERSION = "1.${BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_SHA = sh(returnStdout: true, script: 'git rev-parse --short=12 HEAD').trim()
                    def branch = (env.BRANCH_NAME ?: env.GIT_BRANCH ?: '').replaceFirst('^origin/', '')
                    env.IS_MAIN = (branch == 'main').toString()
                    echo "Commit ${env.GIT_SHA}, branche ${branch}"
                }
            }
        }

        stage('Environnement') {
            steps {
                sh 'docker version --format "Docker {{.Server.Version}}"'
                script {
                    def stack = sh(returnStdout: true, script: 'docker ps -q --filter label=com.docker.compose.project=internatadmin ' +
                                   '--filter label=com.docker.compose.service=pilotage-service').trim()
                    if (stack && !params.ALLOW_RUNNING_STACK) {
                        error('Le profil Compose all tourne : l\'arreter (docker compose --profile all stop) avant le build')
                    }
                }
            }
        }

        stage('Build et tests unitaires') {
            steps {
                mvnw('clean verify -DskipITs')
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage("Tests d'integration") {
            steps {
                // Testcontainers (PostgreSQL, RabbitMQ) et WireMock ; les tests unitaires ne sont pas rejoues
                mvnw('verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false')
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/failsafe-reports/*.xml'
                }
            }
        }

        stage('Analyse des dependances (OWASP)') {
            when { expression { params.RUN_DEPCHECK } }
            steps {
                withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
                    // la cle reste dans l'environnement : seul son nom figure dans la commande
                    runTool(env.BUILD_IMAGE, '-e NVD_API_KEY',
                            "./mvnw -B org.owasp:dependency-check-maven:${DC_VERSION}:aggregate " +
                            '-DnvdApiKeyEnvironmentVariable=NVD_API_KEY ' +
                            '-DdataDirectory="$TOOL_HOME/dependency-check-data" ' +
                            '-DsuppressionFiles="$WORKSPACE/dependency-check-suppressions.xml" ' +
                            "-Dformats=HTML,JSON -DfailBuildOnCVSS=${params.DC_FAIL_CVSS} " +
                            '-DossindexAnalyzerEnabled=false')
                }
            }
        }

        stage('SonarQube') {
            when { expression { params.RUN_SONAR } }
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    runTool(env.BUILD_IMAGE, '--add-host host.docker.internal:host-gateway -e SONAR_TOKEN',
                            "./mvnw -B org.sonarsource.scanner.maven:sonar-maven-plugin:${SONAR_PLUGIN_VERSION}:sonar " +
                            "-Dsonar.host.url=${params.SONAR_HOST_URL} -Dsonar.projectKey=internatadmin-backend")
                }
            }
        }

        stage('Services modifies') {
            steps {
                script {
                    def services = changedServices(SERVICES, SHARED_PATHS, params.BUILD_ALL)
                    env.IMAGE_SERVICES = services.join(',')
                    echo(services ? "Images a construire : ${env.IMAGE_SERVICES}" : 'Aucune image a reconstruire')
                }
            }
        }

        stage('Build des images') {
            when { expression { env.IMAGE_SERVICES } }
            steps {
                script {
                    for (svc in env.IMAGE_SERVICES.split(',')) {
                        sh "docker build --build-arg SERVICE=${svc} " +
                           "--label org.opencontainers.image.revision=${env.GIT_COMMIT} " +
                           "-t ${IMAGE_REPO}:${svc}-${env.GIT_SHA} ."
                    }
                }
            }
        }

        stage('Scan des images (Trivy)') {
            when { expression { env.IMAGE_SERVICES } }
            steps {
                script {
                    sh 'mkdir -p reports/trivy'
                    def critical = []
                    for (svc in env.IMAGE_SERVICES.split(',')) {
                        def image = "${IMAGE_REPO}:${svc}-${env.GIT_SHA}"
                        // rapport HIGH et CRITICAL archive, puis controle bloquant sur CRITICAL seulement
                        runTool(env.TRIVY_IMAGE, '',
                                "image --timeout 20m --cache-dir \"\$TOOL_HOME/.cache/trivy\" --no-progress " +
                                "--ignorefile .trivyignore --show-suppressed " +
                                "--severity HIGH,CRITICAL --format table --output reports/trivy/${svc}.txt ${image}")
                        // code dedie : 10 = vulnerabilites trouvees, tout autre code non nul = echec de l'outil
                        def status = runTool(env.TRIVY_IMAGE, '',
                                "image --timeout 20m --cache-dir \"\$TOOL_HOME/.cache/trivy\" --skip-db-update --no-progress " +
                                "--ignorefile .trivyignore --severity CRITICAL --exit-code ${TRIVY_VULN_EXIT_CODE} --format json " +
                                "--output reports/trivy/${svc}-critical.json ${image}", true)
                        if (status.toString() == TRIVY_VULN_EXIT_CODE) {
                            critical << svc
                        } else if (status != 0) {
                            error("Echec de l'analyse Trivy de ${svc} (code ${status}) : resultat inconnu, pas une vulnerabilite")
                        }
                    }
                    if (critical) {
                        error("Vulnerabilites CRITICAL dans : ${critical.join(', ')} (voir reports/trivy)")
                    }
                }
            }
        }

        stage('Push Docker Hub') {
            when { expression { env.IMAGE_SERVICES && env.IS_MAIN == 'true' } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials',
                                                  usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                        set +x
                        printf '%s' "$DOCKER_PASS" | docker login --username "$DOCKER_USER" --password-stdin
                    '''
                }
                script {
                    for (svc in env.IMAGE_SERVICES.split(',')) {
                        sh """
                            docker tag ${IMAGE_REPO}:${svc}-${env.GIT_SHA} ${IMAGE_REPO}:${svc}-${VERSION}
                            docker tag ${IMAGE_REPO}:${svc}-${env.GIT_SHA} ${IMAGE_REPO}:${svc}-latest
                            docker push ${IMAGE_REPO}:${svc}-${env.GIT_SHA}
                            docker push ${IMAGE_REPO}:${svc}-${VERSION}
                            docker push ${IMAGE_REPO}:${svc}-latest
                        """
                    }
                }
            }
        }

        stage('Declenchement du deploiement') {
            when { expression { params.TRIGGER_DEPLOY && env.IMAGE_SERVICES && env.IS_MAIN == 'true' } }
            steps {
                build job: params.DEPLOY_JOB, wait: false, parameters: [
                    string(name: 'BACKEND_SERVICES', value: env.IMAGE_SERVICES),
                    string(name: 'BACKEND_TAG', value: env.GIT_SHA)
                ]
            }
        }
    }

    post {
        always {
            archiveArtifacts allowEmptyArchive: true, fingerprint: false, artifacts:
                'target/dependency-check-report.*, reports/trivy/**, .trivyignore, dependency-check-suppressions.xml, **/target/site/jacoco/**'
            sh 'docker logout >/dev/null 2>&1 || true'
            script {
                // images locales du build : elles sont sur Docker Hub, on libere le disque du poste
                for (svc in (env.IMAGE_SERVICES ?: '').tokenize(',')) {
                    sh "docker image rm ${IMAGE_REPO}:${svc}-${env.GIT_SHA} ${IMAGE_REPO}:${svc}-${VERSION} ${IMAGE_REPO}:${svc}-latest >/dev/null 2>&1 || true"
                }
            }
        }
        success {
            echo "Build reussi : commit ${env.GIT_SHA}, version ${VERSION}, images ${env.IMAGE_SERVICES ?: 'aucune'}"
        }
        failure {
            echo 'Build en echec : voir le stage en rouge et les rapports archives'
        }
    }
}
