package tw.com;

import com.amazonaws.services.cloudformation.model.*;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.KeyPair;
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
import tw.com.providers.SavesFile;
import tw.com.repository.*;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.time.Duration;
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
    public static final String KEYNAME_TAG = "keypairname";
	
	public static final String PARAMETER_STACKNAME = "stackname";
	public static final String NAT_EIP = "natEip";

	private final VpcRepository vpcRepository;
	private final CloudFormRepository cfnRepository;
	private final ELBRepository elbRepository;
	private final CloudRepository cloudRepository;
	private final NotificationSender notificationSender;
	private final MonitorStackEvents monitor;
	private final IdentityProvider identityProvider;
	private final LogRepository logRepository;

    public AwsFacade(MonitorStackEvents monitor, CloudFormRepository cfnRepository, VpcRepository vpcRepository,
                     ELBRepository elbRepository, CloudRepository cloudRepository, NotificationSender notificationSender,
                     IdentityProvider identityProvider, LogRepository logRepository) {
		this.monitor = monitor;
		this.cfnRepository = cfnRepository;
		this.vpcRepository = vpcRepository;
		this.elbRepository = elbRepository;
		this.cloudRepository = cloudRepository;
		this.notificationSender = notificationSender;
		this.identityProvider = identityProvider;
        this.logRepository = logRepository;
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
		outputs.stream().filter(output -> shouldCreateTag(output.getDescription())).forEach(output -> {
			logger.info("Should create output tag for " + output.toString());
			vpcRepository.setVpcTag(projAndEnv, output.getOutputKey(), output.getOutputValue());
		});
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
			logger.error("Exception", e);
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
            // important to get id's before deletion request, may throw otherwise
            StackNameAndId id = cfnRepository.getStackNameAndId(stackEntry.getStackName());
            cfnRepository.deleteStack(stackEntry.getStackName());
            pending.add(highestAppliedDelta,id);
        }
        catch (WrongNumberOfStacksException notFound) {
            logger.error("Could not find stack with correct index to delete, index was "+highestAppliedDelta, notFound);
        }
        return monitor.waitForDeleteFinished(pending, setsDeltaIndex);
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

	public void setTagForVpc(ProjectAndEnv projectAndEnv, String tagName, String tagValue) {
		vpcRepository.setVpcTag(projectAndEnv,tagName,tagValue);
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
	
	private boolean containsAny(List<String> first, List<String> second) {
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

	public void addCurrentIPWithPortToELB(ProjectAndEnv projectAndEnv, String type, ProvidesCurrentIp hasCurrentIp, Integer port) throws CfnAssistException {
		InetAddress address = hasCurrentIp.getCurrentIp();
		logger.info(format("Request to add %s port:%s for elb on %s of type %s", address.getHostAddress(), port, projectAndEnv, type));
		String groupId = getSecGroupIdForELB(projectAndEnv, type);
		logger.info("Found sec group: " + groupId);
        List<InetAddress> addresses = new LinkedList<>();
        addresses.add(address);
		cloudRepository.updateAddIpsAndPortToSecGroup(groupId, addresses, port);
	}

    public void addHostAndPortToELB(ProjectAndEnv projectAndEnv, String type, String host, Integer port) throws UnknownHostException, CfnAssistException {
        List<InetAddress> addresses = Arrays.asList(Inet4Address.getAllByName(host));
        logger.info(format("Request to add %s [%s] port:%s for elb on %s of type %s", host, addresses, port, projectAndEnv, type));
        String groupId = getSecGroupIdForELB(projectAndEnv, type);
        logger.info("Found sec group: " + groupId);
        cloudRepository.updateAddIpsAndPortToSecGroup(groupId, addresses, port);
    }

	public void removeHostAndPortFromELB(ProjectAndEnv projectAndEnv, String type, String hostname, Integer port) throws UnknownHostException, CfnAssistException {
        List<InetAddress> addresses = Arrays.asList(Inet4Address.getAllByName(hostname));
        logger.info(format("Request to remove %s [%s] port:%s for elb on %s of type %s", hostname, addresses, port, projectAndEnv, type));
        String groupId = getSecGroupIdForELB(projectAndEnv, type);
        logger.info("Found sec group: " + groupId);
        cloudRepository.updateRemoveIpsAndPortFromSecGroup(groupId, addresses, port);
	}

	public void removeCurrentIPAndPortFromELB(ProjectAndEnv projectAndEnv, String type, ProvidesCurrentIp hasCurrentIp, Integer port) throws CfnAssistException {
		InetAddress address = hasCurrentIp.getCurrentIp();
		logger.info(format("Request to remove %s port:%s for elb on %s of type %s", address.getHostAddress(), port, projectAndEnv, type));
		String groupId = getSecGroupIdForELB(projectAndEnv, type);
		logger.info("Found sec group: " + groupId);
        List<InetAddress> addresses = new LinkedList<>();
        addresses.add(address);
        cloudRepository.updateRemoveIpsAndPortFromSecGroup(groupId, addresses, port);
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
       return cloudRepository.getZones();
    }

	public KeyPair createKeyPair(ProjectAndEnv projAndEnv, SavesFile destination, String filename) throws CfnAssistException {
		if (destination.exists(filename)) {
            throw new CfnAssistException(format("File '%s' already exists", filename));
        }

		String env = projAndEnv.getEnv();
        String project = projAndEnv.getProject();
		String keypairName = format("%s_%s", project,env);
        logger.info("Create key pair with name " + keypairName);
		KeyPair result = cloudRepository.createKeyPair(keypairName, destination, filename);
		vpcRepository.setVpcTag(projAndEnv,KEYNAME_TAG, result.getKeyName());
		return result;
	}

	public List<String> createSSHCommand(ProjectAndEnv projectAndEnv, String user) throws CfnAssistException {
        String home = System.getenv("HOME");
		String keyNameFromTag = vpcRepository.getVpcTag(AwsFacade.KEYNAME_TAG, projectAndEnv);
		String keyName = keyNameFromTag.replaceFirst("_keypair","");
		String eipAllocId = vpcRepository.getVpcTag(AwsFacade.NAT_EIP, projectAndEnv);
		if (eipAllocId==null) {
			throw new CfnAssistException(format("Unable to find tag %s for %s", AwsFacade.NAT_EIP, projectAndEnv));
		}
        String address = cloudRepository.getIpFor(eipAllocId);
        List<String> command = new LinkedList<>();
        command.add("ssh");
        command.add("-i");
        command.add(format("%s/.ssh/%s.pem",home, keyName));
        command.add(format("%s@%s", user,address));
        return command;
	}

	public void removeCloudWatchLogsOlderThan(ProjectAndEnv projectAndEnv, int days) {
        List<String> groups = logRepository.logGroupsFor(projectAndEnv);
        groups.forEach(group -> {
            logger.info(format("Removing streams over %s days from log group %s", days, group));
            logRepository.removeOldStreamsFor(group, Duration.ofDays(days));
        });

	}

	public void tagCloudWatchLog(ProjectAndEnv projectAndEnv, String groupName) {
		logRepository.tagCloudWatchLog(projectAndEnv, groupName);
	}
}
