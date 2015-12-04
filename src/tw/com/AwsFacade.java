package tw.com;

import com.amazonaws.services.cloudformation.model.*;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.identitymanagement.model.User;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.entity.*;
import tw.com.exceptions.*;
import tw.com.parameters.*;
import tw.com.providers.IdentityProvider;
import tw.com.providers.NotificationSender;
import tw.com.providers.ProvidesCurrentIp;
import tw.com.repository.CloudFormRepository;
import tw.com.repository.CloudRepository;
import tw.com.repository.ELBRepository;
import tw.com.repository.VpcRepository;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.*;

import static java.lang.String.format;


public class AwsFacade implements ProvidesZones {

    @Deprecated
	private static final String UPDATE_EXTENSTION_LEGACY = ".delta"; // use update instead
    private static final String UPDATE_EXTENSTION = ".update";

    private static final Logger logger = LoggerFactory.getLogger(AwsFacade.class);
	
	public static final String ENVIRONMENT_TAG = "CFN_ASSIST_ENV";
	public static final String PROJECT_TAG = "CFN_ASSIST_PROJECT"; 
	public static final String INDEX_TAG = "CFN_ASSIST_DELTA";
    public static final String UPDATE_INDEX_TAG = "CFN_ASSIST_UPDATE";
    public static final String BUILD_TAG = "CFN_ASSIST_BUILD_NUMBER";
	public static final String TYPE_TAG = "CFN_ASSIST_TYPE";
	public static final String ENV_S3_BUCKET = "CFN_ASSIST_BUCKET";
	
	public static final String PARAMETER_STACKNAME = "stackname";

    private VpcRepository vpcRepository;
	private CloudFormRepository cfnRepository;
	private ELBRepository elbRepository;
	private CloudRepository cloudRepository;
	private NotificationSender notificationSender;
	private MonitorStackEvents monitor;
	private IdentityProvider identityProvider;

	private String regionName;

	public AwsFacade(MonitorStackEvents monitor, CloudFormRepository cfnRepository, VpcRepository vpcRepository, 
			ELBRepository elbRepository, CloudRepository cloudRepository, NotificationSender notificationSender,
			IdentityProvider identityProvider, String regionName) {
		this.monitor = monitor;
		this.cfnRepository = cfnRepository;
		this.vpcRepository = vpcRepository;
		this.elbRepository = elbRepository;
		this.cloudRepository = cloudRepository;
		this.notificationSender = notificationSender;
		this.identityProvider = identityProvider;
		this.regionName = regionName;
	}

	public List<TemplateParameter> validateTemplate(String templateBody) {
		List<TemplateParameter> parameters = cfnRepository.validateStackTemplate(templateBody);
		logger.info(format("Found %s parameters", parameters.size()));
		return parameters;
	}

	public List<TemplateParameter> validateTemplate(File file) throws IOException {
		logger.info("Validating template and discovering parameters for file " + file.getAbsolutePath());
		String contents = loadFileContents(file);
		return validateTemplate(contents);
	}
	
	public StackNameAndId applyTemplate(String filename, ProjectAndEnv projAndEnv) throws CfnAssistException, IOException, InterruptedException {
		File file = new File(filename);
		return applyTemplate(file, projAndEnv);
	}
	
	public StackNameAndId applyTemplate(File file, ProjectAndEnv projAndEnv)
			throws IOException, InterruptedException, CfnAssistException {
		return applyTemplate(file, projAndEnv, new HashSet<>());
	}

    public StackNameAndId applyTemplate(File file, ProjectAndEnv projAndEnv, Collection<Parameter> userParameters) throws
            IOException, InterruptedException, CfnAssistException {
        Tagging tagging = new Tagging();
        return applyTemplate(file, projAndEnv, userParameters, tagging);
    }

    private StackNameAndId applyTemplate(File file, ProjectAndEnv projAndEnv, Collection<Parameter> userParameters,
                                        Tagging tagging) throws CfnAssistException, IOException, InterruptedException {
        logger.info(format("Applying template %s for %s", file.getAbsoluteFile(), projAndEnv));
        Vpc vpcForEnv = findVpcForEnv(projAndEnv);
        List<TemplateParameter> declaredParameters = validateTemplate(file);

        List<PopulatesParameters> populators = new LinkedList<>();
        populators.add(new CfnBuiltInParams(vpcForEnv.getVpcId()));
        populators.add(new AutoDiscoverParams(file, vpcRepository, cfnRepository));
        populators.add(new EnvVarParams());
        ParameterFactory parameterFactory = new ParameterFactory(populators);

        if (projAndEnv.hasComment()) {
            tagging.setCommentTag(projAndEnv.getComment());
        }

        String contents = loadFileContents(file);

        if (isUpdate(file)) {
            logger.info("Request to update a stack, filename is " + file.getAbsolutePath());
            return updateStack(projAndEnv, userParameters, declaredParameters, contents, parameterFactory);
        } else {
            return createStack(file, projAndEnv, userParameters, declaredParameters, contents, parameterFactory,tagging);
        }
    }

	private boolean isUpdate(File file) {
        String name = file.getName();
        return (name.contains(UPDATE_EXTENSTION) || name.contains(UPDATE_EXTENSTION_LEGACY));
	}

	private boolean updateSuffix(String name) {
		return name.endsWith(UPDATE_EXTENSTION) || name.endsWith(UPDATE_EXTENSTION_LEGACY);
	}

	private StackNameAndId updateStack(ProjectAndEnv projAndEnv, Collection<Parameter> userParameters,
                                       List<TemplateParameter> declaredParameters, String contents,
                                       ParameterFactory parameterFactory) throws CfnAssistException, IOException, InterruptedException {

		Collection<Parameter> parameters = parameterFactory.createRequiredParameters(projAndEnv, userParameters,
                declaredParameters, this);
		String stackName = findStackToUpdate(declaredParameters, projAndEnv);
		
		StackNameAndId id = cfnRepository.updateStack(contents, parameters, monitor, stackName);
		
		try {
			monitor.waitForUpdateFinished(id);
		} catch (WrongStackStatus stackFailedToUpdate) {
			logger.error("Failed to update stack",stackFailedToUpdate);
			cfnRepository.updateFail(id);
			throw stackFailedToUpdate;
		}
		Stack createdStack = cfnRepository.updateSuccess(id);
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
					logger.error(format("Found parameter %s but no default given", PARAMETER_STACKNAME));
					throw new InvalidStackParameterException(PARAMETER_STACKNAME); 
				}
			}
		}
		logger.error(format("Unable to find parameter call '%s' which is required to peform a stack update", PARAMETER_STACKNAME));
		throw new InvalidStackParameterException(PARAMETER_STACKNAME);
	}

	private StackNameAndId createStack(File file, ProjectAndEnv projAndEnv,
			Collection<Parameter> userParameters,
			List<TemplateParameter> declaredParameters,
            String contents,
            ParameterFactory parameterFactory, Tagging tagging)
			throws CfnAssistException, InterruptedException, IOException {
		String stackName = createStackName(file, projAndEnv);
		logger.info("Stackname is " + stackName);
		
		handlePossibleRollback(stackName);

        Collection<Parameter> parameters = parameterFactory.createRequiredParameters(projAndEnv, userParameters, declaredParameters, this);

		StackNameAndId id = cfnRepository.createStack(projAndEnv, contents, stackName, parameters, monitor, tagging);
		
		try {
			monitor.waitForCreateFinished(id);
		} catch (WrongStackStatus stackFailedToCreate) {
			logger.error("Failed to create stack",stackFailedToCreate);
			cfnRepository.createFail(id);
			throw stackFailedToCreate;
		}
		Stack createdStack = cfnRepository.createSuccess(id);
		createOutputTags(createdStack, projAndEnv);
		sendNotification(stackName, StackStatus.CREATE_COMPLETE.toString());
		return id;
	}

	private void sendNotification(String stackName, String status)
			throws CfnAssistException {
		User userId = identityProvider.getUserId();
		notificationSender.sendNotification(new CFNAssistNotification(stackName, status, userId));
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
		logger.info(format("Found VPC %s corresponding to %s", vpcForEnv.getVpcId(), projAndEnv));
		return vpcForEnv;
	}


	public String createStackName(File file, ProjectAndEnv projAndEnv) {
		// note: aws only allows [a-zA-Z][-a-zA-Z0-9]* in stacknames
		String filename = file.getName();
		String name = FilenameUtils.removeExtension(filename);
		if (updateSuffix(name)) {
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
	
	public void deleteStackFrom(File templateFile, ProjectAndEnv projectAndEnv) throws CfnAssistException {
		String fullName = createStackName(templateFile, projectAndEnv);
		deleteStack(fullName);
	}

	public void deleteStackByName(String partialName, ProjectAndEnv projectAndEnv) throws CfnAssistException {
		String fullname = createName(projectAndEnv, partialName);
		deleteStack(fullname);
	}
	
	private void deleteStack(String stackName) throws CfnAssistException {
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
			sendNotification(stackName, StackStatus.DELETE_COMPLETE.toString());
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
			ProjectAndEnv projAndEnv, Collection<Parameter> cfnParams)
            throws IOException, InterruptedException, CfnAssistException {
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
				logger.info(format("Apply template file: %s, index is %s", file.getAbsolutePath(), deltaIndex));
                Tagging tagging = new Tagging();
                tagging.setIndexTag(deltaIndex);
				StackNameAndId stackId = applyTemplate(file, projAndEnv, cfnParams, tagging);
				logger.info("Create/Updated stack " + stackId);
				updatedStacks.add(stackId); 
				setDeltaIndex(projAndEnv, deltaIndex);
			} else {
				logger.info(format("Skipping file %s as already applied, index was %s", file.getAbsolutePath(), deltaIndex));
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

    public List<String> stepbackLastChange(ProjectAndEnv projAndEnv) throws CfnAssistException {
        DeletionsPending pending = new DeletionsPending();

        int highestAppliedDelta = getDeltaIndex(projAndEnv);
        logger.info("Current delta is " + highestAppliedDelta);
        SetsDeltaIndex setsDeltaIndex = vpcRepository.getSetsDeltaIndexFor(projAndEnv);

        try {
            StackEntry stackEntry = cfnRepository.getStacknameByIndex(projAndEnv.getEnvTag(), highestAppliedDelta);
            StackNameAndId id = cfnRepository.getStackNameAndId(stackEntry.getStackName()); // important to get id's before deletion request, may throw otherwise
            cfnRepository.deleteStack(stackEntry.getStackName());
            pending.add(highestAppliedDelta,id);
        }
        catch (WrongNumberOfStacksException notFound) {
            logger.error("Could not find stack with correct index to delete, index was "+highestAppliedDelta, notFound);
        }
        return monitor.waitForDeleteFinished(pending, setsDeltaIndex);
    }

    @Deprecated
	public List<String> stepbackLastChangeFromFolder(String folderPath, ProjectAndEnv projAndEnv) throws CfnAssistException {
		DeletionsPending pending = new DeletionsPending();
		File folder = validFolder(folderPath);
		List<File> files = loadFiles(folder);
		Collections.reverse(files); // delete in reverse direction
		
		int highestAppliedDelta = getDeltaIndex(projAndEnv);
		logger.info("Current delta is " + highestAppliedDelta);	
		
		File toDelete = findFileToStepBack(files, highestAppliedDelta);
		if (toDelete==null) {
			logger.warn("No suitable stack/file found to step back from");
			return new LinkedList<>();
		}
		
		logger.info("Need to step back for file " + toDelete.getAbsolutePath());
		int deltaIndex = extractIndexFrom(toDelete);
		SetsDeltaIndex setsDeltaIndex = vpcRepository.getSetsDeltaIndexFor(projAndEnv);	
		String stackName = createStackName(toDelete, projAndEnv);

		if (isUpdate(toDelete)) {
			logger.warn("Rolling back a stack change/delta does nothing except update delta index on VPC");
			setsDeltaIndex.setDeltaIndex(deltaIndex-1);	
			return new LinkedList<>();
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

    public List<String> rollbackTemplatesByIndexTag(ProjectAndEnv projAndEnv) throws CfnAssistException {
        DeletionsPending pending = new DeletionsPending();
        int highestAppliedDelta = getDeltaIndex(projAndEnv);

        while (highestAppliedDelta>0) {
            try {
                logger.info("Current delta is " + highestAppliedDelta);
                StackEntry stackToDelete = cfnRepository.getStacknameByIndex(projAndEnv.getEnvTag(), highestAppliedDelta);
                // TODO add ID to StackEntry
                StackNameAndId id = cfnRepository.getStackNameAndId(stackToDelete.getStackName());
                logger.info(format("Found stack %s matching index", id));
                cfnRepository.deleteStack(stackToDelete.getStackName());
                pending.add(highestAppliedDelta,id);
                highestAppliedDelta--;
            }
            catch (WrongNumberOfStacksException notFound) {
                logger.error("Unable to find a stack matching index " + highestAppliedDelta, notFound);
                break;
            }
        }
        SetsDeltaIndex setsDeltaIndex = vpcRepository.getSetsDeltaIndexFor(projAndEnv);
        return monitor.waitForDeleteFinished(pending, setsDeltaIndex);
    }

    @Deprecated
    public List<String> rollbackTemplatesInFolder(String folderPath, ProjectAndEnv projAndEnv) throws CfnAssistException {
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
			if (!isUpdate(file)) {
				int deltaIndex = extractIndexFrom(file);
				String stackName = createStackName(file, projAndEnv);
				if (deltaIndex>highestAppliedDelta) {
					logger.warn(format("Not deleting %s as index %s is greater than current delta %s", stackName, deltaIndex, highestAppliedDelta));
				} else {			
					logger.info(format("About to request deletion of stackname %s", stackName));
					StackNameAndId id = cfnRepository.getStackNameAndId(stackName); // important to get id's before deletion request, may throw otherwise
					cfnRepository.deleteStack(stackName);
					pending.add(deltaIndex,id);
				}
			} else {
				logger.info(format("Skipping file %s as it is a stack update file", file.getAbsolutePath()));
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
		int result;
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
			logger.error(format("Managed to find vpc already present with tags %s and id %s", projectAndEnvToSet, result.getVpcId()));
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

	public void tidyNonLBAssocStacks(File file, ProjectAndEnv projectAndEnv, String typeTag) throws CfnAssistException {
		String filename = file.getName();
		String name = FilenameUtils.removeExtension(filename);
		if (updateSuffix(name)) {
			throw new InvalidStackParameterException("Cannot invoke for .delta files");
		}
		logger.info(format("Checking for non-instance-associated stacks for %s and name %s. Will use %s:%s if >1 ELB",
                projectAndEnv, name, AwsFacade.TYPE_TAG, typeTag));

		List<StackEntry> candidateStacks = cfnRepository.getStacksMatching(projectAndEnv.getEnvTag(), name);
		
		if (candidateStacks.isEmpty()) {
			logger.warn("No matching stacks found for possible deletion");
			return;
		} 
		
		List<String> regInstanceIds = currentlyRegisteredInstanceIDs(projectAndEnv, typeTag);
	
		List<StackEntry> toDelete = new LinkedList<>();
		for(StackEntry entry : candidateStacks) {
			List<String> ids = cfnRepository.getInstancesFor(entry.getStackName());
			if (ids.isEmpty()) {
				logger.warn(format("Stack %s has no instances at all, will not be deleted", entry.getStackName()));
			} else {
				if (containsAny(regInstanceIds,ids)) {
					logger.info(format("Stack %s contains instances registered to LB, will not be deleted", entry.getStackName()));
				} else {
					logger.warn(format("Stack %s has no registered instances, will be deleted", entry.getStackName()));
					toDelete.add(entry);
				}
			}
		}
		
		if (toDelete.isEmpty()) {
			logger.info("No stacks to delete");
		} else {
			for(StackEntry delete : toDelete) {
				logger.warn("Deleting stack " + delete.getStackName());
				deleteStack(delete.getStackName());
			}
		}
		
	}

	private List<String> currentlyRegisteredInstanceIDs(
			ProjectAndEnv projectAndEnv, String typeTag)
			throws TooManyELBException {
		List<Instance> registeredInstances = elbRepository.findInstancesAssociatedWithLB(projectAndEnv, typeTag);
		List<String> regInstanceIds = new LinkedList<>();
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
		logger.info(format("Update instances for ELB to match %s and type tag %s", projectAndEnv, typeTag));
		return elbRepository.updateInstancesMatchingBuild(projectAndEnv, typeTag);	
	}

	public void whitelistCurrentIpForPortToElb(ProjectAndEnv projectAndEnv, String type, ProvidesCurrentIp hasCurrentIp, Integer port) throws CfnAssistException {		
		InetAddress address = hasCurrentIp.getCurrentIp();
		logger.info(format("Request to add %s port:%s for elb on %s of type %s", address.getHostAddress(), port, projectAndEnv, type));
		String groupId = getSecGroupIdForELB(projectAndEnv, type);
		logger.info("Found sec group: " + groupId);
		cloudRepository.updateAddIpAndPortToSecGroup(groupId, address, port);
	}

	public void blacklistCurrentIpForPortToElb(ProjectAndEnv projectAndEnv, String type, ProvidesCurrentIp hasCurrentIp, Integer port) throws CfnAssistException {
		InetAddress address = hasCurrentIp.getCurrentIp();
		logger.info(format("Request to remove %s port:%s for elb on %s of type %s", address.getHostAddress(), port, projectAndEnv, type));
		String groupId = getSecGroupIdForELB(projectAndEnv, type);
		logger.info("Found sec group: " + groupId);
		cloudRepository.updateRemoveIpAndPortFromSecGroup(groupId, address, port);
	}
	
	private String getSecGroupIdForELB(ProjectAndEnv projectAndEnv, String type)
			throws CfnAssistException {
		LoadBalancerDescription elb = elbRepository.findELBFor(projectAndEnv, type);
		if (elb==null) {
			throw new CfnAssistException("Did not find ELB for current vpc");
		}
		
		List<String> groups = elb.getSecurityGroups();
		
		if (groups.size()>1) {
			throw new CfnAssistException("Found multiple security groups associated with elb " + elb.getDNSName());
		}

		return groups.get(0);
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

    @Override
    public Map<String, AvailabilityZone> getZones() {
       return cloudRepository.getZones(regionName);
    }

}
