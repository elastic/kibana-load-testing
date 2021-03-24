def uploadGcsArtifact(uploadPrefix, pattern) {
    googleStorageUpload(
            credentialsId: 'kibana-ci-gcs-plugin',
            bucket: "gs://${uploadPrefix}",
            pattern: pattern,
            sharedPublicly: true,
            showInline: true,
    )
}

def uploadGatlingReport(index = 1) {
    uploadGcsArtifact("kibana-ci-artifacts/jobs/${env.JOB_NAME}/${env.BUILD_NUMBER}/${index}", 'report.tar.gz')
    uploadGcsArtifact("kibana-ci-artifacts/jobs/${env.JOB_NAME}/${env.BUILD_NUMBER}/${index}", 'lastDeployment.txt')
}

return this
