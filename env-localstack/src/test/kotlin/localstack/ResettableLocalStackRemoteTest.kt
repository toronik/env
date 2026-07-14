package localstack

import io.github.adven27.env.localstack.resettableLocalStack
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest

class ResettableLocalStackRemoteTest {

    private val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
        .withServices(LocalStackContainer.Service.S3)

    private lateinit var s3: S3Client

    @Before
    fun startContainer() {
        localstack.start()

        s3 = S3Client.builder()
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test", "test")
                )
            )
            .region(Region.of("us-east-1"))
            .build()

        // Create bucket and put an object to simulate existing state
        s3.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build())
        s3.putObject(
            PutObjectRequest.builder().bucket(TEST_BUCKET).key("junk-object").build(),
            RequestBody.fromString("junk content")
        )
    }

    @After
    fun stopContainer() {
        System.clearProperty("SPECS_SUT_START")
        s3.close()
        localstack.stop()
    }

    @Test
    fun `start with reset gate unset recreates bucket so it is empty`() {
        System.clearProperty("SPECS_SUT_START") // gate absent → shouldReset = true

        val endpoint = localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString()
        val props = mapOf(
            "env.aws.s3.endpoint" to endpoint,
            "env.aws.region" to "us-east-1",
            "env.aws.access-key" to "test",
            "env.aws.secret-key" to "test",
            "env.aws.s3.bucket" to TEST_BUCKET
        )

        val system = resettableLocalStack(props)
        system.start(false)

        assertTrue(system.running())

        // Bucket should exist and be empty
        val objects = s3.listObjectsV2(
            ListObjectsV2Request.builder().bucket(TEST_BUCKET).build()
        )
        assertEquals("bucket should be empty after reset", 0, objects.contents().size)
    }

    @Test
    fun `start with SPECS_SUT_START=false is connect-only and data survives`() {
        System.setProperty("SPECS_SUT_START", "false")

        val endpoint = localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString()
        val props = mapOf(
            "env.aws.s3.endpoint" to endpoint,
            "env.aws.region" to "us-east-1",
            "env.aws.access-key" to "test",
            "env.aws.secret-key" to "test",
            "env.aws.s3.bucket" to TEST_BUCKET
        )

        val system = resettableLocalStack(props)
        system.start(false)

        // Object should survive (no reset performed)
        val objects = s3.listObjectsV2(
            ListObjectsV2Request.builder().bucket(TEST_BUCKET).build()
        )
        assertEquals("data should survive in connect-only mode", 1, objects.contents().size)
        assertEquals("junk-object", objects.contents()[0].key())
    }

    companion object {
        private const val TEST_BUCKET = "pk-849-test"
    }
}
