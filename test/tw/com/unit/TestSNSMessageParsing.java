package tw.com.unit;

import static org.junit.Assert.*;

import org.junit.Test;

import tw.com.entity.StackNotification;

public class TestSNSMessageParsing {

	String stackEventSampleText = "StackName='temporaryStack'\nStackId='arn:aws:cloudformation:eu-west-1:619378453009:stack/temporaryStack/8c343e50-9eff-11e3-b6e7-506cf935a496'\nEventId='9afc1e30-9eff-11e3-b6e7-506cf935a496'\nLogicalResourceId='temporaryStack'\nPhysicalResourceId='arn:aws:cloudformation:eu-west-1:619378453009:stack/temporaryStack/8c343e50-9eff-11e3-b6e7-506cf935a496'\nResourceType='AWS::CloudFormation::Stack'\nTimestamp='2014-02-26T16:04:00.438Z'\nResourceStatus='CREATE_COMPLETE'\nResourceStatusReason=''\nResourceProperties=''\n";
	String resourceFailureText = "StackName='CfnAssistTestelb'\nStackId='arn:aws:cloudformation:eu-west-1:619378453009:stack/CfnAssistTestelb/3f12d2c0-af8b-11e3-a939-5088487db896'\nEventId='loadBalancer-CREATE_FAILED-1395249866157'\nLogicalResourceId='loadBalancer'\nPhysicalResourceId=''\nResourceType='AWS::ElasticLoadBalancing::LoadBalancer'\nTimestamp='2014-03-19T17:24:26.157Z'\nResourceStatus='CREATE_FAILED'\nResourceStatusReason='VPC vpc-38d62752 has no internet gateway'\nResourceProperties='{\"Listeners\":[{\"PolicyNames\":[],\"LoadBalancerPort\":\"80\",\"InstancePort\":\"8082\",\"Protocol\":\"HTTP\"}],\"Subnets\":[\"subnet-d8ded7ac\"],\"HealthCheck\":{\"Timeout\":\"5\",\"Interval\":\"15\",\"Target\":\"HTTP:8080/api/status\",\"HealthyThreshold\":\"2\",\"UnhealthyThreshold\":\"2\"}}'\n";

	// TODO Messaging based tests
	
	@Test
	public void testCanParseStackCreatedOkStatus() {
		StackNotification notification = StackNotification.parseNotificationMessage(stackEventSampleText);	
		
		assertEquals("temporaryStack", notification.getStackName());
		assertEquals("CREATE_COMPLETE", notification.getStatus());
		assertEquals("AWS::CloudFormation::Stack", notification.getResourceType());
		assertEquals("arn:aws:cloudformation:eu-west-1:619378453009:stack/temporaryStack/8c343e50-9eff-11e3-b6e7-506cf935a496", notification.getStackId());
	}
	
	@Test
	public void testCanParseResourceFailureEvent() {
		StackNotification notification = StackNotification.parseNotificationMessage(resourceFailureText);	
		
		assertEquals("CfnAssistTestelb", notification.getStackName());
		assertEquals("CREATE_FAILED", notification.getStatus());
		assertEquals("AWS::ElasticLoadBalancing::LoadBalancer", notification.getResourceType());
		assertEquals("arn:aws:cloudformation:eu-west-1:619378453009:stack/CfnAssistTestelb/3f12d2c0-af8b-11e3-a939-5088487db896", notification.getStackId());
		assertEquals("VPC vpc-38d62752 has no internet gateway",notification.getStatusReason());
	}
	
}
