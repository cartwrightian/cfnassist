package tw.com;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import software.amazon.awssdk.services.ec2.model.AvailabilityZone;
import software.amazon.awssdk.services.ec2.model.Vpc;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;
import tw.com.repository.CloudFormRepository;
import tw.com.repository.CloudRepository;
import tw.com.repository.VpcRepository;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class UpdateStackExpectations extends EasyMockSupport {
    protected static final String VPC_ID = "vpcId";

    protected CloudFormRepository cfnRepository;
    protected VpcRepository vpcRepository;
    protected MonitorStackEvents monitor;
    protected CloudRepository cloudRepository;
    protected ProjectAndEnv projectAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
    private Map<String, AvailabilityZone> zones = new HashMap<>();

    protected StackNameAndId setUpdateExpectations(String stackName, String filename,
                                                   List<TemplateParameter> templateParameters,
                                                   Collection<Parameter> parameters)
            throws CfnAssistException, InterruptedException, IOException {
        String stackId = "stackId";
        Stack stack = new Stack().withStackId(stackId);
        StackNameAndId stackNameAndId = new StackNameAndId(stackName, stackId);

        String contents = EnvironmentSetupForTests.loadFile(filename);
        EasyMock.expect(vpcRepository.getCopyOfVpc(projectAndEnv)).andReturn(Vpc.builder().vpcId(VPC_ID).build());
        EasyMock.expect(cfnRepository.validateStackTemplate(contents)).andReturn(templateParameters);

        EasyMock.expect(cfnRepository.updateStack(contents, parameters, monitor, stackName)).andReturn(stackNameAndId);
        EasyMock.expect(monitor.waitForUpdateFinished(stackNameAndId)).andReturn(StackStatus.UPDATE_COMPLETE.toString());
        EasyMock.expect(cfnRepository.updateSuccess(stackNameAndId)).andReturn(stack);
        EasyMock.expect(cloudRepository.getZones()).andReturn(zones);
        return stackNameAndId;
    }
}
