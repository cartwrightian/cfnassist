package tw.com;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

public class TestDeltaIndexTagging {

	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(TestAwsFacade.PROJECT, TestAwsFacade.ENV);
	private DefaultAWSCredentialsProviderChain credentialsProvider;
	private AwsFacade aws;
	private VpcRepository vpcRepos;

	@Before
	public void beforeTestsRun() {
		credentialsProvider = new DefaultAWSCredentialsProviderChain();
		aws = new AwsFacade(credentialsProvider, TestAwsFacade.getRegion());
		vpcRepos = new VpcRepository(credentialsProvider, TestAwsFacade.getRegion());	
	}

	@Test
	public void canSetAndResetTheDeltaIndexTagOnVpc() {
		aws.resetDeltaIndex(mainProjectAndEnv);
		
		aws.setDeltaIndex(mainProjectAndEnv, 42);
		int tagValue = getValueOfTag();
		assertEquals(42, tagValue);	
		int result = aws.getDeltaIndex(mainProjectAndEnv);
		assertEquals(42, result);
		
		aws.resetDeltaIndex(mainProjectAndEnv);
		tagValue = getValueOfTag();
		assertEquals(0, tagValue);
		result = aws.getDeltaIndex(mainProjectAndEnv);
		assertEquals(0, result);
	}

	private int getValueOfTag() {
		Vpc vpc = vpcRepos.getCopyOfVpc(mainProjectAndEnv);
		List<Tag> tags = vpc.getTags();
		for(Tag tag : tags) {
			if (tag.getKey().equals(AwsFacade.INDEX_TAG)) {
				return Integer.parseInt(tag.getValue());
			}
		}
		return -1;
	}

}
