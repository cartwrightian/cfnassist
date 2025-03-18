package tw.com.unit;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tw.com.EnvironmentSetupForTests;
import tw.com.entity.InstanceSummary;

import software.amazon.awssdk.services.ec2.model.Tag;

public class TestInstanceSummary {

	@Test
	public void testShouldSummariseTags() {
		List<Tag> tags = new LinkedList<>();
		
		tags.add(EnvironmentSetupForTests.createEc2Tag("tag1", "valueA"));
		tags.add(EnvironmentSetupForTests.createEc2Tag("tag2", "valueB"));
		tags.add(EnvironmentSetupForTests.createEc2Tag("tag3", "valueC"));

		InstanceSummary summary = new InstanceSummary("id", "10.0.0.99", tags);
		
		String results = summary.getTags();
		
		assertEquals("tag1=valueA,tag2=valueB,tag3=valueC", results);
	}
	
	@Test
	public void shouldReturnCorrectValues() {
		List<Tag> tags = new LinkedList<>();
		InstanceSummary summary = new InstanceSummary("id", "10.0.0.99", tags);

		assertEquals("id", summary.getInstance());
		assertEquals("10.0.0.99", summary.getPrivateIP());

	}
	
}
