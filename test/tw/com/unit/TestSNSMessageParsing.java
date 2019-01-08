package tw.com.unit;

import static org.junit.Assert.*;

import org.junit.Test;

import software.amazon.awssdk.services.cloudformation.model.StackStatus;
import tw.com.entity.StackNotification;

public class TestSNSMessageParsing {

	String stackEventSampleText = "StackName='temporaryStack'\nStackId='arn:aws:cloudformation:eu-west-1:619378453009:stack/temporaryStack/8c343e50-9eff-11e3-b6e7-506cf935a496'\nEventId='9afc1e30-9eff-11e3-b6e7-506cf935a496'\nLogicalResourceId='temporaryStack'\nPhysicalResourceId='arn:aws:cloudformation:eu-west-1:619378453009:stack/temporaryStack/8c343e50-9eff-11e3-b6e7-506cf935a496'\nResourceType='AWS::CloudFormation::Stack'\nTimestamp='2014-02-26T16:04:00.438Z'\nResourceStatus='CREATE_COMPLETE'\nResourceStatusReason=''\nResourceProperties=''\n";
	String resourceFailureText = "StackName='CfnAssistTestelb'\nStackId='arn:aws:cloudformation:eu-west-1:619378453009:stack/CfnAssistTestelb/3f12d2c0-af8b-11e3-a939-5088487db896'\nEventId='loadBalancer-CREATE_FAILED-1395249866157'\nLogicalResourceId='loadBalancer'\nPhysicalResourceId=''\nResourceType='AWS::ElasticLoadBalancing::LoadBalancer'\nTimestamp='2014-03-19T17:24:26.157Z'\nResourceStatus='CREATE_FAILED'\nResourceStatusReason='VPC vpc-38d62752 has no internet gateway'\nResourceProperties='{\"Listeners\":[{\"PolicyNames\":[],\"LoadBalancerPort\":\"80\",\"InstancePort\":\"8082\",\"Protocol\":\"HTTP\"}],\"Subnets\":[\"subnet-d8ded7ac\"],\"HealthCheck\":{\"Timeout\":\"5\",\"Interval\":\"15\",\"Target\":\"HTTP:8080/api/status\",\"HealthyThreshold\":\"2\",\"UnhealthyThreshold\":\"2\"}}'\n";
	String instanceInProgessAPIv1_10_0 = "StackId='arn:aws:cloudformation:eu-west-1:300752856189:stack/tramchesterB0Devservers/fdd1dc60-1362-11e5-b6e6-5001411350e0'\nTimestamp='2015-06-15T13:32:54.008Z'\nEventId='TechLabWebServerA-CREATE_IN_PROGRESS-2015-06-15T13:32:54.008Z'\nLogicalResourceId='TechLabWebServerA'\nNamespace='300752856189'\nResourceProperties='{\"IamInstanceProfile\":\"arn:aws:iam::300752856189:instance-profile/tramchester/Dev/tramchesterBDev009createInstanceIAMRole-InstanceProfile-132Q9VUV5W6KO\",\"ImageId\":\"ami-f0b11187\",\"Tags\":[{\"Value\":\"tramchesterWebA\",\"Key\":\"Name\"},{\"Value\":\"web\",\"Key\":\"CFN_ASSIST_TYPE\"}],\"SubnetId\":\"subnet-a3db63d4\",\"SecurityGroupIds\":[\"sg-4e601d2b\"],\"UserData\":\"I2luY2x1ZGUKaHR0cHM6Ly9zMy1ldS13ZXN0LTEuYW1hem9uYXdzLmNvbS90cmFtY2hlc3RlcjJkaXN0LzAvY2xvdWRJbml0LnR4dApodHRwczovL3MzLWV1LXdlc3QtMS5hbWF6b25hd3MuY29tL3RyYW1jaGVzdGVyMmRpc3QvMC9zZXR1cFRyYW1XZWJTZXJ2ZXIuc2gKIyBXQUlUVVJMPWh0dHBzOi8vY2xvdWRmb3JtYXRpb24td2FpdGNvbmRpdGlvbi1ldS13ZXN0LTEuczMtZXUtd2VzdC0xLmFtYXpvbmF3cy5jb20vYXJuJTNBYXdzJTNBY2xvdWRmb3JtYXRpb24lM0FldS13ZXN0LTElM0EzMDA3NTI4NTYxODklM0FzdGFjay90cmFtY2hlc3RlckIwRGV2c2VydmVycy9mZGQxZGM2MC0xMzYyLTExZTUtYjZlNi01MDAxNDExMzUwZTAvd2ViRG9uZVdhaXRIYW5kbGU/QVdTQWNjZXNzS2V5SWQ9QUtJQUpSQkZPRzZSUEdBU0RXR0EmRXhwaXJlcz0xNDM0NDYxNTcyJlNpZ25hdHVyZT1ZQ3RoTkFKMUZLNkFkdERPNzhTR24lMkZ6UXJ4WSUzRAojIEVOVj1EZXYKIyBBUlRJRkFDVFNVUkw9aHR0cHM6Ly9zMy1ldS13ZXN0LTEuYW1hem9uYXdzLmNvbS90cmFtY2hlc3RlcjJkaXN0CiMgQlVJTEQ9MAo=\",\"KeyName\":\"tramchester2\",\"InstanceType\":\"t2.micro\"}\n'\nResourceStatus='CREATE_IN_PROGRESS'\nResourceStatusReason=''\nResourceType='AWS::EC2::Instance'\nStackName='tramchesterB0Devservers'\n";
	String waitHandleInProgressAPIv1_10_0 =  "StackId='arn:aws:cloudformation:eu-west-1:300752856189:stack/tramchesterB0Devservers/fdd1dc60-1362-11e5-b6e6-5001411350e0'\nTimestamp='2015-06-15T13:32:52.223Z'\nEventId='webDoneWaitHandle-CREATE_IN_PROGRESS-2015-06-15T13:32:52.223Z'\nLogicalResourceId='webDoneWaitHandle'\nNamespace='300752856189'\nPhysicalResourceId='https://cloudformation-waitcondition-eu-west-1.s3-eu-west-1.amazonaws.com/arn%3Aaws%3Acloudformation%3Aeu-west-1%3A300752856189%3Astack/tramchesterB0Devservers/fdd1dc60-1362-11e5-b6e6-5001411350e0/webDoneWaitHandle?AWSAccessKeyId=AKIAJRBFOG6RPGASDWGA&Expires=1434461572&Signature=YCthNAJ1FK6AdtDO78SGn%2FzQrxY%3D'\nResourceProperties='{}\n'\nResourceStatus='CREATE_IN_PROGRESS'\nResourceStatusReason='Resource creation Initiated'\nResourceType='AWS::CloudFormation::WaitConditionHandle'\nStackName='tramchesterB0Devservers'\n";
	
	@Test
	public void testCanParseStackCreatedOkStatus() {
		StackNotification notification = StackNotification.parseNotificationMessage(stackEventSampleText);	
		
		assertEquals("temporaryStack", notification.getStackName());
		assertEquals(StackStatus.CREATE_COMPLETE, notification.getStatus());
		assertEquals("AWS::CloudFormation::Stack", notification.getResourceType());
		assertEquals("arn:aws:cloudformation:eu-west-1:619378453009:stack/temporaryStack/8c343e50-9eff-11e3-b6e7-506cf935a496", notification.getStackId());
	}
	
	@Test
	public void testCanParseStackInProgessStatus() {
		StackNotification notification = StackNotification.parseNotificationMessage(instanceInProgessAPIv1_10_0);	
		
		assertEquals("tramchesterB0Devservers", notification.getStackName());
		assertEquals(StackStatus.CREATE_IN_PROGRESS, notification.getStatus());
		assertEquals("AWS::EC2::Instance", notification.getResourceType());
		assertEquals("arn:aws:cloudformation:eu-west-1:300752856189:stack/tramchesterB0Devservers/fdd1dc60-1362-11e5-b6e6-5001411350e0", notification.getStackId());
	}
	
	@Test
	public void testCanParseWaitHandleInProgessStatus() {
		StackNotification notification = StackNotification.parseNotificationMessage(waitHandleInProgressAPIv1_10_0);	
		
		assertEquals("tramchesterB0Devservers", notification.getStackName());
		assertEquals(StackStatus.CREATE_IN_PROGRESS, notification.getStatus());
		assertEquals("AWS::CloudFormation::WaitConditionHandle", notification.getResourceType());
		assertEquals("arn:aws:cloudformation:eu-west-1:300752856189:stack/tramchesterB0Devservers/fdd1dc60-1362-11e5-b6e6-5001411350e0", notification.getStackId());
	}
	
	@Test
	public void testCanParseResourceFailureEvent() {
		StackNotification notification = StackNotification.parseNotificationMessage(resourceFailureText);	
		
		assertEquals("CfnAssistTestelb", notification.getStackName());
		assertEquals(StackStatus.CREATE_FAILED, notification.getStatus());
		assertEquals("AWS::ElasticLoadBalancing::LoadBalancer", notification.getResourceType());
		assertEquals("arn:aws:cloudformation:eu-west-1:619378453009:stack/CfnAssistTestelb/3f12d2c0-af8b-11e3-a939-5088487db896", notification.getStackId());
		assertEquals("VPC vpc-38d62752 has no internet gateway",notification.getStatusReason());
	}
	
}
