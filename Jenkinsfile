// Pipeline CI du backend monolithique : build, tests, analyses de securite, image Docker et publication sur Docker Hub.
//
// Prerequis de l'instance Jenkins :
//   - agent portant le label MI_AG, lance directement sur le Mac (agent.jar) avec Docker Desktop ;
//   - plugins : workflow-aggregator, credentials-binding, junit, github ;
//   - credentials :
//       dockerhub-credentials : utilisateur et jeton d'acces Docker Hub ;
//       nvd-api-key           : texte secret, cle de l'API NVD, seulement si RUN_DEPCHECK est coche ;
//       sonar-token           : texte secret, seulement si RUN_SONAR est coche.
//
// Les outils (JDK 21, Trivy) tournent dans des conteneurs ephemeres : l'espace de travail y est monte au meme
// chemin que sur l'hote, et le dossier persistant ~/.cache/internatadmin-ci de l'hote sert de HOME (TOOL_HOME),
// avec le depot Maven de l'hote (~/.m2) monte dedans : dependances Maven, bases NVD et Trivy restent en cache.
// Aucun secret n'est ecrit dans une commande ni dans une image : uniquement des variables d'environnement.
//
// Build incremental : sans changement du code depuis le dernier build reussi, tout est saute apres le checkout.
// BUILD_ALL force le build complet.

// un changement dans ces chemins concerne l'application ou son image
def BUILD_PATHS = ['src/', 'pom.xml', 'Dockerfile', '.dockerignore', '.mvn/', 'mvnw']

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

// Le code a-t-il change depuis le dernier build reussi ?
def hasChanges(List buildPaths, boolean buildAll) {
    def base = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT
    if (buildAll || !base || sh(returnStatus: true, script: "git cat-file -e ${base}^{commit}") != 0) {
        return true
    }
    def files = sh(returnStdout: true, script: "git diff --name-only --no-renames ${base} HEAD").trim().readLines()
    return files.any { f -> buildPaths.any { p -> f == p || f.startsWith(p) } }
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
        booleanParam(name: 'BUILD_ALL', defaultValue: false, description: 'Tout executer meme sans changement du code')
        booleanParam(name: 'RUN_DEPCHECK', defaultValue: true, description: 'Analyse des dependances OWASP Dependency-Check')
        string(name: 'DC_FAIL_CVSS', defaultValue: '9', description: 'Score CVSS a partir duquel OWASP Dependency-Check fait echouer le build')
        booleanParam(name: 'RUN_SONAR', defaultValue: false, description: 'Analyse SonarQube (serveur et credential sonar-token requis)')
        string(name: 'SONAR_HOST_URL', defaultValue: 'http://host.docker.internal:9000', description: 'URL du serveur SonarQube')
        booleanParam(name: 'TRIGGER_DEPLOY', defaultValue: false, description: 'Declencher le job de deploiement apres le push')
        string(name: 'DEPLOY_JOB', defaultValue: 'deployment-cd', description: 'Job Jenkins du depot deployment-cd')
    }

    environment {
        BUILD_IMAGE = 'eclipse-temurin:21-jdk-alpine'
        TRIVY_IMAGE = 'aquasec/trivy:0.58.2'
        TRIVY_VULN_EXIT_CODE = '10'
        // HOME des conteneurs outils, adosse a ~/.cache/internatadmin-ci sur l'hote
        TOOL_HOME = '/ci-home'
        DC_VERSION = '12.2.2'
        SONAR_PLUGIN_VERSION = '5.0.0.4389'
        DOCKERHUB_USER = 'abdoulaye940'
        DOCKERHUB_REPO = 'daara-backend'
        IMAGE_REPO = "${DOCKERHUB_USER}/${DOCKERHUB_REPO}"
        // tag de version lisible : 1.<numero de build>
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
                    env.HAS_CHANGES = hasChanges(BUILD_PATHS, params.BUILD_ALL).toString()
                    echo "Commit ${env.GIT_SHA}, branche ${branch}, code modifie : ${env.HAS_CHANGES}"
                }
            }
        }

        stage('Build et tests unitaires') {
            when { expression { env.HAS_CHANGES == 'true' } }
            steps {
                mvnw('clean verify -DskipITs')
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage("Tests d'integration") {
            when { expression { env.HAS_CHANGES == 'true' } }
            steps {
                // Testcontainers (PostgreSQL) ; les tests unitaires ne sont pas rejoues
                mvnw('verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false')
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/failsafe-reports/*.xml'
                }
            }
        }

        stage('Analyse des dependances (OWASP)') {
            when { expression { params.RUN_DEPCHECK && env.HAS_CHANGES == 'true' } }
            steps {
                withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
                    // la cle reste dans l'environnement : seul son nom figure dans la commande
                    runTool(env.BUILD_IMAGE, '-e NVD_API_KEY',
                            "./mvnw -B org.owasp:dependency-check-maven:${DC_VERSION}:check " +
                            '-DnvdApiKeyEnvironmentVariable=NVD_API_KEY ' +
                            // fichiers annuels de NVD : l'API paginee a echoue en 429 et en 503
                            "'-DnvdDatafeedUrl=https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz' " +
                            '-DdataDirectory="$TOOL_HOME/dependency-check-data" ' +
                            "-Dformats=HTML,JSON -DfailBuildOnCVSS=${params.DC_FAIL_CVSS} " +
                            '-DossindexAnalyzerEnabled=false')
                }
            }
        }

        stage('SonarQube') {
            when { expression { params.RUN_SONAR && env.HAS_CHANGES == 'true' } }
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    runTool(env.BUILD_IMAGE, '--add-host host.docker.internal:host-gateway -e SONAR_TOKEN',
                            "./mvnw -B org.sonarsource.scanner.maven:sonar-maven-plugin:${SONAR_PLUGIN_VERSION}:sonar " +
                            "-Dsonar.host.url=${params.SONAR_HOST_URL} -Dsonar.projectKey=internatadmin-monolithe")
                }
            }
        }

        stage("Build de l'image") {
            when { expression { env.HAS_CHANGES == 'true' } }
            steps {
                sh "docker build --label org.opencontainers.image.revision=${env.GIT_COMMIT} -t ${IMAGE_REPO}:${env.GIT_SHA} ."
            }
        }

        stage("Scan de l'image (Trivy)") {
            when { expression { env.HAS_CHANGES == 'true' } }
            steps {
                script {
                    sh 'mkdir -p reports/trivy'
                    def image = "${IMAGE_REPO}:${env.GIT_SHA}"
                    // rapport HIGH et CRITICAL archive, puis controle bloquant sur CRITICAL seulement
                    runTool(env.TRIVY_IMAGE, '',
                            "image --scanners vuln --timeout 20m --cache-dir \"\$TOOL_HOME/.cache/trivy\" --no-progress " +
                            "--ignorefile .trivyignore --show-suppressed " +
                            "--severity HIGH,CRITICAL --format table --output reports/trivy/image.txt ${image}")
                    // code dedie : 10 = vulnerabilites trouvees, tout autre code non nul = echec de l'outil
                    def status = runTool(env.TRIVY_IMAGE, '',
                            "image --scanners vuln --timeout 20m --cache-dir \"\$TOOL_HOME/.cache/trivy\" --skip-db-update --no-progress " +
                            "--ignorefile .trivyignore --severity CRITICAL --exit-code ${TRIVY_VULN_EXIT_CODE} --format json " +
                            "--output reports/trivy/image-critical.json ${image}", true)
                    if (status.toString() == TRIVY_VULN_EXIT_CODE) {
                        error('Vulnerabilites CRITICAL dans l\'image (voir reports/trivy)')
                    } else if (status != 0) {
                        error("Echec de l'analyse Trivy (code ${status}) : resultat inconnu, pas une vulnerabilite")
                    }
                }
            }
        }

        stage('Push Docker Hub') {
            when { expression { env.HAS_CHANGES == 'true' && env.IS_MAIN == 'true' } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials',
                                                  usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                        set +x
                        printf '%s' "$DOCKER_PASS" | docker login --username "$DOCKER_USER" --password-stdin
                    '''
                }
                sh """
                    docker tag ${IMAGE_REPO}:${env.GIT_SHA} ${IMAGE_REPO}:${VERSION}
                    docker tag ${IMAGE_REPO}:${env.GIT_SHA} ${IMAGE_REPO}:latest
                    docker push ${IMAGE_REPO}:${env.GIT_SHA}
                    docker push ${IMAGE_REPO}:${VERSION}
                    docker push ${IMAGE_REPO}:latest
                """
            }
        }

        stage('Declenchement du deploiement') {
            when { expression { params.TRIGGER_DEPLOY && env.HAS_CHANGES == 'true' && env.IS_MAIN == 'true' } }
            steps {
                build job: params.DEPLOY_JOB, wait: false, parameters: [
                    string(name: 'BACKEND_SERVICES', value: 'backend'),
                    string(name: 'BACKEND_TAG', value: env.GIT_SHA)
                ]
            }
        }
    }

    post {
        always {
            archiveArtifacts allowEmptyArchive: true, fingerprint: false, artifacts:
                'target/dependency-check-report.*, reports/trivy/**, .trivyignore, target/site/jacoco/**'
            sh 'docker logout >/dev/null 2>&1 || true'
            script {
                // images locales du build : elles sont sur Docker Hub, on libere le disque du poste
                if (env.GIT_SHA) {
                    sh "docker image rm ${IMAGE_REPO}:${env.GIT_SHA} ${IMAGE_REPO}:${VERSION} ${IMAGE_REPO}:latest >/dev/null 2>&1 || true"
                }
            }
        }
        success {
            echo "Build reussi : commit ${env.GIT_SHA}, version ${VERSION}, code modifie : ${env.HAS_CHANGES}"
        }
        failure {
            echo 'Build en echec : voir le stage en rouge et les rapports archives'
        }
    }
}
