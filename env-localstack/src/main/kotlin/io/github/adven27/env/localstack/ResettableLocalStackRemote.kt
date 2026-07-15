package io.github.adven27.env.localstack

import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import io.github.adven27.env.core.shouldReset
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.Delete
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import java.net.URI

@Suppress("unused")
fun resettableLocalStack(props: Map<String, String>): ExternalSystem = ResettableLocalStackRemote(props)

private class ResettableLocalStackRemote(private val props: Map<String, String>) : ExternalSystem {
    override val config: ExternalSystemConfig = ExternalSystemConfig(props)

    override fun start(fixedEnv: Boolean) {
        props.forEach(System::setProperty)
        if (!shouldReset()) return

        val endpoint = props.getValue("env.aws.s3.endpoint")
        val region = props.getValue("env.aws.region")
        val accessKey = props.getValue("env.aws.access-key")
        val secretKey = props.getValue("env.aws.secret-key")
        val bucket = props.getValue("env.aws.s3.bucket")

        val s3 = S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
            )
            .region(Region.of(region))
            .build()

        s3.use { client ->
            ensureBucketEmpty(client, bucket)
        }
    }

    override fun stop() = Unit

    override fun running() = true

    private fun ensureBucketEmpty(s3: S3Client, bucket: String) {
        // Ensure the bucket exists — create only if missing (idempotent on first run)
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
        } catch (_: BucketAlreadyOwnedByYouException) {
            // Already exists — proceed to empty
        } catch (_: BucketAlreadyExistsException) {
            // Already exists — proceed to empty
        }

        // Bulk-empty: one deleteObjects call per page
        var continuationToken: String? = null
        do {
            val request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .also { b -> continuationToken?.let { b.continuationToken(it) } }
                .build()
            val response = s3.listObjectsV2(request)
            val objects = response.contents()
            if (objects.isNotEmpty()) {
                val identifiers = objects.map {
                    ObjectIdentifier.builder().key(it.key()).build()
                }
                s3.deleteObjects(
                    DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(identifiers).build())
                        .build()
                )
            }
            continuationToken = if (response.isTruncated == true) response.nextContinuationToken() else null
        } while (continuationToken != null)
    }
}
