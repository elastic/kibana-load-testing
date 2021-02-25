def uploadGcsArtifact(uploadPrefix, pattern) {
    googleStorageUpload(
            credentialsId: 'kibana-ci-gcs-plugin',
            bucket: "gs://${uploadPrefix}",
            pattern: pattern,
            sharedPublicly: true,
            showInline: true,
    )
}

def uploadGatlingReport() {
    uploadGcsArtifact("kibana-ci-artifacts/jobs/${env.JOB_NAME}/${env.BUILD_NUMBER}", 'report.tar.gz')
    uploadGcsArtifact("kibana-ci-artifacts/jobs/${env.JOB_NAME}/${env.BUILD_NUMBER}", 'lastDeployment.txt')
}

return this
