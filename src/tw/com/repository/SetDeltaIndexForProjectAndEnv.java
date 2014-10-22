package tw.com.repository;

import tw.com.SetsDeltaIndex;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;

public class SetDeltaIndexForProjectAndEnv implements SetsDeltaIndex {

		private ProjectAndEnv projAndEnv;
		private VpcRepository vpcRepository;

		public SetDeltaIndexForProjectAndEnv(ProjectAndEnv projAndEnv, VpcRepository vpcRepository) {
			this.projAndEnv = projAndEnv;
			this.vpcRepository = vpcRepository;
		}

		@Override
		public void setDeltaIndex(Integer newDelta) throws CannotFindVpcException {
			vpcRepository.setVpcIndexTag(projAndEnv, newDelta.toString());
		}

}
