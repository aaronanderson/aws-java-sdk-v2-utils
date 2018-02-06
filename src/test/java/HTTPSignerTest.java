
import javax.json.JsonObject;

import org.junit.Test;

import software.amazon.awssdk.core.auth.AwsCredentialsProviderChain;
import software.amazon.awssdk.core.auth.DefaultCredentialsProvider;
import software.amazon.awssdk.core.auth.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;

public class HTTPSignerTest {

	@Test
	public void searchTest() throws Exception {
		AWSSignerHttpClient client = AWSSignerHttpClient.builder().serviceName("es").awsCredentials(AwsCredentialsProviderChain.of(InstanceProfileCredentialsProvider.builder().build(), DefaultCredentialsProvider.create())).build();

		SdkHttpFullRequest httpRequest = SdkHttpFullRequest.builder().method(SdkHttpMethod.GET).protocol("https").host("search-some-aws-elasticsearch-domain.us-west-1.es.amazonaws.com").build();
		JsonObject obj = client.<JsonObject> execute(httpRequest);

		System.out.format("Result: %s\n", obj);

	}

}
