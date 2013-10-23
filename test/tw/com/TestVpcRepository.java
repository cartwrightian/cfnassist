package tw.com;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.model.Vpc;

public class TestVpcRepository {

	private DefaultAWSCredentialsProviderChain credentialsProvider;

	@Before
	public void beforeTestsRun() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
	}
	
	@Test
	public void test() {
		VpcRepository repository = new VpcRepository(credentialsProvider, TestAwsFacade.getRegion());
		Vpc vpc = repository.findVpcForEnv(TestAwsFacade.ENV);
		
		assertNotNull(vpc);
	}

}
