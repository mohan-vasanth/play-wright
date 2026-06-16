pipeline {

    agent any

    environment {
        JMETER_HOME = 'C:\\apache-jmeter-5.6.3'
        REPORT_DIR  = 'reports'
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {

        // ── Stage 1: Build ────────────────────────────────────────────────
        stage('Maven Build') {
            steps {
                bat 'mvn clean compile -q'
            }
        }

        // ── Stage 2: Playwright UI Tests ──────────────────────────────────
        stage('Playwright UI Tests') {
            steps {
                bat '''
                    mvn test ^
                      -Dtest=com.automation.playwright_framework.LoginTest ^
                      -Dplaywright.headless=true ^
                      -Dtradenix.user.username=mohan ^
                      -Dtradenix.user.password=12345678
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: 'target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'reports/playwright/**',
                                     allowEmptyArchive: true
                }
            }
        }

        // ── Stage 3: JMeter Performance Tests ────────────────────────────
        stage('JMeter Performance Test') {
            steps {
                bat '''
                    if exist reports\\jmeter\\results.jtl del /f reports\\jmeter\\results.jtl
                    if exist reports\\jmeter\\html-report rmdir /s /q reports\\jmeter\\html-report

                    %JMETER_HOME%\\bin\\jmeter.bat ^
                      -n ^
                      -t jmeter\\performance-test.jmx ^
                      -l reports\\jmeter\\results.jtl ^
                      -e ^
                      -o reports\\jmeter\\html-report
                '''
            }
            post {
                always {
                    // Publish JMeter HTML report tab in Jenkins
                    publishHTML(target: [
                        allowMissing:          true,
                        alwaysLinkToLastBuild: true,
                        keepAll:               true,
                        reportDir:             'reports/jmeter/html-report',
                        reportFiles:           'index.html',
                        reportName:            'JMeter Performance Report'
                    ])
                    archiveArtifacts artifacts: 'reports/jmeter/**',
                                     allowEmptyArchive: true
                }
            }
        }

        // ── Stage 4: Archive All Reports ─────────────────────────────────
        stage('Archive Reports') {
            steps {
                archiveArtifacts artifacts: 'reports/**', allowEmptyArchive: true
                echo "All reports archived."
                echo "JMeter HTML Report: ${env.BUILD_URL}JMeter_Performance_Report/"
            }
        }
    }

    post {
        success {
            echo "Pipeline PASSED — all UI and performance tests succeeded."
        }
        failure {
            echo "Pipeline FAILED — check Playwright and JMeter reports above."
            emailext(
                to:      'qa-team@yourdomain.com',
                subject: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body:    "Build failed: ${env.BUILD_URL}"
            )
        }
        always {
            cleanWs(cleanWhenSuccess: false)
        }
    }
}
