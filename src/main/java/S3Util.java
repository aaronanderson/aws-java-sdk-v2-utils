

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

import software.amazon.awssdk.core.client.builder.ClientHttpConfiguration;
import software.amazon.awssdk.core.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.interceptor.Context.BeforeTransmission;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.InterceptorContext;
import software.amazon.awssdk.core.regions.Region;
import software.amazon.awssdk.core.sync.ResponseInputStream;
import software.amazon.awssdk.http.Abortable;
import software.amazon.awssdk.http.AbortableCallable;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkRequestContext;
import software.amazon.awssdk.services.s3.AwsS3V4Signer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public class S3Util {

	public static URI presignS3DownloadLink(String bucketName, String fileName) throws SdkClientException {
		try {
			S3ClientBuilder s3Builder = S3Client.builder().region(Region.US_WEST_1);
			S3PresignExecutionInterceptor presignInterceptor = new S3PresignExecutionInterceptor(Region.US_WEST_1, LocalDateTime.now().plusDays(4));
			s3Builder.overrideConfiguration(ClientOverrideConfiguration.builder().addLastExecutionInterceptor(presignInterceptor).build());
			s3Builder.httpConfiguration(ClientHttpConfiguration.builder().httpClient(new NullSdkHttpClient()).build());
			S3Client s3Client = s3Builder.build();

			GetObjectRequest s3GetRequest = GetObjectRequest.builder().bucket(bucketName).key(fileName).build();
			ResponseInputStream<GetObjectResponse> response = s3Client.getObject(s3GetRequest);
			response.close();

			return presignInterceptor.getSignedURI();
		} catch (Throwable t) {
			if (t instanceof SdkClientException) {
				throw (SdkClientException) t;
			}
			throw new SdkClientException(t);
		}
	}

	public static class NullSdkHttpClient implements SdkHttpClient {

		@Override
		public void close() {

		}

		@Override
		public <T> Optional<T> getConfigurationValue(SdkHttpConfigurationOption<T> key) {
			return Optional.empty();
		}

		@Override
		public AbortableCallable<SdkHttpFullResponse> prepareRequest(SdkHttpFullRequest request, SdkRequestContext requestContext) {
			return new AbortableCallable<SdkHttpFullResponse>() {
				@Override
				public SdkHttpFullResponse call() throws Exception {
					return SdkHttpFullResponse.builder().statusCode(200).content(new AbortableInputStream(new ByteArrayInputStream(new byte[0]), new Abortable() {

						@Override
						public void abort() {

						}
					})).build();
				}

				@Override
				public void abort() {

				}
			};
		}

	}

	public static class S3PresignExecutionInterceptor implements ExecutionInterceptor {

		final private AwsS3V4Signer signer;
		final private LocalDateTime expirationTime;
		private URI signedURI;

		public S3PresignExecutionInterceptor(Region region, LocalDateTime expirationTime) {
			signer = new AwsS3V4Signer();
			signer.setServiceName("s3");
			signer.setRegionName(region.value());
			this.expirationTime = expirationTime;
		}

		@Override
		public void beforeTransmission(BeforeTransmission context, ExecutionAttributes executionAttributes) {
			// remove all headers because a Browser that downloads the shared URL will not send the exact values. X-Amz-SignedHeaders should only contain the host header.
			SdkHttpFullRequest modifiedSdkRequest = context.httpRequest().toBuilder().clearHeaders().build();

			InterceptorContext modifiedContext = InterceptorContext.builder().request(context.request()).httpRequest(modifiedSdkRequest).build();
			Date expirationDate = Date.from(expirationTime.atZone(ZoneId.systemDefault()).toInstant());
			SdkHttpFullRequest signedRequest = signer.presign(modifiedContext, executionAttributes, expirationDate);// sign(getRequest, new ExecutionAttributes());
			signedURI = signedRequest.getUri();
		}

		public URI getSignedURI() {
			return signedURI;
		}

	}

	public static void main(String[] args) throws Exception {
		System.out.format("Download URL: %s\n", presignS3DownloadLink("some-bucket", "some-key.txt"));
	}

}
