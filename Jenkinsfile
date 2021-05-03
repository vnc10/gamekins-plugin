pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                sh 'mvn -B clean compile'
            }
        }
        stage('Test') {
            steps {
                sh 'mvn -B test'
            }
        }
        stage('Package') {
            steps {
                sh 'mvn -B hpi:hpi'
            }
        }
    }
    post {
        always {
            gamekins jacocoCSVPath: '**/target/site/jacoco/jacoco.csv', jacocoResultsPath: '**/target/site/jacoco/', mocoJSONPath: '**/target/moco/mutation/moco.json', searchCommitCount: 50
        }
    }
}
