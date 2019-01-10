package tw.com.providers;

import java.util.LinkedList;
import java.util.List;

import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DBInstance;
import software.amazon.awssdk.services.rds.model.DBSubnetGroup;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesResponse;

public class RDSClient {
	RdsClient rdsClient;

	public RDSClient(RdsClient rdsClient) {
		this.rdsClient = rdsClient;
	}

	public List<DBInstance> getDBInstancesForVpc(String vpcId) {
		DescribeDbInstancesResponse result = rdsClient.describeDBInstances();
		List<DBInstance> dbInstances = result.dbInstances();
		
		List<DBInstance> filtered = new LinkedList<>();
		for(DBInstance dbInstance : dbInstances) {
			DBSubnetGroup dbSubnetGroup = dbInstance.dbSubnetGroup();
			if (dbSubnetGroup!=null) {
				String groupVpcId = dbSubnetGroup.vpcId();
				if (groupVpcId.equals(vpcId)) {
					filtered.add(dbInstance);
				}
			}
		}
		return filtered;
	}

}
