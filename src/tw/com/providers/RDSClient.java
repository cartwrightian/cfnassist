package tw.com.providers;

import java.util.LinkedList;
import java.util.List;

import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBSubnetGroup;
import com.amazonaws.services.rds.model.DescribeDBInstancesResult;

public class RDSClient {
	AmazonRDSClient rdsClient;

	public RDSClient(AmazonRDSClient rdsClient) {
		this.rdsClient = rdsClient;
	}

	public List<DBInstance> getDBInstancesForVpc(String vpcId) {	
		DescribeDBInstancesResult result = rdsClient.describeDBInstances();
		List<DBInstance> dbInstances = result.getDBInstances();
		
		List<DBInstance> filtered = new LinkedList<DBInstance>(); 
		for(DBInstance dbInstance : dbInstances) {
			DBSubnetGroup dbSubnetGroup = dbInstance.getDBSubnetGroup();
			if (dbSubnetGroup!=null) {
				String groupVpcId = dbSubnetGroup.getVpcId();
				if (groupVpcId.equals(vpcId)) {
					filtered.add(dbInstance);
				}
			}
		}
		return filtered;
	}

}
