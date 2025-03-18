package tw.com.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*;
import tw.com.EnvironmentSetupForTests;
import tw.com.providers.LoadBalancerClientV2;

import java.util.*;

import static java.lang.Thread.sleep;
import static org.junit.Assert.*;
import static tw.com.EnvironmentSetupForTests.MAIN_VPC_FOR_TEST;

public class TestLoadBalancerClientV2 {
    public static final String CFNASSIST_TEST_TARGET_GROUP = "cfnassistTestTargetGroup";

    private static final int INSTANCE_PORT = 9998;

    private static TargetGroup targetGroup;
    private static ElasticLoadBalancingV2Client awsELBClient;
    private static Ec2Client awsEC2Client;
    private static Instance instance;

    private LoadBalancerClientV2 client;

    @BeforeAll
    public static void onceBeforeSuiteOfTestsRun() {
        awsELBClient = EnvironmentSetupForTests.createELBClientV2();
        awsEC2Client = EnvironmentSetupForTests.createEC2Client();

        createTargetGroup();
        instance = EnvironmentSetupForTests.createSimpleInstance(awsEC2Client);
    }

    @AfterAll
    public static void onceAfterAllTestsHaveRun() {
        deleteTargetGroup();
        deleteInstance();
    }

    @BeforeEach
    public void beforeEachTestRuns() {
        client = new LoadBalancerClientV2(awsELBClient);
    }

    @AfterEach
    public void afterEachTestRuns() {
        deregisterAnyInstances();
    }

    @Test
    public void shouldDescribeTargetGroups() {
        List<TargetGroup> groups = client.describerTargetGroups();
        assertFalse(groups.isEmpty());

        List<TargetGroup> matchVPC = groups.stream().filter(targetGroup -> MAIN_VPC_FOR_TEST.equals(targetGroup.vpcId())).toList();
        assertEquals(1, matchVPC.size());

        TargetGroup result = matchVPC.get(0);
        assertEquals(CFNASSIST_TEST_TARGET_GROUP, result.targetGroupName());
    }

    @Test
    public void shouldDescribeInstances() throws InterruptedException {
        List<TargetDescription> initial = client.describeTargets(targetGroup, EnumSet.allOf(TargetHealthStateEnum.class));
        assertTrue(initial.isEmpty());

        waitForInstanceAvailable();
        registerInstance();

        List<TargetDescription> results = client.describeTargets(targetGroup, EnumSet.of(TargetHealthStateEnum.UNUSED));
        assertFalse(results.isEmpty());
        assertEquals(instance.instanceId(), results.get(0).id());
    }

    @Test
    public void shouldRegisterInstance() throws InterruptedException {
        List<TargetDescription> initial = client.describeTargets(targetGroup, EnumSet.allOf(TargetHealthStateEnum.class));
        assertTrue(initial.toString(), initial.isEmpty());

        waitForInstanceAvailable();

        client.registerInstance(targetGroup, instance.instanceId(), INSTANCE_PORT);

        // will be unused as target group not associated with a LB
        List<TargetDescription> results = client.describeTargets(targetGroup, EnumSet.of(TargetHealthStateEnum.UNUSED));
        assertFalse(results.isEmpty());

        assertEquals(instance.instanceId(), results.get(0).id());
    }

    @Test
    public void shouldDeregisterInstance() throws InterruptedException {
        registerInstance();

        client.deregisterInstance(targetGroup, instance.instanceId(), INSTANCE_PORT);

        DescribeTargetHealthRequest describeTargetHealthRequest = DescribeTargetHealthRequest.builder().
                targetGroupArn(targetGroup.targetGroupArn()).build();

        int countDown = 10;
        boolean dereg = false;
        while (!dereg && countDown>0) {
            sleep(500);
            DescribeTargetHealthResponse health = awsELBClient.describeTargetHealth(describeTargetHealthRequest);
            if (health.hasTargetHealthDescriptions()) {
                List<TargetHealthDescription> healthDescriptions = health.targetHealthDescriptions();
                dereg = healthDescriptions.isEmpty();
            } else {
                dereg = true;
            }
            countDown--;
        }
        assertTrue("did not unregister", dereg);
    }


    private static void waitForInstanceAvailable() throws InterruptedException {
        // cannot add an instance to a target group unless in running state
        final DescribeInstancesRequest request = DescribeInstancesRequest.builder().instanceIds(instance.instanceId()).build();
        int countDown = 60; // hopefully not always taking this long(!)
        InstanceState state;
        do {
            sleep(1000);
            countDown--;
            DescribeInstancesResponse results = awsEC2Client.describeInstances(request);
            Optional<Instance> maybeState = results.reservations().stream().flatMap(reservation -> reservation.instances().stream()).
                    filter(found -> found.instanceId().equals(instance.instanceId())).findFirst();
            assertFalse(maybeState.isEmpty());

            state = maybeState.get().state();
        } while (state.name()!=InstanceStateName.RUNNING && countDown>0);

        if (state.name()!=InstanceStateName.RUNNING) {
            fail("instance " + instance.instanceId() + " never reach running, got " + state.name());
        }

    }

    private static void registerInstance() throws InterruptedException {
        waitForInstanceAvailable();

        TargetDescription description = TargetDescription.builder().id(instance.instanceId()).port(INSTANCE_PORT).build();
        RegisterTargetsRequest request = RegisterTargetsRequest.builder().
                targets(description).
                targetGroupArn(targetGroup.targetGroupArn()).
                build();
        awsELBClient.registerTargets(request);

    }

    private static void createTargetGroup() {

        CreateTargetGroupRequest targetGroupRequest= CreateTargetGroupRequest.builder().
                targetType(TargetTypeEnum.INSTANCE).
                name(CFNASSIST_TEST_TARGET_GROUP).
                vpcId(MAIN_VPC_FOR_TEST).
                protocol(ProtocolEnum.HTTP).port(9999).
                build();

        CreateTargetGroupResponse results = awsELBClient.createTargetGroup(targetGroupRequest);
        assertTrue(results.hasTargetGroups());
        List<TargetGroup> created = results.targetGroups();
        assertEquals(1, created.size());
        targetGroup = created.get(0);

    }

    private static void deleteTargetGroup() {

        if (targetGroup!=null) {

            deregisterAnyInstances();

            DeleteTargetGroupRequest deleteRequest = DeleteTargetGroupRequest.builder().targetGroupArn(targetGroup.targetGroupArn()).build();
            awsELBClient.deleteTargetGroup(deleteRequest);
            targetGroup=null;
        }

    }

    private static void deregisterAnyInstances() {
        DescribeTargetHealthResponse found = getGroupHealth();
        if (found.hasTargetHealthDescriptions()) {
            List<TargetDescription> targets = found.targetHealthDescriptions().stream().map(TargetHealthDescription::target).toList();
            if (!targets.isEmpty()) {
                DeregisterTargetsRequest deregisterTargetsRequest = DeregisterTargetsRequest.builder().
                        targetGroupArn(targetGroup.targetGroupArn()).
                        targets(targets).build();
                awsELBClient.deregisterTargets(deregisterTargetsRequest);

                DescribeTargetHealthResponse shouldNotFind = getGroupHealth();
                if (shouldNotFind.hasTargetHealthDescriptions()) {
                    assertTrue("unexpected " + shouldNotFind, shouldNotFind.targetHealthDescriptions().isEmpty());
                }
            }
        }
    }

    private static DescribeTargetHealthResponse getGroupHealth() {
        DescribeTargetHealthRequest request = DescribeTargetHealthRequest.builder().targetGroupArn(targetGroup.targetGroupArn()).build();
        return awsELBClient.describeTargetHealth(request);
    }

    private static void deleteInstance() {
        if (instance!=null) {
            awsEC2Client.terminateInstances(TerminateInstancesRequest.builder().instanceIds(instance.instanceId()).build());
            instance=null;
        }
    }
}
