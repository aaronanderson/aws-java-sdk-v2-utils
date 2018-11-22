import static org.junit.Assert.assertNotNull;

import javax.json.JsonObject;

import org.junit.Test;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;

public class ElasticSearchTest {

	public static final String ELASTIC_SEARCH_HOST = "XXXXXXXXX.es.amazonaws.com";

	@Test
	public void searchTest() throws Exception {
		AWSSignerHttpClient client = AWSSignerHttpClient.builder().serviceName("es").awsCredentials(AwsCredentialsProviderChain.of(InstanceProfileCredentialsProvider.builder().build(), DefaultCredentialsProvider.create())).build();
		SdkHttpFullRequest httpRequest = SdkHttpFullRequest.builder().method(SdkHttpMethod.GET).protocol("https").host(ELASTIC_SEARCH_HOST).build();
		JsonObject obj = client.<JsonObject>execute(httpRequest);
		assertNotNull(obj);
		System.out.format("Result: %s\n", obj);

	}

}
