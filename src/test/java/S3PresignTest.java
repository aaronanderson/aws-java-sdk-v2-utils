import static org.junit.Assert.assertNotNull;

import java.net.URI;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.johnzon.jaxrs.JsrProvider;
import org.junit.Test;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;

public class S3PresignTest {
	public static final String S3_BUCKET = "XXXXXXXXXXXXXXXXXX";
	public static final String S3_FILENAME = "/test/test.txt";

	@Test
	public void presignTest() throws Exception {
		AwsCredentialsProviderChain credentials = AwsCredentialsProviderChain.of(InstanceProfileCredentialsProvider.builder().build(), DefaultCredentialsProvider.create());
		S3Presigner s3Presigner = S3Presigner.builder().awsCredentials(credentials).build();

		URI presigned = s3Presigner.presignS3UploadLink(S3_BUCKET, S3_FILENAME);
		Client client = ClientBuilder.newClient();
		client.register(JsrProvider.class);
		WebTarget target = client.target(presigned);
		System.out.format("Upload URL: %s\n", presigned);
		Response response = target.request().put(Entity.entity("Sample File.", MediaType.TEXT_PLAIN_TYPE));
		assertNotNull(response);
		System.out.format("Upload Response: %d\n", response.getStatus());

		presigned = s3Presigner.presignS3DownloadLink(S3_BUCKET, S3_FILENAME);
		target = client.target(presigned);
		System.out.format("Download URL: %s\n", presigned);
		response = target.request().get();
		assertNotNull(response);
		System.out.format("Download Response: %d %s\n", response.getStatus(), response.readEntity(String.class));

	}

}
