cfnassit
========

cfnassit is to a tool help with [cloud formation](http://aws.amazon.com/cloudformation/) deployments into [AWS](http://aws.amazon.com/) VPCs

Current Release
---------------
[Download Currnet Release version 1.0.36](https://cfnassist-release.s3-eu-west-1.amazonaws.com/36/cfnassit-1.0.36.zip)

Old Releases
------------
[Version 1.0.33](https://cfnassist-release.s3-eu-west-1.amazonaws.com/33/cfnassit-1.0.33.zip)
[Version 1.0.29](https://s3-eu-west-1.amazonaws.com/cfnassist-release/29/cfnassit-1.0.29.zip)

Build Status
------------
[![Build Status](https://snap-ci.com/cartwrightian/cfnassist/branch/master/build_image)](https://snap-ci.com/cartwrightian/cfnassist/branch/master)

Key Features
------------
* Works with the existing syntax of cloud formation json, it just uses some simple conventions for parameters
* Implements a simple Project/Environment abstraction on top of AWS VPCs  (i.e. cfnassit/qa or cfnassit/dev)
* Manages application of create and delta cloudformation scripts against particular Projects and Environments
* Tracks which scripts need to be applied to projects and environments using a simple delta tracking mechanism borrowed from [dbdeploy](http://dbdeploy.com/)
* Autopopulates physical id's based on logical identifiers plus the project & environment, this means you can break large scripts apart and think about project/env/logical ids instead of VPC id/physical id
* Assists with the [Phoenix Server](http://martinfowler.com/bliki/PhoenixServer.html) pattern by automating the switch over of instances for an ELB based on build numbers
* Allows upload to S3; along with the option of passing S3 urls of new aritifacts directly into cloudformation templates
* Use VPC Tags as input or output to templates using a simple convention in the parameter description

Usage
-----
The tool provides a command line interface and ant tasks. Examples of the CLI use below, see `exampleAntTasks.xml` for ant task usage. 

Warning
-------
Creating AWS resources can mean you might be liable for charges. Please check the AWS documentation.

Prerequisites
-------------
You need to be familiar with AWS, in particular VPCs and Cloud Formation.

Notes
-----
* The tool uses [DefaultAWSCredentialsProviderChain](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html) to connect to AWS, please read to understand how to authenticate with cfnassist.
* For certain key parameters the tool checks for environmental variables, for example CFN\_ASSIST\_PROJECT and CFN\_ASSIST\_ENV, this is useful if you use tools like autoenv or are working on a particular project a lot.

Documentation TODOs
-------------------
* Mention rate limits and the caching approach?
* Add links to the various *example json files in the test/cfnScripts folder*

CLI HowTo
---------

The example CLIs below assume you have the environmental variable **CFN\_ASSIST\_PROJECT** set to your project, that `cfn\_assist.sh` 
is on the PATH and the **EC2\_REGION** environmental variable is set to the appropriate AWS region.

1.Create a VPC and initialise with a Project and Environment
-------------------------------------------------------------

First create a VPC, for example:

`aws ec2 create-vpc --cidr-block 10.0.0.0/16`

result:

>VPC	10.0.0.0/16	dopt-5e4f5b3c	default	pending	vpc-926e8bf7  

Wait for the create to finish, use this to check the status

`aws ec2 describe-vpcs`

result:

>VPCS	10.0.0.0/16	dopt-5e4f5b3c	default	False	available	vpc-926e8bf7

Now use cfnassit to init the vpc with the tags it uses to id vpc's and track changes.

`cfnassist.sh -env Dev -init vpc-926e8bf7`

Replace `vpc-926e8bf7` with the ID of your VPC.

Check this work as expected using 

`aws ec2 describe-vpcs`

You should see the VPC now has three tags associated with it:

>VPCS	10.0.0.0/16	dopt-5e4f5b3c	default	False	available	vpc-926e8bf7  
>TAGS	CFN\_ASSIST\_ENV	Dev  
>TAGS	CFN\_ASSIST\_DELTA	0   
>TAGS	CFN\_ASSIST\_PROJECT	tramchester  

Repeat above steps for other envs such as UAT and Prod

2.Set up your infrastructure 
----------------------------

This typically includes the things you don't need to change on every release. 
For example subnets, load balancers, NAT and internet gateways and so on. 
You may still want to clean out a VPC and rerun this on occasions to make sure you can fully recreate a fully working VPC from scratch. 
Live instances etc should probably be created during the CI build especially if you are doing blue/green deploys. More on this below.

After each script succeeds the VPC **CFN\_ASSIST\_DELTA** tag is updated, this way the tool only tries to create the required stacks for each VPC. The tool will also take care of deleting an existing stack if it is in the rollback complete state.

`cfnassist.sh -env Dev -dir ./infrastructure`

My infrastructure dir looks like this:
>-rw-r--r--  1 icartwri  staff  1748 13 Jan 15:30 001subnets.json    
>-rw-r--r--  1 icartwri  staff   510 13 Jan 15:26 002internetGateway.json  
>-rw-r--r--  1 icartwri  staff  6287 13 Jan 21:34 003webSubnetAclAndSG.json  
>-rw-r--r--  1 icartwri  staff  1521 13 Jan 21:41 004dbSubnetAclAndSG.json  
>-rw-r--r--  1 icartwri  staff  5672 13 Jan 21:35 005intSubnetAclAndSG.json  
>-rw-r--r--  1 icartwri  staff  6572  9 Apr 15:46 006monSubnetAclAndSG.json  
>-rw-r--r--  1 icartwri  staff  5818 13 Jan 15:26 007natSubnetAclAndSG.json  
>-rw-r--r--  1 icartwri  staff  2842 13 Jan 21:46 008lbSubnetACLandSG.json  
>-rw-r--r--  1 icartwri  staff  1406 13 Jan 21:30 009natServer.json  
>-rw-r--r--  1 icartwri  staff   875 13 Jan 21:30 010elasticLoadBalancer.json  
>-rw-r--r--  1 icartwri  staff  3895 13 Jan 21:31 011routing.json  

The tool executes these in numeric order, hence the filenames beginning with numbers, it does 'create' by default but can also do 'updates', see below.

*Use Logical IDs*

The tool will inject the correct *Physical ID* for a resource based on the current VPC, it finds the correct VPC using the *Project* and *Environment* . You can declare a parameter exactly as normal for cloudformation but follow the convention below and cfn assit will find the correct *physical ID*:

>"Parameters" : {  
>                        "env" : { "Type" : "String" },  
>                        "vpc" : { "Type" : "String" },  
>                        "natSubnet" : { "Type" : "String" , "Description" : "::natSubnet" }  
>                },  

In this example **::natSubnet** triggers to cfn assist find the right Physical ID for that logical ID in the current VPC, 
it does this by scanning the stacks associated with the current VPC for that logical ID. 
Also note the tool automatically injects the correct VPC and env (i.e. dev, UAT, Prod, etc).

So the example above will apply all the cfn scripts found in the infrastructure dir in order, the cloud formation stacks created will include the project and env in their name as well as the filename. There are also tagged with the project and env (and build number if present, more on that below....)

*Results*

If all goes will the VPC tags get updated with the new delta index, so for example:

> TAGS	CFN\_ASSIST\_DELTA	11  
> TAGS	CFN\_ASSIST\_ENV	Dev  
> TAGS	CFN\_ASSIST\_PROJECT	tramchester  

You should also be able to see all the associated stacks using

`aws cloudformation describe-stacks`

*Create or Update?*

By default cfnassit invokes cloud formation 'create', but if the filename contains ends with `.delta.json` it will do a cloud formation 'update' 
instead. 
As described above cfnassit will keep track of which creates and updates been applied. The semantics of cloud formation updates are complex
and specific to the resource type involved, you need to **check the AWS documents** especially as some updates can actually result in a resource
being *recreated*.

3.Rolling it all back
---------------------

**Use with CARE!** 

This will rollback all delta's by deleting the stacks for a VPC in the correct order and updating the DELTA tag on the VPC as it goes! 
You may want to do this while getting things bedded in initially or when you want to check you can fully recreate an environment.

`cfnassist.sh -env Dev -rollback ./infrastructure`

4.Built-in parameters and Mappings
----------------------------------

cnfassit auto-populates some parameters when it invokes the cloud formation api, 
you need to make sure these parameters are declared in your json otherwise the api will throw an error. 
The pre-populated variables are env and vpc, so you need to declare them:

> "Parameters":{  
>		"env":{  
>			"Type":"String"  
>		},  
>		"vpc":{  
>			"Type":"String"  
>		},  

You can then use cloudformation mappings in the usual way to inject environment specific parameters i.e.

>"Mappings" : {  
>                        "environMap" : {  
>                                "qa" : { "keyName" : "techLab", "NatEip" : "eipalloc-0fb76661" },  
>                                "test" : { "keyName" : "techLab", "NatEip" : "TBA" },  
>                                "prod" : { "keyName" : "techLab", "NatEip" : "eipalloc-1de56375" }  
>                        }  
>                },  

The automatically populated *env* parameter is a more portable way to do this than mappings based on the VPC ID, 
especially if you need to recreate an environment/vpc from scratch or move to a different AWS region.

Use the mappings exactly as normal in cloudformation:

> "KeyName": { "Fn::FindInMap" : [ "environMap", { "Ref" : "env" }, "keyName" ]} ,   

5.Create instances (without build numbers)
------------------------------------------

For some longer lived services/instances you can include them in your 'infrastructure' delta directory, 
but sometimes it is more flexible to create them seperately. 

For example RDS instances might be a resource you create but don't want to create/delete on each build.

`cfnassist.sh -env Dev -file ./rdsInstance.json`

6.Create instances with build numbers
-------------------------------------

This is probably the more normal way to create instances, for example if you are using the Phoenix Server pattern.

`cfnassist.sh -env Dev -file ./rdsInstance.json -build 876`

This will create a stack, with the stack and the instances tagged additionally with **CFN_ASSIST_BUILD_NUMBER**, 
the stack name will also include the build number.

7.List out the stacks
---------------------
*Work in progress: display format, not having to give the project, plus list out managed VPCs*

You can list out the stacks cfnassit is managing for a particular project/env using

`cfnassist.sh -env Dev -ls` 

8.Pass parameters into the cloud formation scripts
--------------------------------------------------

You can declare the parameters exactly as you would for cloud formation. You can then pass values into them using the `parameters` argument.

`cfnassist.sh -env Dev -file ./rdsInstance.json -build 876 -parameters "testA=123;testB=123"`

*Note: You'll need to escape the ; character as appropriate for your shell/cli*

9.Switch over ELB instances using Build Number
----------------------------------------------

This lets you switch over the instances an ELB is pointing at based on *build number* and a special tag **CFN_ASSIST_TYPE**. 

You need to set this *type tag* on the instances yourself, for example including the following in your instance definition:

>"Tags": [  
>                  { "Key" : "Name", "Value": "aTestInstance" },  
>                  { "Key" : "CFN_ASSIST_TYPE", "Value": "web" }  
>                ]  

Now you can switch over the ELB using

`cfnassist.sh -env Dev -build 876 -elbUpdate web`

This will find all the instances created in cloud formation stacks for the project/env *and* that have the 
appropriate **CFN_ASSIST_TYPE** tag. 
It then finds the ELB for the VPC for the project and environment, addes the instances to the ELB and *removes* any instances not 
matching the build number.

You can 'rollback' the ELB to point at the previous instances by giving their build number, so you may not want to immediately delete 
previous instances until sure all is ok with the new ones.

*Restriction: The tool currently only supports one ELB per VPC*

10.Reset the delta tag on a VPC
------------------------------

**Use with CARE!** 

This will reset the delta index tag on the corresponding VPC back to 0.

`cfnassist.sh -env Dev -reset`

*If you have cloud formation stacks associated with the VPC you will not be able to manage them using cfnassit if you do this.*

11.Using SNS instead of polling for stack monitoring
----------------------------------------------------
By default cfnassist uses polling to check the status of stacks being created/updated/deleted. 
Cloud formation has the ability to publish SNS notifications as the status of a stack changes, 
and cfn assit can use these instead of polling to track status.

This avoids using polling meaning less network traffic and less chance of hitting 'rate limits' on the API

If you specify `-sns` on the commandline then cfnassit will 

1. Create a SNS topic using the name *CFN_ASSIST_EVENTS* (if it does not exist already)
2. Create a SQS queue using the name *CFN_ASSIST_EVENT_QUEUE* (again, only if required)
3. Create a policy meaning that SNS can publish the notifications to the queue. (and again, only if requied)
4. Finally it will subscribe the Queue to the SNS notifications.

If you try to update/delete a stack using the SNS option when the stack was not originally created with the 
flag set this will fail. 
As the cloud formation API now allows a stack to have the sns topic updated this restriction should soon be 
removed.

12.Upload files or folders to S3 and inject corresponding URLs into templates
-----------------------------------------------------------------------------
You can use cfnassist to upload files or folders into S3 and then pass in the S3 url's of those files into templates
automatically. The files will be prefixed with the build number i.e. /BUILDNUMBER/filename

The path to the file will be replaced with the URL, for example

`cfnassist.sh -env Dev -file templateFile.json -artifacts "urlA=dist/release.txt;urlB=dist/deployable.tgz" -bucket bucketName MyBucket -build 1123`

This will upload the files `release.txt` and `deployable.tgz` to S3 bucket MyBucket and then populate the 
parameters `urlA` and `urlB` with the corresponding S3 urls and pass these into `templateFile.json`. 

You can also upload the contents of a folder, in this case the path to the folder will be replaced with the URL of the bucket and key.

**NOTE** 
The current file path is not used, so the file dist/release.txt will end up in the bucket MyBucket with the key 1123/release.txt, 
this applies to the individual files within a folder also.

You can use the environmental variable *CFN_ASSIST_BUCKET* to specify the S3 bucket to use.

13.Upload or delete arifacts in S3
----------------------------------
Sometimes you want to create or delete things in S3 independently of deploying templates.

`cfnassist.sh -env Dev -s3create -artifacts "xzy=dist/release.txt;abc=dist/deployable.tgz" -bucket bucketName MyBucket -build 1123`

Or

`cfnassist.sh -env Dev -s3create -artifacts "folder=dist/subFolder" -bucket bucketName MyBucket -build 1123`

The files will be uploaded as per 12 above.

To delete files you need to need to pass in the name of the files themselves.

`cfnassist.sh -env Dev -s3delete -artifacts "mno=release.txt;rst=deployable.tgz -bucket bucketName MyBucket -build 1123`

**NOTE** 
The current file path is not used, so the file dist/release.txt will end up in the bucket MyBucket with the key `1123/release.txt`

14.Delete a stack
-----------------
You can delete a stack created with cfnassit using the following command:

`cfnassist.sh -env Dev -build 1223 -delete ./templateFile.json`

This will delete the stack that was created from the templateFile.json file with the build number 1223.

15.Using VPC Tags
-----------------
cfnassist will automatically populate a Tag on the VPC based on a simple convention for the Output section of a cloudformation script.
For example having the following in your output section will cause a new tag called SUBNET to be created on the correct VPC for the current project/env combination. In this case the tag will contian the ID of a created subnet.

>"Outputs" : {
>	    "SUBNET" : {
>	        "Value" : { "Ref":"testSubnet" }, "Description":"::CFN_TAG"
>	    }

You can also use a VPC Tag value as in input parameter, just use the same convention for the input parameter as above.

>	"testVPCTAG":{
>			"Type":"String",
>			"Description":"::CFN_TAG"
>		}	

This example will try and find a TAG called testVPCTAG on the current VPC and inject it's value into the template.

