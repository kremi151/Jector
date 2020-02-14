void setBuildStatus(String message, String state) {
	step([
		$class: "GitHubCommitStatusSetter",
		reposSource: [$class: "ManuallyEnteredRepositorySource", url: env.GIT_URL],
		commitShaSource: [$class: "ManuallyEnteredShaSource", sha: env.GIT_COMMIT],
		contextSource: [$class: "ManuallyEnteredCommitContextSource", context: "ci/jenkins/build-status"],
		errorHandlers: [[$class: "ChangingBuildStatusErrorHandler", result: "UNSTABLE"]],
		statusResultSource: [ $class: "ConditionalStatusResultSource", results: [[$class: "AnyBuildResult", message: message, state: state]] ]
	]);
}

pipeline {
	agent any
	stages {
		stage('Notify GitHub') {
			steps {
				setBuildStatus('Build is pending', 'PENDING')
			}
		}
		stage('Build') {
			steps {
				sh 'chmod +x gradlew'
				sh './gradlew build -x test'
			}
		}
		stage('Test') {
			steps {
				sh './gradlew cleanTest test'
			}
		}
		stage('Publish') {
		    environment {
		        GITHUB_USERNAME = "kremi151"
		    }
		    steps {
                withCredentials([string(credentialsId: "github_upload_packages_token", variable: "GITHUB_TOKEN")]) {
                    sh './gradlew publish'
                }
		    }
		}
	}
	post {
		always {
			archiveArtifacts artifacts: '*/build/libs/*.jar', onlyIfSuccessful: true
			junit '*/build/test-results/**/*.xml'
		}
		success {
			setBuildStatus('Build succeeded', 'SUCCESS')
		}
		failure {
			setBuildStatus('Build failed', 'FAILURE')
		}
		unstable {
			setBuildStatus('Build is unstable', 'UNSTABLE')
		}
	}
}