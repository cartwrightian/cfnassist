cfnassit
========

cfnassit to a tool help with cloud formation deployments into AWS VPCs

What is does
------------

* Implements a simple Project/Environment abstraction on top of AWS VPCs  (i.e. cfnassit/qa or cfnassit/dev)
* Manages application of create and delta cloudformation scripts against particular Projects and Environments
* Tracks which scripts need to be applies to projects and environments using a simple delta tracking mechanism borrowed from dbdeploy
* Autopopulates physical id's based on logical identifiers and a project & environment, this means you can break large scripts apart and think about project/env/logical ids instead of VPC id/physical id
* Assit with the Pheonix server pattern by automating the switch over of instances for an ELB based on build numbers

Usage
-----
The tool provides a command line interface and ant tasks 

Notes
-----
* The tool uses [DefaultAWSCredentialsProviderChain](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html) to connect to AWS, please read to understand how to authenticate with cfnassist.

* For certain key parameters the tool checks for environmental variables, for example CFN\_ASSIST\_PROJECT and CFN\_ASSIST\_ENV, this is useful if you use tools like autoenv or are working on a particular project a lot.

Documentation TODOs
-------------------
* Document the ls command
* Document the use of the SNS monitoring option

Commandline HowTo
-----------------

**Example commandlines assume you have the environmental variable CFN\_ASSIST\_PROJECT set and cfn_assist on the PATH**

1. Create a VPC and initialise with a Project and Environment

`aws ec2 create-vpc --cidr-block 10.0.0.0/16`

result:
`VPC	10.0.0.0/16	dopt-5e4f5b3c	default	pending	vpc-926e8bf7`

Wait for this to finish, use this to check the status

`aws ec2 describe-vpcs`

result:

VPCS	10.0.0.0/16	dopt-5e4f5b3c	default	False	available	vpc-926e8bf7

Now use cfnassit to init the vpc with the tags:

Assumptions
Env var **CFN\_ASSIST\_PROJECT** is set to project name, otherwise give -project <Name> on the cli as well
Env var **EC2\_REGION** set to aws region, other add -region <region>

`cfnassist.sh -env Dev -init vpc-926e8bf7`

Check with 

`aws ec2 describe-vpcs`

Example result:
VPCS	10.0.0.0/16	dopt-5e4f5b3c	default	False	available	vpc-926e8bf7
TAGS	CFN\_ASSIST\_ENV	Dev
TAGS	CFN\_ASSIST\_DELTA	0
TAGS	CFN\_ASSIST\_PROJECT	tramchester

Repeat above steps for other envs such as UAT and Prod

2. Set up your infrasture 

This typically includes the things you don't need to change on every release. For example subnets, load balancers, NAT and internet gateways and so on. You may still want to clean out a VPC and rerun this on occasions to make sure you can fully recreat a fully working VPC from scratch. Live instances etc should probably be created during the CI build especially if you are doing blue/green deploys. More on this below.

After each script succeeds the VPC **CFN\_ASSIST\_DELTA** tag is updated, this way the tool only tries to create the requierd stacks for each VPC. The tool will also take care of deleting a an existing stack if it is in the rollback complete state.

`cfnassist.sh -env Dev -dir ./infrastructure`

My infrastructure dir looks like this:
-rw-r--r--  1 icartwri  staff  1748 13 Jan 15:30 001subnets.json
-rw-r--r--  1 icartwri  staff   510 13 Jan 15:26 002internetGateway.json
-rw-r--r--  1 icartwri  staff  6287 13 Jan 21:34 003webSubnetAclAndSG.json
-rw-r--r--  1 icartwri  staff  1521 13 Jan 21:41 004dbSubnetAclAndSG.json
-rw-r--r--  1 icartwri  staff  5672 13 Jan 21:35 005intSubnetAclAndSG.json
-rw-r--r--  1 icartwri  staff  6572  9 Apr 15:46 006monSubnetAclAndSG.json
-rw-r--r--  1 icartwri  staff  5818 13 Jan 15:26 007natSubnetAclAndSG.json
-rw-r--r--  1 icartwri  staff  2842 13 Jan 21:46 008lbSubnetACLandSG.json
-rw-r--r--  1 icartwri  staff  1406 13 Jan 21:30 009natServer.json
-rw-r--r--  1 icartwri  staff   875 13 Jan 21:30 010elasticLoadBalancer.json
-rw-r--r--  1 icartwri  staff  3895 13 Jan 21:31 011routing.json

*Use Logical IDs*

The tool will inject the correct *Physical ID* for a resource based on the current VPC, it finds the correct VPC using the *Project* and *Environment* . You can declare a parameter exactly as normal for cloudformation but follow the convention below and cfn assit will find the correct *physical ID*:

`"Parameters" : {
                        "env" : { "Type" : "String" },
                        "vpc" : { "Type" : "String" },
                        "natSubnet" : { "Type" : "String" , "Description" : "::natSubnet" }
                },`

In this example **::natSubnet** leads to cfn assist find the right Physical ID for that logical ID in the current VPC, it does this by scanning the stacks associated with the current VPC for that logical ID. Also note the tool automaticalls injects the correct VPC and env (i.e. dev, UAT, Prod, etc).

This will apply all the cfn scripts found in the infrastructure dir in order, the cloud formation stacks created will include the project and env in their name as well as the filename. There are also tagged with the project and env (and build number if present, more on that below....)

*Results*

If all goes will the VPC tags get updated with (for example):

TAGS	CFN\_ASSIST\_DELTA	11
TAGS	CFN\_ASSIST\_ENV	Dev
TAGS	CFN\_ASSIST\_PROJECT	tramchester

You should also be able to see all the associated stacks using

`aws cloudformation describe-stacks`

3. Rolling it all back.

**Use with CARE!** 

This will rollback all delta's by deleting the stacks for a VPC in the correct order and updating the DELTA tag on the VPC as it goes! You may want to this initially while getting things bedded in initially while you still have just the one environment i.e. Dev or Test

`cfnassist.sh -env Dev -rollback ./infrastructure`

4. Mappings
You can use cloudformation mappings in the usual way to inject environment specific parameters i.e.

`"Mappings" : {
                        "environMap" : {
                                "qa" : { "keyName" : "techLab", "NatEip" : "eipalloc-0ab76661" },
                                "test" : { "keyName" : "techLab", "NatEip" : "TBA" },
                                "prod" : { "keyName" : "techLab", "NatEip" : "eipalloc-1ee56375" }
                        }
                },`

The automatically populated *env* parameter is a more portable way to do this that the VPC ID, especially if you need to recreate an environment/vpc from scratch.

Use like this:

`"KeyName": { "Fn::FindInMap" : [ "environMap", { "Ref" : "env" }, "keyName" ]} ,`

5. Create instances without build numbers

For some longer lived services/instances you can include them in your 'infrastructure' delta directory, but sometimes it is more flexible to create them seperately. For example RDS instances:

`cfnassist.sh -env Dev -file ./rdsInstance.json`

6. Create instances with build numbers

This is probably the more normal way to create instances, for example if you are using the Pheonix Server pattern.

`cfnassist.sh -env Dev -file ./rdsInstance.json -build 876`

This will create a stack and the instances tagged additionally with **CFN_ASSIST_BUILD_NUMBER**, the stack name will also include the build number.

7. Pass additional parameters into the cloud formation scripts

You declare the parameters exactly as you would for cloud formation. You can then pass values into them using the `parameters` argument.

`cfnassist.sh -env Dev -file ./rdsInstance.json -build 876 -parameters "testA=123;testB=123"`

*Note: You'll need to escape the ; character as appropriate for your shell/cli*

8. Switch over ELB instances using Build Numer

This lets you switch over the instances an ELB is pointing at based on *build number* and a special tag **CFN_ASSIST_TYPE**. 

You need to set this *type tag* on the instances yourself, for example includint the following in your instance definition:

`"Tags": [
                  { "Key" : "Name", "Value": "aTestInstance" },
                  { "Key" : "CFN_ASSIST_TYPE", "Value": "web" }
                ]` 

Now you can switch over the ELB using

`cfnassist.sh -env Dev -build 876 -elbUpdate web`

This will finds instances created in cloud formation stacks *and* that have the appropriate **CFN_ASSIST_TYPE** tag. It then finds the ELB for the VPC for the project and environment, addes the instances to the ELB and *removes* any instances not making the build.

9. Reset the delta tag on a VPC

**Use with CARE!** 

This will reset the delta index tag on the corresponding VPC back to 0.

`cfnassist.sh -env Dev -reset`

*If you have cloud formation stacks associated with the VPC you will not be able to manage them using cfnassit if you do this*
