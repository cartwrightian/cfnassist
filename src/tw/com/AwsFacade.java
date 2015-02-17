package tw.com;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.entity.DeletionsPending;
import tw.com.entity.InstanceSummary;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.SearchCriteria;
import tw.com.entity.StackEntry;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.BadVPCDeltaIndexException;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.DuplicateStackException;
import tw.com.exceptions.InvalidStackParameterException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.TagsAlreadyInit;
import tw.com.exceptions.TooManyELBException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.exceptions.WrongStackStatus;
import tw.com.parameters.AutoDiscoverParams;
import tw.com.parameters.CfnBuiltInParams;
import tw.com.parameters.EnvVarParams;
import tw.com.parameters.ParameterFactory;
import tw.com.parameters.PopulatesParameters;
import tw.com.providers.ProvidesCurrentIp;
import tw.com.repository.CloudFormRepository;
import tw.com.repository.CloudRepository;
import tw.com.repository.ELBRepository;
import tw.com.repository.VpcRepository;

import com.amazonaws.services.cloudformation.model.Output;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;

public class AwsFacade {

	private static final String DELTA_EXTENSTION = ".delta";

	private static final Logger logger = LoggerFactory.getLogger(AwsFacade.class);
	
	public static final String ENVIRONMENT_TAG = "CFN_ASSIST_ENV";
	public static final String PROJECT_TAG = "CFN_ASSIST_PROJECT"; 
	public static final String INDEX_TAG = "CFN_ASSIST_DELTA";
	public static final String BUILD_TAG = "CFN_ASSIST_BUILD_NUMBER";
	public static final String TYPE_TAG = "CFN_ASSIST_TYPE";
	public static final String COMMENT_TAG = "CFN_COMMENT";
	public static final String ENV_S3_BUCKET = "CFN_ASSIST_BUCKET";
	
	private static final String PARAMETER_STACKNAME = "stackname";
	
	private VpcRepository vpcRepository;
	private CloudFormRepository cfnRepository;
	private ELBRepository elbRepository;
	private CloudRepository cloudRepository;

	private MonitorStackEvents monitor;

	private String commentTag="";


	public AwsFacade(MonitorStackEvents monitor, CloudFormRepository cfnRepository, VpcRepository vpcRepository, ELBRepository elbRepository, CloudRepository cloudRepository) {
		this.monitor = monitor;
		this.cfnRepository = cfnRepository;
		this.vpcRepository = vpcRepository;
		this.elbRepository = elbRepository;
		this.cloudRepository = cloudRepository;
	}
	
	public void setCommentTag(String commentTag) {
		this.commentTag = commentTag;		
	}

	public List<TemplateParameter> validateTemplate(String templateBody) {
		List<TemplateParameter> parameters = cfnRepository.validateStackTemplate(templateBody);
		logger.info(String.format("Found %s parameters", parameters.size()));
		return parameters;
	}

	public List<TemplateParameter> validateTemplate(File file) throws FileNotFoundException, IOException {
		logger.info("Validating template and discovering parameters for file " + file.getAbsolutePath());
		String contents = loadFileContents(file);
		return validateTemplate(contents);
	}
	
	public StackNameAndId applyTemplate(String filename, ProjectAndEnv projAndEnv) throws FileNotFoundException, CfnAssistException, IOException, InterruptedException {
		File file = new File(filename);
		return applyTemplate(file, projAndEnv);
	}
	
	public StackNameAndId applyTemplate(File file, ProjectAndEnv projAndEnv)
			throws FileNotFoundException, IOException,
			InvalidStackParameterException, InterruptedException, CfnAssistException {
		return applyTemplate(file, projAndEnv, new HashSet<Parameter>());
	}
	
	// TODO version that we can pass vpc into so when doing batch operations not repeatedly getting
	public StackNameAndId applyTemplate(File file, ProjectAndEnv projAndEnv, Collection<Parameter> userParameters) throws 
		FileNotFoundException, IOException, InterruptedException, CfnAssistException {
		logger.info(String.format("Applying template %s for %s", file.getAbsoluteFile(), projAndEnv));	
		Vpc vpcForEnv = findVpcForEnv(projAndEnv);
		List<TemplateParameter> declaredParameters = validateTemplate(file);

		List<PopulatesParameters> populators = new LinkedList<PopulatesParameters>();
		populators.add(new CfnBuiltInParams(vpcForEnv.getVpcId()));
		populators.add(new AutoDiscoverParams(file, vpcRepository, cfnRepository));	
		populators.add(new EnvVarParams());
		ParameterFactory parameterFactory= new ParameterFactory(populators);
		
		String contents = loadFileContents(file);
		
		if (isDelta(file)) {
			logger.info("Request to update a stack, filename is " + file.getAbsolutePath());
			return updateStack(projAndEnv, userParameters, declaredParameters, contents, parameterFactory);
		} else {
			return createStack(file, projAndEnv, userParameters, declaredParameters, contents, parameterFactory);
		}	
	}

	private boolean isDelta(File file) {
		return (file.getName().contains(DELTA_EXTENSTION));
	}

	private StackNameAndId updateStack(ProjectAndEnv projAndEnv,
			Collection<Parameter> userParameters, List<TemplateParameter> declaredParameters, String contents, ParameterFactory parameterFactory) throws FileNotFoundException, InvalidStackParameterException, IOException, InterruptedException, WrongNumberOfStacksException, NotReadyException, WrongStackStatus, CannotFindVpcException {
		
		Collection<Parameter> parameters = parameterFactory.createRequiredParameters(projAndEnv, userParameters, declaredParameters);
		String stackName = findStackToUpdate(declaredParameters, projAndEnv);
		
		StackNameAndId id = cfnRepository.updateStack(contents, parameters, monitor, stackName);
		
		try {
			monitor.waitForUpdateFinished(id);
		} catch (WrongStackStatus stackFailedToCreate) {
			logger.error("Failed to create stack",stackFailedToCreate);
			cfnRepository.updateRepositoryFor(id);
			throw stackFailedToCreate;
		}
		Stack createdStack = cfnRepository.updateRepositoryFor(id);
		createOutputTags(createdStack, projAndEnv);
		return id;		
	}

	private String findStackToUpdate(List<TemplateParameter> declaredParameters, ProjectAndEnv projAndEnv) throws InvalidStackParameterException {
		for(TemplateParameter param : declaredParameters) {
			if (param.getParameterKey().equals(PARAMETER_STACKNAME)) {
				String defaultValue = param.getDefaultValue();
				if ((defaultValue!=null) && (!defaultValue.isEmpty())) {
					return createName(projAndEnv, defaultValue);
				} else {
					logger.error(String.format("Found parameter %s but no default given",PARAMETER_STACKNAME));
					throw new InvalidStackParameterException(PARAMETER_STACKNAME); 
				}
			}
		}
		logger.error(String.format("Unable to find parameter %s which is required to peform a stack update", PARAMETER_STACKNAME));
		throw new InvalidStackParameterException(PARAMETER_STACKNAME);
	}

	private StackNameAndId createStack(File file, ProjectAndEnv projAndEnv,
			Collection<Parameter> userParameters,
			List<TemplateParameter> declaredParameters, String contents, ParameterFactory parameterFactory)
			throws CfnAssistException, InterruptedException, FileNotFoundException, IOException {
		String stackName = createStackName(file, projAndEnv);
		logger.info("Stackname is " + stackName);
		
		handlePossibleRollback(stackName);
						
		Collection<Parameter> parameters = parameterFactory.createRequiredParameters(projAndEnv, userParameters, declaredParameters);

		StackNameAndId id = cfnRepository.createStack(projAndEnv, contents, stackName, parameters, monitor, commentTag);
		
		try {
			monitor.waitForCreateFinished(id);
		} catch (WrongStackStatus stackFailedToCreate) {
			logger.error("Failed to create stack",stackFailedToCreate);
			cfnRepository.updateRepositoryFor(id);
			throw stackFailedToCreate;
		}
		Stack createdStack = cfnRepository.updateRepositoryFor(id);
		createOutputTags(createdStack, projAndEnv);
		return id;
	}

	private void createOutputTags(Stack createdStack, ProjectAndEnv projAndEnv) {
		List<Output> outputs = createdStack.getOutputs();
		for(Output output  : outputs) {
			if (shouldCreateTag(output.getDescription())) {
				logger.info("Should create output tag for " + output.toString());
				vpcRepository.setVpcTag(projAndEnv, output.getOutputKey(), output.getOutputValue());
			}
		}
	}

	private boolean shouldCreateTag(String description) {
		return description.equals(PopulatesParameters.CFN_TAG_ON_OUTPUT);
	}

	private void handlePossibleRollback(String stackName)
			throws WrongNumberOfStacksException, NotReadyException,
			WrongStackStatus, InterruptedException, DuplicateStackException {
		String currentStatus = cfnRepository.getStackStatus(stackName);
		if (currentStatus.length()!=0) {
			logger.warn("Stack already exists: " + stackName);
			StackNameAndId stackId = cfnRepository.getStackNameAndId(stackName);
			if (isRollingBack(stackId,currentStatus)) {
				logger.warn("Stack is rolled back so delete it and recreate " + stackId);
				cfnRepository.deleteStack(stackName);
			} else {
				logger.error("Stack exists and is not rolled back, cannot create another stack with name:" +stackName);
				throw new DuplicateStackException(stackName);
			}
		}
	}

	private boolean isRollingBack(StackNameAndId id, String currentStatus) throws NotReadyException, WrongNumberOfStacksException, WrongStackStatus, InterruptedException {
		if (currentStatus.equals(StackStatus.ROLLBACK_IN_PROGRESS.toString())) {
			monitor.waitForRollbackComplete(id);
			return true;
		} else if (currentStatus.equals(StackStatus.ROLLBACK_COMPLETE.toString())) {
			return true;
		}
		return false;
	}

	private Vpc findVpcForEnv(ProjectAndEnv projAndEnv) throws InvalidStackParameterException {
		Vpc vpcForEnv = vpcRepository.getCopyOfVpc(projAndEnv);
		if (vpcForEnv==null) {
			logger.error("Unable to find VPC tagged as environment:" + projAndEnv);
			throw new InvalidStackParameterException(projAndEnv.toString());
		}
		logger.info(String.format("Found VPC %s corresponding to %s", vpcForEnv.getVpcId(), projAndEnv));
		return vpcForEnv;
	}


	public String createStackName(File file, ProjectAndEnv projAndEnv) {
		// note: aws only allows [a-zA-Z][-a-zA-Z0-9]* in stacknames
		String filename = file.getName();
		String name = FilenameUtils.removeExtension(filename);
		if (name.endsWith(DELTA_EXTENSTION)) {
			logger.debug("Detected delta filename, remove additional extension");
			name = FilenameUtils.removeExtension(name);
		}
		return createName(projAndEnv, name);
	}

	private String createName(ProjectAndEnv projAndEnv, String name) {
		String project = projAndEnv.getProject();
		String env = projAndEnv.getEnv();
		if (projAndEnv.hasBuildNumber()) {
			return project+projAndEnv.getBuildNumber()+env+name;
		} else {
			return project+env+name;
		}
	}
	
	public void deleteStackFrom(File templateFile, ProjectAndEnv projectAndEnv) {
		String stackName = createStackName(templateFile, projectAndEnv);
		deleteStack(stackName, projectAndEnv);
	}
	
	private void deleteStack(String stackName, ProjectAndEnv projectAndEnv) {
		StackNameAndId stackId;
		try {
			stackId = cfnRepository.getStackNameAndId(stackName);
		} catch (WrongNumberOfStacksException e) {
			logger.warn("Unable to find stack " + stackName);
			return;
		}
				
		logger.info("Found ID for stack: " + stackId);
		cfnRepository.deleteStack(stackName);
		
		try {
			monitor.waitForDeleteFinished(stackId);
		} catch (WrongNumberOfStacksException | NotReadyException
				| WrongStackStatus | InterruptedException e) {
			logger.error("Unable to delete stack " + stackName);
			logger.error(e.getMessage());
			logger.error(e.getStackTrace().toString());
		}
		
	}

	private String loadFileContents(File file) throws IOException {
		return FileUtils.readFileToString(file, Charset.defaultCharset());
	}

	public ArrayList<StackNameAndId> applyTemplatesFromFolder(String folderPath,
			ProjectAndEnv projAndEnv) throws InvalidStackParameterException,
			FileNotFoundException, IOException, CfnAssistException,
			InterruptedException {
		return applyTemplatesFromFolder(folderPath, projAndEnv, new LinkedList<Parameter>());
	}

	public ArrayList<StackNameAndId> applyTemplatesFromFolder(String folderPath,
			ProjectAndEnv projAndEnv, Collection<Parameter> cfnParams) throws InvalidStackParameterException, FileNotFoundException, IOException, InterruptedException, CfnAssistException {
		ArrayList<StackNameAndId> updatedStacks = new ArrayList<>();
		File folder = validFolder(folderPath);
		logger.info("Invoking templates from folder: " + folderPath);
		List<File> files = loadFiles(folder);
		
		logger.info("Attempt to Validate all files");
		for(File file : files) {
			validateTemplate(file);
		}
		
		int highestAppliedDelta = getDeltaIndex(projAndEnv);
		logger.info("Current index is " + highestAppliedDelta);
		
		logger.info("Validation ok, apply template files");
		for(File file : files) {
			int deltaIndex = extractIndexFrom(file);
			if (deltaIndex>highestAppliedDelta) {
				logger.info(String.format("Apply template file: %s, index is %s",file.getAbsolutePath(), deltaIndex));
				StackNameAndId stackId = applyTemplate(file, projAndEnv, cfnParams);
				logger.info("Create/Updated stack " + stackId);
				updatedStacks.add(stackId); 
				setDeltaIndex(projAndEnv, deltaIndex);
			} else {
				logger.info(String.format("Skipping file %s as already applied, index was %s", file.getAbsolutePath(), deltaIndex));
			}		
		}
		
		logger.info("All templates successfully invoked");
		
		return updatedStacks;
	}

	private List<File> loadFiles(File folder) {
		FilenameFilter jsonFilter = new JsonExtensionFilter();
		File[] files = folder.listFiles(jsonFilter);
		Arrays.sort(files); // place in lexigraphical order
		return Arrays.asList(files);
	}

	private File validFolder(String folderPath)
			throws InvalidStackParameterException {
		File folder = new File(folderPath);
		if (!folder.isDirectory()) {
			throw new InvalidStackParameterException(folderPath + " is not a directory");
		}
		return folder;
	}
	
	public List<String> stepbackLastChange(String folderPath, ProjectAndEnv projAndEnv) throws CfnAssistException {
		DeletionsPending pending = new DeletionsPending();
		File folder = validFolder(folderPath);
		List<File> files = loadFiles(folder);
		Collections.reverse(files); // delete in reverse direction
		
		int highestAppliedDelta = getDeltaIndex(projAndEnv);
		logger.info("Current delta is " + highestAppliedDelta);	
		
		File toDelete = findFileToStepBack(files, highestAppliedDelta);
		if (toDelete==null) {
			logger.warn("No suitable stack/file found to step back from");
			return new LinkedList<String>();
		}
		
		logger.info("Need to step back for file " + toDelete.getAbsolutePath());
		int deltaIndex = extractIndexFrom(toDelete);
		SetsDeltaIndex setsDeltaIndex = vpcRepository.getSetsDeltaIndexFor(projAndEnv);	
		String stackName = createStackName(toDelete, projAndEnv);

		if (isDelta(toDelete)) {
			logger.warn("Rolling back a stack change/delta does nothing except update delta index on VPC");
			setsDeltaIndex.setDeltaIndex(deltaIndex-1);	
			return new LinkedList<String>();
		}
		else {
			StackNameAndId id = cfnRepository.getStackNameAndId(stackName); // important to get id's before deletion request, may throw otherwise
			cfnRepository.deleteStack(stackName);
			pending.add(deltaIndex,id);
			return monitor.waitForDeleteFinished(pending, setsDeltaIndex);
		}	
	}

	private File findFileToStepBack(List<File> files, int highestAppliedDelta) {
		File toDelete = null;
		for(File file : files) {
			int deltaIndex = extractIndexFrom(file);
			if (deltaIndex==highestAppliedDelta) {
				toDelete = file;
				break;
			}
		}
		return toDelete;
	}
	
	public List<String> rollbackTemplatesInFolder(String folderPath, ProjectAndEnv projAndEnv) throws InvalidStackParameterException, CfnAssistException {
		DeletionsPending pending = new DeletionsPending();
		File folder = validFolder(folderPath);
		List<File> files = loadFiles(folder);
		Collections.reverse(files); // delete in reverse direction
		
		int highestAppliedDelta = getDeltaIndex(projAndEnv);
		logger.info("Current delta is " + highestAppliedDelta);
		
		File fileMatchingIndex = findFileToStepBack(files,highestAppliedDelta);
		if (fileMatchingIndex==null) {
			throw new CfnAssistException("Cannot find file that corresponds to current index, folder was " + folderPath);
		}
		
		for(File file : files) {
			if (!isDelta(file)) {
				int deltaIndex = extractIndexFrom(file);
				String stackName = createStackName(file, projAndEnv);
				if (deltaIndex>highestAppliedDelta) {
					logger.warn(String.format("Not deleting %s as index %s is greater than current delta %s", stackName, deltaIndex, highestAppliedDelta));
				} else {			
					logger.info(String.format("About to request deletion of stackname %s", stackName));
					StackNameAndId id = cfnRepository.getStackNameAndId(stackName); // important to get id's before deletion request, may throw otherwise
					cfnRepository.deleteStack(stackName);
					pending.add(deltaIndex,id);
				}
			} else {
				logger.info(String.format("Skipping file %s as it is a stack update file",file.getAbsolutePath()));
			}
		}
		SetsDeltaIndex setsDeltaIndex = vpcRepository.getSetsDeltaIndexFor(projAndEnv);	
		return monitor.waitForDeleteFinished(pending, setsDeltaIndex);
	}

	private int extractIndexFrom(File file) {
		StringBuilder indexPart = new StringBuilder();
		String name = file.getName();
		
		int i = 0;
		while(Character.isDigit(name.charAt(i)))  {
			indexPart.append(name.charAt(i));
			i++;
		}
		
		return Integer.parseInt(indexPart.toString());
	}

	public void resetDeltaIndex(ProjectAndEnv projAndEnv) throws CannotFindVpcException {
		vpcRepository.setVpcIndexTag(projAndEnv, "0");
	}

	public void setDeltaIndex(ProjectAndEnv projAndEnv, Integer index) throws CannotFindVpcException {
		vpcRepository.setVpcIndexTag(projAndEnv, index.toString());
	}

	public int getDeltaIndex(ProjectAndEnv projAndEnv) throws CfnAssistException {
		String tag = vpcRepository.getVpcIndexTag(projAndEnv);
		int result = -1;
		try {
			result = Integer.parseInt(tag);
			return result;
		}
		catch(NumberFormatException exception) {
			logger.error("Could not parse the delta index: " + tag);
			throw new BadVPCDeltaIndexException(tag);
		}
	}

	public void initEnvAndProjectForVPC(String targetVpcId, ProjectAndEnv projectAndEnvToSet) throws CfnAssistException {
		Vpc result = vpcRepository.getCopyOfVpc(projectAndEnvToSet);
		if (result!=null) {
			logger.error(String.format("Managed to find vpc already present with tags %s and id %s", projectAndEnvToSet, result.getVpcId()));
			throw new TagsAlreadyInit(targetVpcId);
		}	
		vpcRepository.initAllTags(targetVpcId, projectAndEnvToSet);	
	}
	
	public List<StackEntry> listStacks(ProjectAndEnv projectAndEnv) {
		if (projectAndEnv.hasEnv()) {
			return cfnRepository.getStacks(projectAndEnv.getEnvTag());
		}
		return cfnRepository.getStacks();
	}

	public void tidyNonLBAssocStacks(File file, ProjectAndEnv projectAndEnv, String typeTag) throws InvalidStackParameterException, TooManyELBException {
		String filename = file.getName();
		String name = FilenameUtils.removeExtension(filename);
		if (name.endsWith(DELTA_EXTENSTION)) {
			throw new InvalidStackParameterException("Cannot invoke for .delta files");
		}
		logger.info(String.format("Checking for non-instance-associated stacks for %s and name %s. Will use %s:%s if >1 ELB",
				projectAndEnv, name, AwsFacade.TYPE_TAG,typeTag));

		List<StackEntry> candidateStacks = cfnRepository.getStacksMatching(projectAndEnv.getEnvTag(), name);
		
		if (candidateStacks.isEmpty()) {
			logger.warn("No matching stacks found for possible deletion");
			return;
		} 
		
		List<String> regInstanceIds = currentlyRegisteredInstanceIDs(projectAndEnv, typeTag);
	
		List<StackEntry> toDelete = new LinkedList<StackEntry>();
		for(StackEntry entry : candidateStacks) {
			List<String> ids = cfnRepository.getInstancesFor(entry.getStackName());
			if (ids.isEmpty()) {
				logger.warn(String.format("Stack %s has no instances at all, will not be deleted", entry.getStackName()));
			} else {
				if (containsAny(regInstanceIds,ids)) {
					logger.info(String.format("Stack %s contains instances registered to LB, will not be deleted", entry.getStackName()));
				} else {
					logger.warn(String.format("Stack %s has no registered instances, will be deleted", entry.getStackName()));
					toDelete.add(entry);
				}
			}
		}
		
		if (toDelete.isEmpty()) {
			logger.info("No stacks to delete");
		} else {
			for(StackEntry delete : toDelete) {
				logger.warn("Deleting stack " + delete.getStackName());
				deleteStack(delete.getStackName(), projectAndEnv);
			}
		}
		
	}

	private List<String> currentlyRegisteredInstanceIDs(
			ProjectAndEnv projectAndEnv, String typeTag)
			throws TooManyELBException {
		List<Instance> registeredInstances = elbRepository.findInstancesAssociatedWithLB(projectAndEnv, typeTag);
		List<String> regInstanceIds = new LinkedList<String>();
		if (registeredInstances.isEmpty()) {
			logger.warn("No instances associated with ELB");
		} else {
			for(Instance ins : registeredInstances) {
				regInstanceIds.add(ins.getInstanceId());
			}
		}
		return regInstanceIds;
	}
	
	public boolean containsAny(List<String> first, List<String> second) {
		for(String candidate : second) {
			if (first.contains(candidate)) {
				return true;
			}
		}
		return false;
	}

	public List<Instance> updateELBToInstancesMatchingBuild(ProjectAndEnv projectAndEnv, String typeTag) throws CfnAssistException {
		logger.info(String.format("Update instances for ELB to match %s and type tag %s", projectAndEnv, typeTag));
		return elbRepository.updateInstancesMatchingBuild(projectAndEnv, typeTag);	
	}

	public void whitelistCurrentIpForPortToElb(ProjectAndEnv projectAndEnv, String type, ProvidesCurrentIp hasCurrentIp, Integer port) throws CfnAssistException {		
		InetAddress address = hasCurrentIp.getCurrentIp();
		logger.info(String.format("Request to add %s port:%s for elb on %s of type %s", address.getHostAddress(), port, projectAndEnv, type));
		String groupId = getSecGroupIdForELB(projectAndEnv, type);
		logger.info("Found sec group: " + groupId);
		cloudRepository.updateAddIpAndPortToSecGroup(groupId, address, port);
	}

	public void blacklistCurrentIpForPortToElb(ProjectAndEnv projectAndEnv, String type, ProvidesCurrentIp hasCurrentIp, Integer port) throws CfnAssistException {
		InetAddress address = hasCurrentIp.getCurrentIp();
		logger.info(String.format("Request to remove %s port:%s for elb on %s of type %s", address.getHostAddress(), port, projectAndEnv, type));
		String groupId = getSecGroupIdForELB(projectAndEnv, type);
		logger.info("Found sec group: " + groupId);
		cloudRepository.updateRemoveIpAndPortFromSecGroup(groupId, address, port);
	}
	
	private String getSecGroupIdForELB(ProjectAndEnv projectAndEnv, String type)
			throws TooManyELBException, CfnAssistException {
		LoadBalancerDescription elb = elbRepository.findELBFor(projectAndEnv, type);
		if (elb==null) {
			throw new CfnAssistException("Did not find ELB for current vpc");
		}
		
		List<String> groups = elb.getSecurityGroups();
		
		if (groups.size()>1) {
			throw new CfnAssistException("Found multiple security groups associated with elb " + elb.getDNSName());
		}
		
		String groupId = groups.get(0);
		return groupId;
	}

	public List<InstanceSummary> listInstances(SearchCriteria searchCriteria) throws CfnAssistException {
		List<InstanceSummary> result = new LinkedList<>();
		List<String> instanceIds = cfnRepository.getAllInstancesFor(searchCriteria);
		
		for(String id: instanceIds) {
			com.amazonaws.services.ec2.model.Instance instance = cloudRepository.getInstanceById(id);
			InstanceSummary summary = new InstanceSummary(id, instance.getPrivateIpAddress(), instance.getTags());
			result.add(summary);
		}
		
		return result;
	}



}
